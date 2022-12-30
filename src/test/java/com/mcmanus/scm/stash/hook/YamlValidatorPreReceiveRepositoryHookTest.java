package com.mcmanus.scm.stash.hook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.content.*;
import com.atlassian.bitbucket.idx.CommitIndex;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class YamlValidatorPreReceiveRepositoryHookTest {

    // Required testing holes:
    // 1) Need to make sure the temp files/directory is deleted once the validation is complete
    // 2) Need to check that a commit with an incorrect file and then a commit with a fix to that file works
    // 3) Need to check if a non yaml file is allowed
    // 4) Need to check if a commit with a yaml file and a text file works
    // 5) Need to check if a commit with a incorrect yaml file and a text file works
    // 6) Need to check if a delete of an incorrect yaml file works

    @Test
    public void shouldCheckAndAddFilesWithParticularExtension() {

        CommitService commitServiceMock = mock(CommitService.class);
        ContentService contentServiceMock = mock(ContentService.class);
        CommitIndex commitIndexMock = mock(CommitIndex.class);

        Commit commitMock = mock(Commit.class);
        Repository repositoryMock = mock(Repository.class);

        ChangesRequest.Builder builderMock = mock(ChangesRequest.Builder.class);
        ChangesRequest changesRequestMock = mock(ChangesRequest.class);

        Change change = mock(Change.class);
        ArrayList<Change> changes = new ArrayList<>();
        changes.add(change);

        Page<Change> pagedChanges = PageUtils.createPage(changes, PageUtils.newRequest(1, 1));

        when(commitMock.getId()).thenReturn("asdfh329fhpehguh");
        when(builderMock.build()).thenReturn(changesRequestMock);
        when(commitServiceMock.getChanges(any(ChangesRequest.class), any(PageRequest.class))).thenReturn(pagedChanges);
        when(change.getType()).thenReturn(ChangeType.ADD);
        when(change.getPath()).thenReturn(new SimplePath("/right/here.yaml"));

        YamlValidatorPreReceiveRepositoryHook hook = new YamlValidatorPreReceiveRepositoryHook(commitServiceMock,
                contentServiceMock, commitIndexMock);

        ConcurrentMap<String, Commit> testPathChanges = new ConcurrentHashMap<>();

        hook.addFileChangesOnCommit(testPathChanges, repositoryMock, commitMock, "yaml");

        assertThat(testPathChanges.size(), is(1));
    }

    @Test
    public void shouldTestSimpleYamlFile() throws IOException {
        CommitService commitServiceMock = mock(CommitService.class);
        ContentService contentServiceMock = mock(ContentService.class);
        CommitIndex commitIndexMock = mock(CommitIndex.class);

        ClassPathResource classPathResource = new ClassPathResource("good.yaml");
        File resource = classPathResource.getFile();
        String testString = new String(Files.readAllBytes(Paths.get(resource.getPath())));

        YamlValidatorPreReceiveRepositoryHook hook = new YamlValidatorPreReceiveRepositoryHook(commitServiceMock,
                contentServiceMock, commitIndexMock);

        ConcurrentMap<String, String> results = new ConcurrentHashMap<>();
        boolean check = hook.checkFile(testString, results, resource.getPath());

        assertTrue(check);
    }

    @Test
    public void shouldTestSimpleBadYamlFile() throws IOException {
        CommitService commitServiceMock = mock(CommitService.class);
        ContentService contentServiceMock = mock(ContentService.class);
        CommitIndex commitIndexMock = mock(CommitIndex.class);

        ClassPathResource classPathResource = new ClassPathResource("bad.yaml");
        File resource = classPathResource.getFile();
        String testString = new String(Files.readAllBytes(Paths.get(resource.getPath())));

        YamlValidatorPreReceiveRepositoryHook hook = new YamlValidatorPreReceiveRepositoryHook(commitServiceMock,
                contentServiceMock, commitIndexMock);

        ConcurrentMap<String, String> results = new ConcurrentHashMap<>();
        boolean check = hook.checkFile(testString, results, resource.getPath());

        System.out.println(check);
        assertFalse(check);
    }

    @Test
    public void shouldTestMultiYamlFile() throws IOException {
        CommitService commitServiceMock = mock(CommitService.class);
        ContentService contentServiceMock = mock(ContentService.class);
        CommitIndex commitIndexMock = mock(CommitIndex.class);

        ClassPathResource classPathResource = new ClassPathResource("multi-good.yaml");
        File resource = classPathResource.getFile();
        String testString = new String(Files.readAllBytes(Paths.get(resource.getPath())));

        YamlValidatorPreReceiveRepositoryHook hook = new YamlValidatorPreReceiveRepositoryHook(commitServiceMock,
                contentServiceMock, commitIndexMock);

        ConcurrentMap<String, String> results = new ConcurrentHashMap<>();
        boolean check = hook.checkFile(testString, results, resource.getPath());

        assertTrue(check);
    }

    @Test
    public void shouldTestMultiBadYamlFile() throws IOException {
        CommitService commitServiceMock = mock(CommitService.class);
        ContentService contentServiceMock = mock(ContentService.class);
        CommitIndex commitIndexMock = mock(CommitIndex.class);

        ClassPathResource classPathResource = new ClassPathResource("multi-bad.yaml");
        File resource = classPathResource.getFile();
        String testString = new String(Files.readAllBytes(Paths.get(resource.getPath())));

        YamlValidatorPreReceiveRepositoryHook hook = new YamlValidatorPreReceiveRepositoryHook(commitServiceMock,
                contentServiceMock, commitIndexMock);

        ConcurrentMap<String, String> results = new ConcurrentHashMap<>();
        boolean check = hook.checkFile(testString, results, resource.getPath());

        assertFalse(check);
    }

    @Ignore("https://github.com/hmcmanus/yaml-validator-hook/issues/25")
    @Test
    public void shouldTestTaggedYamlFile() throws IOException {
        CommitService commitServiceMock = mock(CommitService.class);
        ContentService contentServiceMock = mock(ContentService.class);
        CommitIndex commitIndexMock = mock(CommitIndex.class);

        ClassPathResource classPathResource = new ClassPathResource("tagged.yaml");
        File resource = classPathResource.getFile();
        String testString = new String(Files.readAllBytes(Paths.get(resource.getPath())));

        YamlValidatorPreReceiveRepositoryHook hook = new YamlValidatorPreReceiveRepositoryHook(commitServiceMock,
                contentServiceMock, commitIndexMock);

        ConcurrentMap<String, String> results = new ConcurrentHashMap<>();
        boolean check = hook.checkFile(testString, results, resource.getPath());

        assertTrue(check);
    }
}
