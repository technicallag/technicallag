package masters.dataClasses;

import java.sql.Connection;
import java.sql.Timestamp;
import utils.Database;

public class Dependency {
    private String dep;
    private String version;
    private Timestamp timestamp = null;
    private Dependency next = null;
    private ProjectVersionInfo parent = null;

    public Dependency(String project, String version) {
        this.setDep(project);
        this.setVersion(version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Dependency that = (Dependency) o;

        if (!getDep().equals(that.getDep())) return false;
        return getVersion().equals(that.getVersion());
    }

    @Override
    public int hashCode() {
        int result = getDep().hashCode();
        result = 31 * result + getVersion().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return getDep() + "\t" + getVersion();
    }

    public String getDep() {
        return dep;
    }

    public void setDep(String dep) {
        this.dep = dep;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTimestamp(Connection c) {
        if (timestamp == null) {
            setTimestamp(Database.timestampFromDB(c, dep, version));
        }
        return timestamp.toString().substring(0,10);
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = Timestamp.valueOf(timestamp.substring(0, timestamp.length()-4));
    }

    public Dependency getNext() {
        return next;
    }

    public void setNext(Dependency next) {
        this.next = next;
    }

    public ProjectVersionInfo getParent() {
        return parent;
    }

    public void setParent(ProjectVersionInfo parent) {
        this.parent = parent;
    }
}
