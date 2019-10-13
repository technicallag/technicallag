CREATE DATABASE libio;

\connect libio;

CREATE TABLE projects (ID varchar, Platform varchar, Name varchar, CreatedTimestamp  varchar, UpdatedTimestamp varchar, Description varchar, Keywords varchar, HomepageURL varchar, Licenses varchar, RepositoryURL varchar, VersionsCount varchar, SourceRank varchar, LatestReleasePublishTimestamp varchar, LatestReleaseNumber varchar, PackageManagerID varchar, DependentProjectsCount varchar, Language varchar, Status varchar, LastSyncedTimestamp varchar, DependentRepositoriesCount varchar, RepositoryID varchar);

CREATE TABLE versions (ID varchar,Platform varchar,ProjectName varchar,ProjectID  varchar,Number varchar,PublishedTimestamp varchar, CreatedTimestamp varchar, UpdatedTimestamp varchar);

CREATE TABLE dependencies (ID varchar, Platform varchar, ProjectName varchar, ProjectID varchar, VersionNumber varchar, VersionID varchar, DependencyName varchar, DependencyPlatform varchar, DependencyKind varchar, OptionalDependency varchar, DependencyRequirements varchar, DependencyProjectID varchar);

SET CLIENT_ENCODING TO 'utf8';

COPY projects FROM D:\Libaries.io_dataset\libraries-1.4.0-2018-12-22\projects-1.4.0-2018-12-22.csv WITH (FORMAT csv);

COPY versions FROM D:\Libaries.io_dataset\libraries-1.4.0-2018-12-22\versions-1.4.0-2018-12-22.csv WITH (FORMAT csv);

COPY dependencies FROM D:\Libaries.io_dataset\libraries-1.4.0-2018-12-22\dependencies-1.4.0-2018-12-22.csv WITH (FORMAT csv);

DELETE FROM dependencies WHERE platform='Platform';

DELETE FROM projects WHERE platform='Platform';

DELETE FROM versions WHERE platform='Platform';

CREATE INDEX timestamp ON versions (ProjectName, Number);