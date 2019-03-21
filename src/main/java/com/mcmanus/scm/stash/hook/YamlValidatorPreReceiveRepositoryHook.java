package com.mcmanus.scm.stash.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.MinimalCommit;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeType;
import com.atlassian.bitbucket.content.ChangesRequest;
import com.atlassian.bitbucket.content.ContentService;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.idx.CommitIndex;
import com.atlassian.bitbucket.io.TypeAwareOutputSupplier;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.MoreSuppliers;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageUtils;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ExportAsService({YamlValidatorPreReceiveRepositoryHook.class})
@Named("yamlValidatorRepositoryHook")
public class YamlValidatorPreReceiveRepositoryHook implements PreRepositoryHook
{
    private static final Logger LOG = LoggerFactory.getLogger(PreRepositoryHook.class);

    private static final int PAGE_REQUEST_LIMIT = 9999;
    private static final String EXTENSION_CONFIG_STRING = "extension";
    private static final String SUMMARY = "summary";
    private static final String DETAIL = "detail";

    @ComponentImport
    private final CommitService commitService;
    @ComponentImport
    private final ContentService contentService;
    @ComponentImport
    private final CommitIndex commitIndex;

    @Inject
    public YamlValidatorPreReceiveRepositoryHook(final CommitService commitService,
                                                 final ContentService contentService,
                                                 final CommitIndex commitIndex
                                                 ){
        this.commitService = commitService;
        this.contentService = contentService;
        this.commitIndex = commitIndex;
    }

    /**
     * Hook to check yaml files before allowing the push to complete.
     *
     * @param repository The repository that the changes are part of
     * @param refChanges A set of changes
     * @param yamlFileExtension What type of yaml file are we checking
     * @return Whether to allow the push to continue or not
     */
    public Map<String, String> onReceive(Repository repository, Collection<RefChange> refChanges, String yamlFileExtension)
    {
        ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();
        ConcurrentMap<String, Commit> pathChanges = new ConcurrentHashMap<>();

        for (RefChange refChange : refChanges) {
            LOG.debug("Processing refchange of type: " + refChange.getType());

            Collection<Commit> commitsToCheck = Collections.synchronizedList(new ArrayList<>());

            findCommitsToCheck(refChange.getToHash(), repository, commitsToCheck);

            for (Commit commit : commitsToCheck) {
                addFileChangesOnCommit(pathChanges, repository, commit, yamlFileExtension);
            }
        }

        if (!pathChanges.isEmpty()) {
            areFilesValid(pathChanges, repository, result);
        }

        return result;
    }

    /**
     * Recursive method to get the list of commits which are new on the branch
     *
     * @param hash The commit hash
     * @param repository The repository that the commits have been completed on
     * @param commitsToProcess A list of commits which are new
     */
    private void findCommitsToCheck(String hash, Repository repository, Collection<Commit> commitsToProcess) {
        final String zero = "0000000000000000000000000000000000000000";
        if (zero.equals(hash)) {
            // a new hash of 40 `0` means the branch is to be deleted
            // just let it pass through in that case
            LOG.debug("Found deletion commit");
            return;
        }

        if (!commitIndex.isIndexed(hash, repository)) {
            final CommitRequest request = new CommitRequest.Builder(repository, hash).build();
            final Commit commit = commitService.getCommit(request);
            LOG.debug("Found commit to check " + hash);
            commitsToProcess.add(commit);
            if (commit != null) {
                for (MinimalCommit parent : commit.getParents()) {
                    findCommitsToCheck(parent.getId(), repository, commitsToProcess);
                }
            }
        }
    }

    /**
     * This function checks that all the files are valid that are being pushed
     *
     * @param pathChanges Map of the string paths with their associated commits
     * @param repository The repository that the push is for
     * @param result Map holding the response to be sent back to the client
     *
     * @return A boolean denoting if the yaml files are valid
     */
    private boolean areFilesValid(ConcurrentMap<String, Commit> pathChanges, Repository repository, ConcurrentMap<String, String> result) {
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
                    if(!checkFile(outputStream.toString(), result, filePath)) {
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

    boolean checkFile(String fileString, ConcurrentMap<String, String> result, String filePath) {
        boolean validFile = true;
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(true);
        Yaml yaml = new Yaml(loaderOptions);
        try {
            LOG.info("Attempting to validate yaml stream");
            yaml.load(fileString);
        } catch (Exception e) {
            LOG.info("Rejecting push because following yaml file is invalid: " + filePath);
            result.putIfAbsent(SUMMARY, "ERROR: Invalid yaml file: " + filePath);
            result.putIfAbsent(DETAIL, e.getMessage());
            validFile = false;
        }
        return validFile;
    }

    /**
     * Creates a map of repository references mapped to the commit that it was changed with
     *
     * @param filesWithCommits The map to be added to
     * @param repository The repository is being pushed to
     * @param commit The new commit with file changes
     */
    public void addFileChangesOnCommit(ConcurrentMap<String, Commit> filesWithCommits, Repository repository, Commit commit, String yamlFileExtension) {
        final ChangesRequest changesRequest = new ChangesRequest.Builder(repository, commit.getId()).build();
        final Page<Change> changes = commitService.getChanges(changesRequest, PageUtils.newRequest(0, PAGE_REQUEST_LIMIT));

        if (changes != null) {
            for (Change change : changes.getValues()) {
                LOG.debug("Change type was: " + change.getType().name());
                if (!ChangeType.DELETE.equals(change.getType())) {

                    String extension = null;
                    if (change.getPath() != null) {
                        extension = change.getPath().getExtension();
                    }
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

    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(@Nonnull PreRepositoryHookContext preRepositoryHookContext, @Nonnull RepositoryHookRequest repositoryHookRequest) {
        RepositoryHookResult result;
        Map<String, String> processedResults = onReceive(repositoryHookRequest.getRepository(),
                repositoryHookRequest.getRefChanges(),
                preRepositoryHookContext.getSettings().getString(EXTENSION_CONFIG_STRING));

        if (processedResults.containsKey(SUMMARY)) {
            result = RepositoryHookResult.rejected(processedResults.get(SUMMARY), processedResults.get(DETAIL));
        } else {
            result = RepositoryHookResult.accepted();
        }
        return result;
    }
}
