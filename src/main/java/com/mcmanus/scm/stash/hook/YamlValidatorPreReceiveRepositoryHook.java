package com.mcmanus.scm.stash.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.MinimalCommit;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeType;
import com.atlassian.bitbucket.content.ChangesRequest;
import com.atlassian.bitbucket.content.ContentService;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.idx.CommitIndex;
import com.atlassian.bitbucket.io.TypeAwareOutputSupplier;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.MoreSuppliers;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class YamlValidatorPreReceiveRepositoryHook implements PreReceiveRepositoryHook
{
    private static final Logger LOG = LoggerFactory.getLogger(PreReceiveRepositoryHook.class);

    private static final int PAGE_REQUEST_LIMIT = 9999;
    private static final String EXTENSION_CONFIG_STRING = "extension";

    private final CommitService commitService;
    private final ContentService contentService;
    private final CommitIndex commitIndex;

    public YamlValidatorPreReceiveRepositoryHook(CommitService commitService,
                                                 ContentService contentService,
                                                 CommitIndex commitIndex
                                                 ){
        this.commitService = commitService;
        this.contentService = contentService;
        this.commitIndex = commitIndex;
    }

    /**
     * Hook to check yaml files before allowing the push to complete.
     *
     * @param context The context of the plugin, mostly used for getting the repository enabled
     * @param refChanges A set of changes
     * @param hookResponse A response to the client attempting to push
     * @return Whether to allow the push to continue or not
     */
    @Override
    public boolean onReceive(RepositoryHookContext context, Collection<RefChange> refChanges, HookResponse hookResponse)
    {
        boolean allFilesValid = true;
        ConcurrentMap<String, Commit> pathChanges = new ConcurrentHashMap<String, Commit>();

        String yamlFileExtension = context.getSettings().getString(EXTENSION_CONFIG_STRING);

        for (RefChange refChange : refChanges) {
            LOG.debug("Processing refchange of type: " + refChange.getType());

            Collection<Commit> commitsToCheck = Collections.synchronizedList(new ArrayList<>());

            findCommitsToCheck(refChange.getToHash(), context.getRepository(), commitsToCheck);

            for (Commit commit : commitsToCheck) {
                addFileChangesOnCommit(pathChanges, context.getRepository(), commit, yamlFileExtension);
            }
        }

        if (!pathChanges.isEmpty()) {
            if (!areFilesValid(pathChanges, context.getRepository(), hookResponse)){
                allFilesValid = false;
            }
        }

        return allFilesValid;
    }

    /**
     * Recursive method to get the list of commits which are new on the branch
     *
     * @param hash The commit hash
     * @param repository The repository that the commits have been completed on
     * @param commitsToProcess A list of commits which are new
     */
    private void findCommitsToCheck(String hash, Repository repository, Collection<Commit> commitsToProcess) {
        if (!commitIndex.isIndexed(hash, repository)) {
            final CommitRequest request = new CommitRequest.Builder(repository, hash).build();
            final Commit commit = commitService.getCommit(request);
            LOG.debug("Found commit to check " + hash);
            commitsToProcess.add(commit);
            for (MinimalCommit parent: commit.getParents()) {
                findCommitsToCheck(parent.getId(), repository, commitsToProcess);
            }
        }
    }

    /**
     * This function checks that all the files are valid that are being pushed
     *
     * @param pathChanges Map of the string paths with their associated commits
     * @param repository The repository that the push is for
     * @param hookResponse The response to the client
     *
     * @return A boolean denoting if the yaml files are valid
     */
    private boolean areFilesValid(ConcurrentMap<String, Commit> pathChanges, Repository repository, HookResponse hookResponse) {
        LOG.info("Found " + pathChanges.size() + " yaml files to validate");
        boolean allFilesAreValid = true;
        try {
            for (String filePath : pathChanges.keySet()){
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                TypeAwareOutputSupplier os = MoreSuppliers.newTypeAwareOutputSupplierOf(outputStream);

                contentService.streamFile(
                        repository,
                        pathChanges.get(filePath).getId(),
                        filePath,
                        os);

                try {
                    Yaml yaml = new Yaml();
                    try {
                        LOG.info("Attempting to validate yaml stream");
                        yaml.load(outputStream.toString());
                    } catch (Exception e) {
                        LOG.info("Rejecting push because following yaml file is invalid: " + filePath);
                        hookResponse.err().println("ERROR: Invalid yaml file: " + filePath);
                        hookResponse.err().println(e.getMessage());
                        allFilesAreValid = false;
                    }
                } finally {
                    LOG.debug("Attempting to close the output stream");
                    outputStream.close();
                }

                if (!allFilesAreValid) {
                    break;
                }

            }
            LOG.debug("Removing the directory created in order to validate yaml");
        } catch (IOException e) {
            LOG.error("Problem creating the a temp directory to validate yaml" + e.getMessage());
        }

        return allFilesAreValid;
    }

    /**
     * Creates a map of repository references mapped to the commit that it was changed with
     *
     * @param filesWithCommits The map to be added to
     * @param repository The repository is being pushed to
     * @param commit The new commit with file changes
     */
    private void addFileChangesOnCommit(ConcurrentMap<String, Commit> filesWithCommits, Repository repository, Commit commit, String yamlFileExtension) {
        final ChangesRequest changesRequest = new ChangesRequest.Builder(repository, commit.getId()).build();
        final Page<Change> changes = commitService.getChanges(changesRequest, PageUtils.newRequest(0, PAGE_REQUEST_LIMIT));

        for(Change change : changes.getValues()) {
            LOG.debug("Change type was: " + change.getType().name());
            if (!ChangeType.DELETE.equals(change.getType())) {
                LOG.debug("");
                String extension = change.getPath().getExtension();
                if (extension != null && extension.matches(yamlFileExtension)) {
                    if (filesWithCommits.containsKey(change.getPath().toString())) {
                        if (commit.getAuthorTimestamp().after(filesWithCommits.get(change.getPath().toString()).getAuthorTimestamp())) {
                            filesWithCommits.replace(change.getPath().toString(), commit);
                        }
                    } else {
                        filesWithCommits.putIfAbsent(change.getPath().toString(), commit);
                    }
                }
            }
        }
    }
}
