package masters.dataClasses;

public class DependencyVersionChange {
    private String project;
    private String oldVers;
    private String newVers;
    private VersionRelationship versionRelationship;

    public DependencyVersionChange(String project, String oldVers, String newVers) {
        this.setProject(project);
        this.setOldVers(oldVers);
        this.setNewVers(newVers);
        versionRelationship = Version.create(oldVers).getRelationship(Version.create(newVers));
    }

    public String toString() {
        String sep = "\t";
        return getProject() + sep + getOldVers() + sep + getNewVers();
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getOldVers() {
        return oldVers;
    }

    public void setOldVers(String oldVers) {
        this.oldVers = oldVers;
    }

    public String getNewVers() {
        return newVers;
    }

    public void setNewVers(String newVers) {
        this.newVers = newVers;
    }


    public VersionRelationship getVersionRelationship() {
        return versionRelationship;
    }

    public void setVersionRelationship(VersionRelationship versionRelationship) {
        this.versionRelationship = versionRelationship;
    }
}
