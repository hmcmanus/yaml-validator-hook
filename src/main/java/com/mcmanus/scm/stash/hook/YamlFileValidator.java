package com.mcmanus.scm.stash.hook;

import com.atlassian.stash.hook.HookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class YamlFileValidator {

    private static final Logger LOG = LoggerFactory.getLogger(YamlFileValidator.class);

    /**
     * This method checks a individual file to make sure it's valid yaml
     *
     * @param file The temporary file to be checked
     * @param hookResponse The response to the user
     * @param filePath The reference file path to the repository
     * @return boolean denoting if the file is valid or not
     */
    public boolean isValidYamlFile(Path file, HookResponse hookResponse, String filePath) {
        boolean fileIsValid = true;
        InputStream input = null;
        Yaml yaml = new Yaml();
        try {
            input = new FileInputStream(file.toFile());
            try {
                LOG.info("Attempting to validate file: " + filePath);
                yaml.load(input);
            } catch (Exception e) {
                LOG.info("Rejecting push because following yaml file is invalid " + filePath);
                hookResponse.err().println("ERROR: Invalid yaml file: " + filePath);
                hookResponse.err().println(e.getMessage());
                fileIsValid = false;
            }
        } catch (Exception e) {
            LOG.error("Unable to create input stream for temporary file");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    LOG.error("Unable to close input stream");
                }
            }
        }
        return fileIsValid;
    }
}
