CREATE DATABASE libio;

\connect libio;

CREATE TABLE projects (ID varchar, Platform varchar, Name varchar, CreatedTimestamp  varchar, UpdatedTimestamp varchar, Description varchar, Keywords varchar, HomepageURL varchar, Licenses varchar, RepositoryURL varchar, VersionsCount varchar, SourceRank varchar, LatestReleasePublishTimestamp varchar, LatestReleaseNumber varchar, PackageManagerID varchar, DependentProjectsCount varchar, Language varchar, Status varchar, LastSyncedTimestamp varchar, DependentRepositoriesCount varchar, RepositoryID varchar);

CREATE TABLE versions (ID varchar,Platform varchar,ProjectName varchar,ProjectID  varchar,Number varchar,PublishedTimestamp varchar, CreatedTimestamp varchar, UpdatedTimestamp varchar);

CREATE TABLE dependencies (ID varchar, Platform varchar, ProjectName varchar, ProjectID varchar, VersionNumber varchar, VersionID varchar, DependencyName varchar, DependencyPlatform varchar, DependencyKind varchar, OptionalDependency varchar, DependencyRequirements varchar, DependencyProjectID varchar);

CREATE TABLE tags (ID varchar, HostType varchar, RepositoryNameWithOwner varchar, RepositoryID varchar, TagName varchar, TagGitSha varchar, TagPublishedTimestamp varchar, TagCreatedTimestamp varchar, TagUpdatedTimestamp varchar);

SET CLIENT_ENCODING TO 'utf8';

COPY projects FROM -- projects csv file location and name
WITH (FORMAT csv);

COPY versions FROM -- versions csv file location and name
WITH (FORMAT csv);

COPY dependencies FROM -- dependencies csv file location and name
WITH (FORMAT csv);

COPY tags FROM -- tag csv file location and name
WITH (FORMAT csv);

DELETE FROM dependencies WHERE platform='Platform';

DELETE FROM projects WHERE platform='Platform';

DELETE FROM versions WHERE platform='Platform';

--CREATE INDEX timestamp ON versions (ProjectName, Number);
--CREATE INDEX projectName ON projects(Name);
CREATE INDEX projectID ON projects(id);
CREATE INDEX repositoryID1 ON tags(repositoryid);
CREATE INDEX repositoryID2 ON tags(repositoryid, tagname);
CREATE INDEX versionprojectID ON versions(projectid);
CREATE INDEX depreq ON dependencies(projectid, dependencyprojectid, versionid);

CREATE TABLE pairs(projectID int, dependencyID int, package varchar, status varchar);
CREATE TABLE printed(projectID int, dependencyID int);
CREATE INDEX printindex ON printed(projectID, dependencyID);