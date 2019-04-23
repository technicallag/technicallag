package masters.dataClasses;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;

// Container class that gathers information from SQL and controls access to the various data classes
public class Results {
    private HashMap<String, Project> projects = new HashMap<>();
    private HashSet<String> projectPairs = new HashSet<>();
    private int FOUND = 0;
    private int NOTFOUND = 0;
    private Logger LOG;

    public Results(Logger log) {
        this.LOG = log;
    }

    public void consumeResults(ResultSet rs) throws SQLException {
        int count = 0;
        while (rs.next()) {
            count++;
            if (count % 100000 == 0)
                LOG.info("Read in line: " + count);

            String name = rs.getString("ProjectName");

            try {
                getProjects().putIfAbsent(name, new Project(name));
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            String version = rs.getString("VersionNumber");

            // Used to avoid submodules being included
            if (version.equals(rs.getString("DependencyRequirements"))) continue;

            Dependency dep = new Dependency (rs.getString("DependencyName"), rs.getString("DependencyRequirements"));
            getProjects().get(name).addProjectInfo(version, dep);
        }
    }

    private String timestampFromDB(Connection c, String projectName, String versionString) {
        try {
            PreparedStatement stmt = c.prepareStatement("select * from versions where ProjectName = ? and Number = ?");
            stmt.setString(1, projectName);
            stmt.setString(2, versionString);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String time = rs.getString("CreatedTimestamp");
                if (time == null) {
                    LOG.debug(String.format("%s version %s doesn't have a timestamp - rs.next okay%n", projectName, versionString));
                    throw new SQLException();
                }
                return time;
            } else {
                LOG.debug(String.format("%s version %s doesn't have a timestamp%n", projectName, versionString));
            }
        } catch (SQLException e) {
            LOG.debug(projectName + " " + versionString);
            e.printStackTrace();
        }
        return "1970-01-01 01:01:01 UTC";
    }

    public void getTimestamps(Connection c) throws SQLException {
        this.getProjects().forEach((projectName,project) -> {
            project.getVersions().forEach((versionString, versionObject) -> {
                // Get timestamps for each version that doesn't yet have them
                if(versionObject.getTime() == null) {
                    versionObject.setTime(timestampFromDB(c, projectName, versionString));
                    // Set timestamps of the dependencies as well
                    versionObject.getDependencies().forEach(dep -> {
                        dep.setTimestamp(timestampFromDB(c, dep.getDep(), dep.getVersion()));
                    });
                }
            });
        });
    }

    public void compareProjects() {
        this.getProjects().forEach((k, v) -> v.compareVersions());
    }

    public void printProjectResults() {
        LOG.info("There were " + getProjects().size() + " projects overall");
        File dir = new File("../data/version-comps");
        if (!dir.exists())
            dir.mkdir();

        this.getProjects().forEach((k, v) -> {
            try {
                File f = new File("../data/version-comps/"+k.replace(":", ",")+".txt");
                v.printChangesToFile(f);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void checkDependenciesAreProjects() {
        this.getProjects().forEach((projName, project) -> {
            project.getVersions().forEach((versionName, version) -> {
                version.getDependencies().forEach(dep -> {
                    Project p = this.getProjects().get(dep.getDep());
                    if (p == null) {
                        LOG.trace(String.format("No project named %s%n", dep.getDep()));
                        dependencyFound(false);
                        return;
                    }
                    ProjectVersionInfo vers = p.getVersions().get(dep.getVersion());
                    if (vers == null) {
                        LOG.trace(String.format("No version %s in project named %s%n", dep.getVersion(), dep.getDep()));
                        dependencyFound(false);
                        return;
                    }
                    LOG.trace(String.format("Project %s, Version %s present: TRUE %n", dep.getDep(), dep.getVersion()));
                    projectPairs.add(project.getName() + "::" + p.getName());
                    dependencyFound(true);
                });
            });
        });
        LOG.info(String.format("Dependencies FOUND: %d, Not FOUND: %d%n", this.FOUND, this.NOTFOUND));
    }

    private void dependencyFound(boolean found) {
        if (found) this.FOUND++;
        else this.NOTFOUND++;
    }

    public void constructTimeline() {
        // Where project a depends on project b
        this.projectPairs.forEach(pair -> {
            Project a = this.getProjects().get(pair.split("::")[0]);
            Project b = this.getProjects().get(pair.split("::")[1]);
            
            ProjectVersionInfo aVersion = a.getFirstVersion();
            ProjectVersionInfo bVersion = b.getFirstVersion();
            
            class Data implements Comparable<Data> {
                Version v, dep;
                Timestamp t, depTime;
                Data (Version v, Timestamp t) {
                    this.v = v;
                    this.t = t;
                }
                Data(Version v, Timestamp t, Version dep, Timestamp depTime) {
                    this.dep = dep;
                    this.depTime = depTime;
                    this(v, t);
                }
                public int compareTo(Data other) {
                    return v.compareTo(other);
                }
            }
            
            // Go through linked list of project versions. Check for dependencies to project b
            // Record version a and its timestamp. Record version b and its timestamp.
            List<Data> firstTimeline = new ArrayList<>();
            while (aVersion != null) {
                Data data = null;
                for (Dependency dep: aVersion.getDependencies()) {
                    if (dep.getDep() == b.getName()) {
                        data = new Data(aVersion.getVersion(), aVersion.getTime(), dep.getVersion(), dep.getTimestamp());
                    }
                }
                firstTimeline.add(data != null ? data : new Data(aVersion.getVersion(), aVersion.getTime()));
                aVersion = aVersion.getNext();
            }       
            
            /*
            What do I need here?
            1. How often are the versions updated
            2. How often is the dependency updated
            3. How much lag is there between an updated version B and its dependency being updated in version A
            4. Is the dependency updating to the newest version of B
            5. How often do versions in A update so they can update B (rephrase)
            */
            
        });
    }
    
    public HashMap<String, Project> getProjects() {
        return projects;
    }

    public void setProjects(HashMap<String, Project> projects) {
        this.projects = projects;
    }
}
