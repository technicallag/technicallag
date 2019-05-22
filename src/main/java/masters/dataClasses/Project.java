package masters.dataClasses;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.google.common.collect.Sets;

public class Project {
    private String name;
    private HashMap<String, ProjectVersionInfo> versions = new HashMap<>();
    private ProjectVersionInfo firstVersion = null;

    public Project(String name) {
        this.setName(name);
    }

    public void addProjectInfo(String vers, Dependency dep) {
        getVersions().putIfAbsent(vers, new ProjectVersionInfo(vers));
        getVersions().get(vers).getDependencies().add(dep);
        dep.setParent(getVersions().get(vers));
    }

    public void compareVersions() {
        // We don't care about projects that don't have multiple versions
        if (getVersions().size() < 2) return;

        // Order versions
        List<ProjectVersionInfo> sortedVersions = new ArrayList<>(getVersions().values());
        Collections.sort(sortedVersions);

        // Print out differences between neighbouring versions
        ProjectVersionInfo old = sortedVersions.get(0);
        this.setFirstVersion(old); // Track head of linked list
        for (int i = 1; i < sortedVersions.size(); i++) {
            ProjectVersionInfo next = sortedVersions.get(i);
            old.setNext(next); // Track order of versions

            Sets.SetView<Dependency> inOld = Sets.difference(old.getDependencies(), next.getDependencies());
            Sets.SetView<Dependency> inNew = Sets.difference(next.getDependencies(), old.getDependencies());

            // Compare differences for updated versions of the same projects. Discard all others.
            List<DependencyVersionChange> changes = old.getChanges();
            for (Dependency oldD: inOld) {
                for (Dependency newD: inNew) {
                    if (oldD.getProjectName().equals(newD.getProjectName())) {
                        oldD.setNext(newD); // Track linked list of dependency
                        changes.add(new DependencyVersionChange(oldD.getProjectName(), oldD.getVersion(), newD.getVersion()));
                        break;
                    }
                }
            }

            old = next;
        }
    }

    public void printChangesToFile(File f) throws IOException {
        // We don't care about projects that don't have multiple versions
        if (getVersions().size() < 2) return;

        // Order versions
        List<ProjectVersionInfo> sortedVersions = new ArrayList<>(getVersions().values());
        Collections.sort(sortedVersions);

        // Print out differences between neighbouring versions
        ProjectVersionInfo old = sortedVersions.get(0);
        boolean written = false;
        BufferedWriter out = null;

        for (int i = 0; i < sortedVersions.size() - 1; i++) {
            List<DependencyVersionChange> changes = sortedVersions.get(i).getChanges();

            // File changes to file
            if (changes.size() > 0) {
                // Only create file if there are results
                if (!written) {
                    out = new BufferedWriter(new FileWriter(f));
                    written = true;
                }

                // Write in diffs
                out.write(sortedVersions.get(i).getVersionString() + "\t" + sortedVersions.get(i+1).getVersionString() + "\n");
                for (DependencyVersionChange vc: changes)
                    out.write(vc + "\n");
                out.write("\n");
            }
        }

        if (written) out.close();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project project = (Project) o;

        return getName() != null ? getName().equals(project.getName()) : project.getName() == null;
    }


    @Override
    public int hashCode() {
        return getName() != null ? getName().hashCode() : 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HashMap<String, ProjectVersionInfo> getVersions() {
        return versions;
    }

    public void setVersions(HashMap<String, ProjectVersionInfo> versions) {
        this.versions = versions;
    }

    public ProjectVersionInfo getFirstVersion() {
        return firstVersion;
    }

    public void setFirstVersion(ProjectVersionInfo firstVersion) {
        this.firstVersion = firstVersion;
    }
}
