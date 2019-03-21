## Background

This plugin checks invalid yaml files which are pushed to a repository. It has no affect on files which do not have the extension of .yaml.

## Installation

Install this plugin from Atlassian Market place via the UPM (Universal Plugin Manager) on your instance

## Enable

You can enable the plugin on a repository basis. Go to the settings of the repository and in the hooks tab you can enable/disable the plugin

## Push Reject

When you attempt to push bad yaml the plugin will reject your push and give you a message indicating the problem

## Development

You can develop against this plugin in the normal way you would any Atlassian Plugin, you can see the getting started here: https://developer.atlassian.com/docs/getting-started

- In order to run with 5.0.0 you have to run the plugin via: 

```
atlas-run -u 6.3.0
```

## Releasing

When you have commit your changes, tested and built a binary you can release the plugin, with the following commands:

```
atlas-mvn release:prepare
```

and then:

```
atlas-mvn release:perform
```

## Appendix

Some of the generated docs from the plugin SDK:

* atlas-run   -- installs this plugin into the product and starts it on localhost
* atlas-debug -- same as atlas-run, but allows a debugger to attach at port 5005
* atlas-cli   -- after atlas-run or atlas-debug, opens a Maven command line window:
                 - 'pi' reinstalls the plugin into the running product instance
* atlas-help  -- prints description for all commands in the SDK

Full documentation is always available at:

https://developer.atlassian.com/display/DOCS/Introduction+to+the+Atlassian+Plugin+SDK


## References

Example plugins:
https://bitbucket.org/atlassian/bitbucket-server-example-plugins/src/9e1728cfab668ab0f8c1c4ada507994dffa5b5e6/hooks-guide/src/main/java/com/atlassian/bitbucket/server/examples/BranchInReviewHook.java?at=master&fileviewer=file-view-default