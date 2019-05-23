package masters.dataClasses;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.apache.log4j.Logger;
import utils.Database;

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

            Dependency dep = new Dependency(rs.getString("DependencyName"), rs.getString("DependencyRequirements"));
            getProjects().get(name).addProjectInfo(version, dep);
        }
    }

    public void compareProjects() {
        this.getProjects().forEach((k, v) -> v.compareVersions());
    }

    public void printProjectResults() {
        LOG.info("There were " + getProjects().size() + " projects overall");
        File dir = new File("data/version-comps");
        if (!dir.exists())
            dir.mkdirs();

        this.getProjects().forEach((k, v) -> {
            try {
                File f = new File("data/version-comps/" + k.replace(":", ",") + ".txt");
                v.printChangesToFile(f);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /*
    Dependencies may or may not be projects in the same dataset.
    This function checks for which projects A and B where A depends on B, both A and B are in the dataset.
    When it finds a pair, it adds the pair to project Pairs
     */
    public void checkDependenciesAreProjects() {
        this.getProjects().forEach((projName, project) -> {
            project.getVersions().forEach((versionName, version) -> {
                version.getDependencies().forEach(dep -> {
                    Project p = this.getProjects().get(dep.getProjectName());
                    if (p == null) {
                        dependencyFound(false);
                        return;
                    }
                    ProjectVersionInfo vers = p.getVersions().get(dep.getVersion());
                    if (vers == null) {
                        dependencyFound(false);
                        return;
                    }
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

    // Requires checkDependenciesAreProjects to have run previously
    // Runs ProjectTimelinePair class concurrently
    public void constructTimeline() throws SQLException {
        File dir = new File("data/timelines2");
        if (!dir.exists()) dir.mkdirs();
//        else {
//            try {
//                FileUtils.cleanDirectory(dir);
//                LOG.info("Old timeline data cleared");
//            } catch (IOException e) {
//                LOG.error(e);
//            }
//        }

        // Run concurrently to speed up
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
        ArrayBlockingQueue<Connection> connections = new ArrayBlockingQueue<>(10);
        for (int i = 0; i < 10; i++)
            connections.add(Database.getConnection());

        // Where project a depends on project b
        int i = 0;
        for (String pair: projectPairs) {
            String[] projs = pair.split("::");
            Project a = projects.get(projs[0]);
            Project b = projects.get(projs[1]);
            executor.execute(new ProjectTimelinePair(pair, a, b, connections, i));
            i++;
        }

        // Wait for threads to finish working
        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.MINUTES);
        } catch(InterruptedException e) {
            executor.shutdownNow();
        }

        // Close DB connections
        while(connections.size() > 0)
            connections.poll().close();

        LOG.info("There were " + TimelineStats.SEMVER_PAIRS.toString() + " pairs where both use semantic versioning");
        LOG.info("There were " + TimelineStats.NOT_SEMVER_PAIRS.toString() + " pairs that were discarded as they don't use semantic versioning");
        LOG.info("There were " + TimelineStats.LARGE_ENOUGH.toString() + " pairs with sufficient history to write to file");
        LOG.info("There were " + TimelineStats.NOT_LARGE_ENOUGH.toString() + " pairs discarded due to small size of history");
    }

    public HashMap<String, Project> getProjects() {
        return projects;
    }

    public void setProjects(HashMap<String, Project> projects) {
        this.projects = projects;
    }
}


// class Data implements Comparable<Data> {
// Version v, dep;
// Timestamp t, depTime;

// Data(Version v, Timestamp t) {
// this.v = v;
// this.t = t;
// }

// Data(Version v, Timestamp t, Version dep, Timestamp depTime) {
// this(v, t);
// this.dep = dep;
// this.depTime = depTime;
// }

// public int compareTo(Data other) {
// return v.compareTo(other.v);
// }
// }


//            // Go through linked list of project versions. Check for dependencies to project b
//            // Record version a and its timestamp. Record version b and its timestamp.
//            List<Data> firstTimeline = new ArrayList<>();
//            while (aVersion != null && bVersion != null) {
//                Data data = null;
//                for (Dependency dep : aVersion.getDependencies()) {
//                    if (dep.getProjectName() == b.getName()) {
//                        data = new Data(aVersion.getVersion(), aVersion.getTime(), dep.getVersion(), dep.getTimestamp());
//                    }
//                }
//                firstTimeline.add(data != null ? data : new Data(aVersion.getVersion(), aVersion.getTime()));
//                aVersion = aVersion.getNext();
//            }


//    public void getTimestamps(Connection c) throws SQLException {
//        this.getProjects().forEach((projectName, project) -> {
//            project.getVersions().forEach((versionString, versionObject) -> {
//                // Get timestamps for each version that doesn't yet have them
//                if (versionObject.getTime() == null) {
//                    versionObject.setTimestamp(timestampFromDB(c, projectName, versionString));
//                    // Set timestamps of the dependencies as well
//                    versionObject.getDependencies().forEach(dep -> {
//                        dep.setTimestamp(timestampFromDB(c, dep.getProjectName(), dep.getVersion()));
//                    });
//                }
//            });
//        });
//    }
