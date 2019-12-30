This project uses the project, version and dependencies tables from the libraries.io/data set.

Unpack these files and run the `dbsetup.sql` file (intended for use with the postgres psql shell) after updating the `copy` commands with the path to the relevant files.

This is a Maven project. It can be run through the command line using:

`mvn compile`

`mvn exec:java "-Dexec.mainClass=masters.Main" "-Dexec.args=-Xmx2g"`


## Enabling NPM semver

Integration is based on invoking the NPM function directy using the Java `Process` API. Alternatives are discussed below.

1. install `node` for your OS, e.g. with `brew install node`
2. in the project root folder, install the following NPM packages: 
   1. npm i semver
   2. npm i make-runnable
   3. npm i line-reader
   
   
### Alternatives

1. using https://github.com/yuchi/java-npm-semver project -- this is a port of NPM semver to Java, the test cases look like this is well done, but using the *real thing* is always better
2. using https://github.com/eclipsesource/J2V8 -- we have tried this, and the mvn dependencies could not be resolved