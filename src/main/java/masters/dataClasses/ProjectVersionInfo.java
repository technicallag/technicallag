package masters.dataClasses;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ProjectVersionInfo implements Comparable {
    private Version versObject;
    private String versionString;
    private Timestamp time = null;
    private HashSet<Dependency> dependencies = new HashSet<>();
    private List<DependencyVersionChange> changes = new ArrayList<>();
    private ProjectVersionInfo next = null;

    public ProjectVersionInfo(String versionString) {
        this.versObject = Version.create(versionString);
        this.versionString = versionString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProjectVersionInfo version = (ProjectVersionInfo) o;

        return versObject != null ? versObject.equals(version.versObject) : version.versObject == null;
    }

    @Override
    public int hashCode() {
        return versObject != null ? versObject.hashCode() : 0;
    }

    public int compareTo(Object other) {
        return this.versObject.compareTo(((ProjectVersionInfo)other).versObject);
    }

    public Version getVersObject() {
        return versObject;
    }

    public void setVersObject(Version versObject) {
        this.versObject = versObject;
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

    public Timestamp getTime() {
        return time;
    }

    public void setTime(String timestamp) {
        this.time = Timestamp.valueOf(timestamp.substring(0, timestamp.length()-4));
    }

    public ProjectVersionInfo getNext() {
        return next;
    }

    public void setNext(ProjectVersionInfo next) {
        this.next = next;
    }
}
