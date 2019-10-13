# Import data from libraries.io/data.

## DB Schema:
`create table projects (ID varchar, Platform varchar, Name varchar, CreatedTimestamp  varchar, UpdatedTimestamp varchar, Description varchar, Keywords varchar, HomepageURL varchar, Licenses varchar, RepositoryURL varchar, VersionsCount varchar, SourceRank varchar, LatestReleasePublishTimestamp varchar, LatestReleaseNumber varchar, PackageManagerID varchar, DependentProjectsCount varchar, Language varchar, Status varchar, LastSyncedTimestamp varchar, DependentRepositoriesCount varchar, RepositoryID varchar);`

`create table versions (ID varchar,Platform varchar,ProjectName varchar,ProjectID  varchar,Number varchar,PublishedTimestamp varchar, CreatedTimestamp varchar, UpdatedTimestamp varchar);`

`CREATE TABLE dependencies (ID varchar, Platform varchar, ProjectName varchar, ProjectID varchar, VersionNumber varchar, VersionID varchar, DependencyName varchar, DependencyPlatform varchar, DependencyKind varchar, OptionalDependency varchar, DependencyRequirements varchar, DependencyProjectID varchar);`

## Set to UTF-8
`SET CLIENT_ENCODING TO 'utf8';`

## Import data: (change path to file if needed)
`COPY projects FROM 'D:\Libaries.io_dataset\libraries-1.4.0-2018-12-22\projects-1.4.0-2018-12-22.csv' WITH (FORMAT csv);`
`COPY versions FROM 'D:\Libaries.io_dataset\libraries-1.4.0-2018-12-22\versions-1.4.0-2018-12-22.csv' WITH (FORMAT csv);`
`COPY dependencies FROM 'D:\Libaries.io_dataset\libraries-1.4.0-2018-12-22\dependencies-1.4.0-2018-12-22.csv' WITH (FORMAT csv);`

## Remove headers
delete from dependencies where platform='Platform';
delete from projects where platform='Platform';
delete from versions where platform='Platform';

## Create indices
CREATE INDEX timestamp ON versions (ProjectName, Number);



# Run
mvn compile
mvn exec:java "-Dexec.mainClass=masters.Main" "-Dexec.args=-Xmx2g"