package com.mcmanus.scm.stash.hook;

import com.atlassian.stash.commit.*;
import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.ChangeType;
import com.atlassian.stash.content.ChangesRequest;
import com.atlassian.stash.content.ContentService;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.idx.CommitIndex;
import com.atlassian.stash.io.MoreSuppliers;
import com.atlassian.stash.io.TypeAwareOutputSupplier;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class YamlValidatorPreReceiveRepositoryHook implements PreReceiveRepositoryHook
{
    private static final Logger LOG = LoggerFactory.getLogger(PreReceiveRepositoryHook.class);
    private static final int PAGE_REQUEST_LIMIT = 9999;
    private static final String YAML_FILE_EXTENSION = "yaml";
    private static final String WORKING_DIR = "yamlValidator";
    private final CommitService commitService;
    private final ApplicationPropertiesService applicationPropertiesService;
    private final ContentService contentService;
    private final CommitIndex commitIndex;
    private final YamlFileValidator yamlFileValidator;

    public YamlValidatorPreReceiveRepositoryHook(CommitService commitService,
                                                 ApplicationPropertiesService applicationPropertiesService,
                                                 ContentService contentService,
                                                 CommitIndex commitIndex){
        this.commitService = commitService;
        this.applicationPropertiesService = applicationPropertiesService;
        this.contentService = contentService;
        this.commitIndex = commitIndex;
        this.yamlFileValidator = new YamlFileValidator();
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
        for (RefChange refChange : refChanges) {
            LOG.debug("Processing refchange of type: " + refChange.getType());

            Collection<Commit> commitsToCheck = Collections.synchronizedList(new ArrayList<Commit>());

            findCommitsToCheck(refChange.getToHash(), context.getRepository(), commitsToCheck);

            for (Commit commit : commitsToCheck) {
                addFileChangesOnCommit(pathChanges, context.getRepository(), commit);
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
        if (!commitIndex.isMemberOf(hash, repository)) {
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
        Path tempDirPath;
        try {
            tempDirPath = createTempDir();
            for (String filePath : pathChanges.keySet()){
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                TypeAwareOutputSupplier os = MoreSuppliers.newTypeAwareOutputSupplierOf(outputStream);

                contentService.streamFile(
                        repository,
                        pathChanges.get(filePath).getId(),
                        filePath,
                        os);

                String uuid = UUID.randomUUID().toString();
                Path tempFilePath = Paths.get(tempDirPath.toString(), uuid + "." + YAML_FILE_EXTENSION);

                try {
                    LOG.debug("Writing temporary yaml file in order to validate " + tempFilePath);
                    outputStream.writeTo(Files.newOutputStream(tempFilePath));
                } finally {
                    outputStream.close();
                }

                boolean fileIsValid = yamlFileValidator.isValidYamlFile(tempFilePath, hookResponse, filePath);
                removeTempFile(tempFilePath);

                if (!fileIsValid) {
                    allFilesAreValid = false;
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
     * Removes the temporary file which has been created in order to test the yaml
     * @param file The file to be removed
     */
    private void removeTempFile(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            LOG.error("Unable to delete temporary file: " + e.getMessage());
        }
    }

    /**
     * Create a working directory for the push validation
     *
     * @return The path of the temporary directory
     */
    private Path createTempDir() throws IOException {
        File stashTempDir = applicationPropertiesService.getTempDir();
        Path tempFilesDir = Paths.get(stashTempDir.getPath(), WORKING_DIR);
        Files.createDirectories(tempFilesDir);

        return tempFilesDir;
    }

    /**
     * Creates a map of repository references mapped to the commit that it was changed with
     *
     * @param filesWithCommits The map to be added to
     * @param repository The repository is being pushed to
     * @param commit The new commit with file changes
     */
    private void addFileChangesOnCommit(ConcurrentMap<String, Commit> filesWithCommits, Repository repository, Commit commit) {
        final ChangesRequest changesRequest = new ChangesRequest.Builder(repository, commit.getId()).build();
        final Page<Change> changes = commitService.getChanges(changesRequest, PageUtils.newRequest(0, PAGE_REQUEST_LIMIT));

        for(Change change : changes.getValues()) {
            if (!ChangeType.DELETE.equals(change.getType())) {
                if (change.getPath().getExtension().equalsIgnoreCase(YAML_FILE_EXTENSION)) {
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
