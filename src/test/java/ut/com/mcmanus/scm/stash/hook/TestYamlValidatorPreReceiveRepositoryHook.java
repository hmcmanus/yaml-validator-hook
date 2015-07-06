package ut.com.mcmanus.scm.stash.hook;

public class TestYamlValidatorPreReceiveRepositoryHook {

    // Required testing holes:
    // 1) Need to make sure the temp files/directory is deleted once the validation is complete
    // 2) Need to check that a commit with an incorrect file and then a commit with a fix to that file works
    // 3) Need to check if a non yaml file is allowed
    // 4) Need to check if a commit with a yaml file and a text file works
    // 5) Need to check if a commit with a incorrect yaml file and a text file works
    // 6) Need to check if a delete of an incorrect yaml file works
}
