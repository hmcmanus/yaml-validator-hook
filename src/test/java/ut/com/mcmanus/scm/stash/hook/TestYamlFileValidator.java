package ut.com.mcmanus.scm.stash.hook;

import com.atlassian.stash.hook.HookResponse;
import com.mcmanus.scm.stash.hook.YamlFileValidator;
import org.junit.Test;

import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TestYamlFileValidator {

    @Test
    public void shouldValidateCorrectYamlFile() throws Throwable {
        URI testFile = Thread.currentThread().getContextClassLoader().getResource("good.yaml").toURI();
        HookResponse mockHookResponse = mock(HookResponse.class);
        YamlFileValidator yamlFileValidator = new YamlFileValidator();

        assertTrue(yamlFileValidator.isValidYamlFile(Paths.get(testFile), mockHookResponse, "src/test/resources/good.yaml"));

        verify(mockHookResponse, times(0)).err();
    }

    @Test
    public void shouldInValidateIncorrectYamlFile() throws Throwable {
        URI testFile = Thread.currentThread().getContextClassLoader().getResource("bad.yaml").toURI();
        HookResponse mockHookResponse = mock(HookResponse.class);
        PrintWriter mockPrintWriter = mock(PrintWriter.class);
        YamlFileValidator yamlFileValidator = new YamlFileValidator();

        when(mockHookResponse.err()).thenReturn(mockPrintWriter);

        assertFalse(yamlFileValidator.isValidYamlFile(Paths.get(testFile), mockHookResponse, "src/test/resources/bad.yaml"));

        verify(mockPrintWriter, times(1)).println("ERROR: Invalid yaml file: src/test/resources/bad.yaml");
    }

}
