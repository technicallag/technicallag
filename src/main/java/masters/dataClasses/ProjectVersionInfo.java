package masters.dataClasses;

import masters.utils.Database;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ProjectVersionInfo implements Comparable<ProjectVersionInfo> {
    private Version version;
    private String versionString;
    private Timestamp timestamp = null;
    private HashSet<Dependency> dependencies = new HashSet<>();
    private List<DependencyVersionChange> changes = new ArrayList<>();
    private ProjectVersionInfo next = null;

    public ProjectVersionInfo(String versionString) {
        this.version = Version.create(versionString);
        this.versionString = versionString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProjectVersionInfo version = (ProjectVersionInfo) o;

        return this.version != null ? this.version.equals(version.version) : version.version == null;
    }

    @Override
    public int hashCode() {
        return version != null ? version.hashCode() : 0;
    }

    public int compareTo(ProjectVersionInfo other) {
        return this.version.compareTo(other.version);
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public String getVersionString() {
        return versionString;
    }

    public void setVersionString(String versionString) {
        this.versionString = versionString;
    }

    public HashSet<Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(HashSet<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<DependencyVersionChange> getChanges() {
        return changes;
    }

    public void setChanges(List<DependencyVersionChange> changes) {
        this.changes = changes;
    }

    public String getTimestamp(Connection c, String project) {
        if (timestamp == null) {
            setTimestamp(Database.timestampFromDB(c, project, versionString));
        }
        return timestamp.toString().substring(0,10);
    }

    public Timestamp getTimestampNullable(Connection c, String project) {
        if (timestamp == null) {
            setTimestamp(Database.timestampFromDB(c, project, versionString));
        }
        return timestamp;
    }

    @Nullable
    public Timestamp getTimestampNullable() {
        return timestamp;
    }

    public String getTimeStringNullable() {
        return timestamp.toString().substring(0,10);
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = Timestamp.valueOf(timestamp.substring(0, timestamp.length()-4));
    }

    public ProjectVersionInfo getNext() {
        return next;
    }

    public void setNext(ProjectVersionInfo next) {
        this.next = next;
    }
}
