This project uses the project, version and dependencies tables from the libraries.io/data set.

Unpack these files and run the `dbsetup.sql` file after updating the `copy` commands with the path to the relevant files.

This is a Maven project. It can be run through the command line using:

`mvn compile`

`mvn exec:java "-Dexec.mainClass=masters.Main" "-Dexec.args=-Xmx2g"`