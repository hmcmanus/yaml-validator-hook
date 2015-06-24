package ut.com.mcmanus.scm.stash.hook;

import com.atlassian.stash.commit.Commit;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.commit.CommitsBetweenRequest;
import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.ChangesRequest;
import com.atlassian.stash.content.ContentService;
import com.atlassian.stash.content.Path;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.mcmanus.scm.stash.hook.YamlValidatorPreReceiveRepositoryHook;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestYamlValidatorPreReceiveRepositoryHook {

    private CommitService mockedCommitService;
    private ApplicationPropertiesService mockPropService;
    private ContentService mockedContentService;
    private RepositoryHookContext mockRepositoryHookContext;
    private RefChange mockRefChange;
    private Collection<RefChange> refChanges;
    private HookResponse mockHookResponse;
    private Page<Commit> mockPageCommits;
    private Commit mockCommit;
    private List<Commit> commitList;
    private Repository mockRepository;
    private Page<Change> mockPageChanges;
    private Change mockChange;
    private List<Change> changeList;

    @Before
    public void setUp(){
        this.mockedCommitService = mock(CommitService.class);
        this.mockPropService = mock(ApplicationPropertiesService.class);
        this.mockedContentService = mock(ContentService.class);
        this.mockRepositoryHookContext = mock(RepositoryHookContext.class);
        this.mockRefChange = mock(RefChange.class);
        this.refChanges = new ArrayList<RefChange>();
        this.refChanges.add(mockRefChange);
        this.mockHookResponse = mock(HookResponse.class);
        this.mockPageCommits = mock(Page.class);
        this.mockCommit = mock(Commit.class);
        this.commitList = new ArrayList<Commit>();
        this.commitList.add(mockCommit);
        this.mockRepository = mock(Repository.class);
        this.mockPageChanges = mock(Page.class);
        this.mockChange = mock(Change.class);
        this.changeList = new ArrayList<Change>();
        this.changeList.add(mockChange);

        when(mockRefChange.getFromHash()).thenReturn("owejf");
        when(mockRefChange.getToHash()).thenReturn("hksdjkj");
        when(mockedCommitService.getCommitsBetween(any(CommitsBetweenRequest.class), any(PageRequest.class))).thenReturn(mockPageCommits);
        when(mockPageCommits.getValues()).thenReturn(commitList);
        when(mockRepositoryHookContext.getRepository()).thenReturn(mockRepository);
        when(mockCommit.getId()).thenReturn("asdfasdf");
        when(mockedCommitService.getChanges(any(ChangesRequest.class), any(PageRequest.class))).thenReturn(mockPageChanges);
        when(mockPageChanges.getValues()).thenReturn(changeList);
    }

    @Test
    public void shouldValidateCorrectYamlFile() throws Throwable {
        File testFile = new File(Thread.currentThread().getContextClassLoader().getResource("good.yaml").toURI());

        InputStream input = new FileInputStream(testFile);
        Yaml yaml = new Yaml();
        yaml.load(input);
    }

    @Test
    public void shouldInValidateInCorrectYamlFile() throws Throwable {
        File testFile = new File(Thread.currentThread().getContextClassLoader().getResource("bad.yaml").toURI());

        InputStream input = new FileInputStream(testFile);
        Yaml yaml = new Yaml();
        try {
            yaml.load(input);
            fail("Yaml file is incorrect and should have thrown an exception");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void shouldAcceptCommitWithNoYamlFiles() {

        YamlValidatorPreReceiveRepositoryHook yamlValidatorPreReceiveRepositoryHook =
                new YamlValidatorPreReceiveRepositoryHook(mockedCommitService, mockPropService, mockedContentService);

        Path mockPath = mock(Path.class);
        when(mockChange.getPath()).thenReturn(mockPath);
        when(mockPath.getExtension()).thenReturn("xml");

        assertTrue(yamlValidatorPreReceiveRepositoryHook.onReceive(mockRepositoryHookContext, refChanges, mockHookResponse));

    }

    // Required testing holes:
    // 1) Need to make sure the temp files/directory is deleted once the validation is complete
    // 2) Need to check that a commit with an incorrect file and then a commit with a fix to that file works
    // 3) Need to check if a non yaml file is allowed
    // 4) Need to check if a commit with a yaml file and a text file works
    // 5) Need to check if a commit with a incorrect yaml file and a text file works
    // 6) Need to check if a delete of an incorrect yaml file works

}
