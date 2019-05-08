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
import org.apache.commons.io.FileUtils;
import utils.Database;

// Container class that gathers information from SQL and controls access to the various data classes
public class Results {
    private HashMap<String, Project> projects = new HashMap<>();
    private HashSet<String> projectPairs = new HashSet<>();
    private int FOUND = 0;
    private int NOTFOUND = 0;
    private int MIN_PROJECT_SIZE = 60;
    private Logger LOG;

    // Variables for parallel timeline building
    private static AtomicInteger SEMVER_PAIRS = new AtomicInteger(0);
    private static AtomicInteger NOT_SEMVER_PAIRS = new AtomicInteger(0);
    private static ConcurrentHashMap<String, LongAdder> DATES = new ConcurrentHashMap<>();

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
                    Project p = this.getProjects().get(dep.getDep());
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

    // Implements the logic for getting timelines
    class TimelineCreator implements Runnable {
        String pair;
        ArrayBlockingQueue<Connection> connections;

        TimelineCreator(String pair, ArrayBlockingQueue<Connection> connections) {
            this.pair = pair;
            this.connections = connections;
        }
        /* What do I need here?
        1. How often are the versions updated
        2. How often is the dependency updated
        3. How much lag is there between an updated version B and its dependency being updated in version A
        4. Is the dependency updating to the newest version of B
        5. How often do versions in A update so they can update B (rephrase)

        Data validation:
        A. Are all the versions in this timeline in libraries.io (or how much is included)
        B. Are the timestamps in here
        */

        private boolean isSemVer(ProjectVersionInfo cur) {
            while (true) {
                if (cur == null) return true;
                if (cur.getVersion().versionTokens.size() == 0) return false;
                cur = cur.getNext();
            }
        }

        public void run() {
            try {
                Project a = projects.get(pair.split("::")[0]);
                Project b = projects.get(pair.split("::")[1]);
                
                if (a == null || b == null) {
                    LOG.warn((a == null ? pair.split("::")[0] : pair.split("::")[1]) + " is null");
                    return;
                }

                ProjectVersionInfo aVersion = a.getFirstVersion();
                ProjectVersionInfo bVersion = b.getFirstVersion();

                if (isSemVer(aVersion) && isSemVer((bVersion))) {
                    SEMVER_PAIRS.getAndIncrement();
                } else {
                    NOT_SEMVER_PAIRS.getAndIncrement();
                    return;
                }

                // Only check versions with decent version histories to focus on projects that may be interesting
                if (a.getVersions().size() > MIN_PROJECT_SIZE && b.getVersions().size() > MIN_PROJECT_SIZE) {
                    Connection c = connections.take();

                    // Write results to file
                    try (BufferedWriter out = new BufferedWriter(new FileWriter(new File("data/timelines2/" + pair.replace(":", "$") + ".csv")))) {
                        while (aVersion != null || bVersion != null) {
                            // Write in
                            List<String> strings = new ArrayList<>();
                            if (aVersion != null) {
                                String atime = aVersion.getTimestamp(c, a.getName());
                                DATES.computeIfAbsent(atime, k -> new LongAdder()).increment();
                                if (atime.equals("2015-02-07")) LOG.trace(atime + " " + DATES.get(atime).toString());
                            }

                            strings.add(aVersion == null ? "" : aVersion.getVersionString());
                            strings.add(aVersion == null ? "" : aVersion.getTimestamp(c, a.getName()));

                            String depVersion = null;
                            String depTime = null;
                            if (aVersion != null) {
                                for (Dependency dep : aVersion.getDependencies()) {
                                    if (dep.getDep().equals(b.getName())) {
                                        depVersion = dep.getVersion();
                                        depTime = dep.getTimestamp(c);
                                        break;
                                    }
                                }
                            }
                            strings.add(depVersion == null ? "" : depVersion);
                            strings.add(depTime == null ? "" : depTime);

                            if (bVersion != null) {
                                String btime = bVersion.getTimestamp(c, b.getName());
                                DATES.computeIfAbsent(btime, k -> new LongAdder()).increment();
                            }
                            strings.add(bVersion == null ? "" : bVersion.getVersionString());
                            strings.add(bVersion == null ? "" : bVersion.getTimestamp(c, b.getName()));
                            strings.add("\n");

                            out.write(String.join(",", strings));

                            // Iterate
                            aVersion = aVersion == null ? null : aVersion.getNext();
                            bVersion = bVersion == null ? null : bVersion.getNext();
                        }
                    } catch (IOException e) {
                        System.err.println(new File("data/timelines/" + pair.replace(":", "$") + ".csv").getAbsolutePath());
                        e.printStackTrace();
                    } finally {
                        connections.add(c);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Requires checkDependenciesAreProjects to have run previously
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
        this.projectPairs.forEach(pair -> executor.execute(new TimelineCreator(pair, connections)));
        executor.shutdown();

        // Close DB connections
        while(connections.size() > 0)
            connections.poll().close();

        LOG.info("There were " + SEMVER_PAIRS + " pairs where both use semantic versioning");
        LOG.info("There were " + NOT_SEMVER_PAIRS + " pairs that were discarded as they don't use semantic versioning");

        // Print out the dates
        while(DATES.elements().hasMoreElements()) {
            LOG.trace(DATES.elements().nextElement());
        }
        LOG.info("Dates size: " + DATES.size());
        DATES.forEach((k,v) -> LOG.trace(String.format("Date: %s \t Number: %d", k, v.longValue())));
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
//                    if (dep.getDep() == b.getName()) {
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
//                        dep.setTimestamp(timestampFromDB(c, dep.getDep(), dep.getVersion()));
//                    });
//                }
//            });
//        });
//    }
