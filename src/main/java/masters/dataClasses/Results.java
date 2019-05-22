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

//    // Implements the logic for getting timelines
//    class TimelineCreator implements Runnable {
//        boolean trackDates;
//        String pair;
//        Project a, b;
//        ArrayBlockingQueue<Connection> connections;
//        Connection c;
//
//        String[] headers = new String[]{
//                "Proj_A_version",
//                "Proj_A_version_date",
//                "Proj_A_dep_declaration_to_Proj_B",
//                "Proj_B_dep_publish_date",
//                "Lag_proj_a-proj_b_time",
//                "Proj_B_latest_current_version",
//                "Num_vers_dep_behind_cur",
//                "Dep_same_major_vers_as_current",
//                "Dep_same_minor_vers_as_current",
//                "Proj_B_version",
//                "Proj_B_version_date",
//        };
//
//        class Data implements Comparable<Data> {
//            ProjectVersionInfo info;
//            String version;
//            Timestamp time;
//
//            String depVersion;
//            Timestamp depTimestamp = new Timestamp(System.currentTimeMillis());
//
//            // Dependency information
//            ProjectVersionInfo currentDep, latestPossibleDep;
//            int currentDepIndex, latestPossibleIndex; // How many versions between declared and current versions
//            VersionRelationship versionRelationship;
//
//            Data(ProjectVersionInfo info, String version, Timestamp time) {
//                this.info = info;
//                this.version = version;
//                this.time = time;
//            }
//
//            String timeString(Timestamp t){
//                return t.toString().substring(0,10);
//            }
//
//            String getDependencyLag() {
//                return Long.toString((time.getTime() - depTimestamp.getTime()) / 1000 / 60 / 60 / 24);
//            }
//
//            @Override
//            public String toString() {
//                String basics = String.join(",", new String[] {
//                        version,
//                        timeString(time)
//                });
//
//                String deps = String.join(",", new String[] {
//                        depVersion,
//                        timeString(depTimestamp),
//
//                });
//
////                if (depVersion == null) {
////                    return version + ',' + timeString(time);
////                } else {
////                    return  version + ',' +
////                            timeString(time) + ',' +
////                            depVersion + ',' +
////                            timeString(depTimestamp) + ',';
////                }
//
//            }
//
//            @Override
//            public int compareTo(Data o) {
//                return this.time.compareTo(o.time);
//            }
//        }
//
//        TimelineCreator(String pair, ArrayBlockingQueue<Connection> connections, boolean trackDates) {
//            this.pair = pair;
//            this.connections = connections;
//            this.trackDates = trackDates;
//        }
//        /* What do I need here?
//        1. How often are the versions updated
//        2. How often is the dependency updated
//        3. How much lag is there between an updated version B and its dependency being updated in version A
//        4. Is the dependency updating to the newest version of B
//        5. How often do versions in A update so they can update B (rephrase)
//
//        Data validation:
//        A. Are all the versions in this timeline in libraries.io (or how much is included)
//        B. Are the timestamps in here
//        */
//
//        private boolean isSemVer(Project a) {
//            ProjectVersionInfo cur = a.getFirstVersion();
//            while (true) {
//                if (cur == null) return true;
//                if (cur.getVersion().versionTokens.size() == 0) return false;
//                cur = cur.getNext();
//            }
//        }
//
//        private ProjectVersionInfo latestVersion(Timestamp t, ProjectVersionInfo first) {
//            ProjectVersionInfo next = first;
//            try {
//                while(next.getTimestampNullable().before(t)) {
//                    first = next;
//                    next = next.getNext();
//                }
//            } catch (NullPointerException e) {} // There aren't more versions after this
//            return first;
//        }
//
//        private int versionsBetween(ProjectVersionInfo a, ProjectVersionInfo b) {
//            if (a.getTimestampNullable().after(b.getTimestampNullable())) return versionsBetween(b, a);
//
//            int count = 0;
//            while (a != b) {
//                count++;
//                a = a.getNext();
//            }
//            return count;
//        }
//
//        private ProjectVersionInfo getCurrentDep(String dep, ProjectVersionInfo project) {
//            while (!(project.getVersionString().equals(dep))) {
//                project = project.getNext();
//            }
//            return project;
//        }
//
//        private void writeLine(BufferedWriter out, String... args) throws IOException {
//            out.write(String.join(",", args));
//            out.write("\n");
//        }
//
//        private void writeHeader(BufferedWriter out) throws IOException {
//            out.write(String.join(",", headers));
//            out.write("\n");
//        }
//
//        private void trackDatesForSummary(ProjectVersionInfo vers, Project proj) {
//            String atime = vers.getTimestamp(c, proj.getName());
//            DATES.computeIfAbsent(atime, k -> new LongAdder()).increment();
//        }
//
//        private List<Data> getProjectData(ProjectVersionInfo info, String otherProj){
//            List<Data> projA = new ArrayList<>();
//
//            while(info != null) {
//                Data data = new Data(info, info.getVersionString(), info.getTimestampNullable(c, a.getName()));
//                projA.add(data);
//
//                if (otherProj != null) { // Is run only for project A
//                    for (Dependency dep : info.getDependencies()) {
//                        if (dep.getProjectName().equals(otherProj)) {
//                            data.depVersion = dep.getVersion();
//                            data.depTimestamp = dep.getTimestampNullable(c);
//                            break;
//                        }
//                    }
//                }
//
//                info = info.getNext();
//            }
//
//            return projA;
//        }
//
//        class ProjectPair {
//
//        }
//
//        private void writeToFile(Project a, Project b) throws InterruptedException {
//            ProjectVersionInfo aVersion = a.getFirstVersion();
//            ProjectVersionInfo bVersion = b.getFirstVersion();
//
//            ProjectVersionInfo currentDep;
//
//            c = connections.take(); // Only take DB connections when they are needed
//
//            // Write results to file
//            try (BufferedWriter out = new BufferedWriter(new FileWriter(new File("data/timelines2/" + pair.replace(":", "$") + ".csv")))) {
//                // Get Project A's data
//                List<Data> projA = getProjectData(aVersion, b.getName());
//                List<Data> projB = getProjectData(bVersion, null);
//
//                // The project versions are originally sorted on version names. This is quick (as timestamps aren't required) but not as accurate as when sorting by timestamps
//                Collections.sort(projA);
//                Collections.sort(projB);
//
//                // Match project A dependencies with project B's timeline
//                boolean dependedYet = false;
//                for (int i = 0; i < projA.size(); i++) {
//                    Data curVersionA = projA.get(i);
//                    if (curVersionA.depVersion == null) {
//                        continue;
//                    } else if (!dependedYet) { // First dependency between projects
//                        dependedYet = true;
//
//                    }
//
//                    int currentDepIndex = 0;
//                    int latestDepIndex = 0;
//                    for (int j = 0; j < projB.size(); j++) {
//                        Data curVersionB = projB.get(j);
//                        if (curVersionA.depVersion.equals(curVersionB.version)) {
//                            currentDepIndex = j;
//                        }
//                        if (curVersionA.depTimestamp.before(curVersionB.time)) {
//                            latestDepIndex = j-1;
//                            break;
//                        }
//                    }
//
//                    curVersionA.currentDep = projB.get(currentDepIndex).info;
//                    curVersionA.currentDepIndex = currentDepIndex;
//                    curVersionA.latestPossibleDep = projB.get(latestDepIndex).info;
//                    curVersionA.latestPossibleIndex = latestDepIndex;
//                    curVersionA.versionRelationship = curVersionA.currentDep.getVersion().getRelationship(curVersionA.latestPossibleDep.getVersion());
//                }
//
//                // Print
//                writeHeader(out);
//
//                int newestBversion = 0;
//                int ind = 0;
//                for (; ind < projA.size() && ind < projB.size(); ind++) {
//                    Data first = projA.get(ind);
//                    Data second = projB.get(ind);
//
//                    if (first.depVersion == null) { // There's no connection between the projects at this version
//                        String[] strings = new String[headers.length - 2]; // Print A and B versions/timestamps
//                        Arrays.fill(strings, "");
//                        strings[0] = first.toString();
//                        strings[strings.length - 1] = second.toString();
//                        writeLine(out, strings);
//                    } else {
//
//                    }
//                }
//
//                // If there's more project A versions, print the remaining
//                for (; ind < projA.size(); ind++) {
//
//                }
//
//                // If there's more project B versions, print the remaining
//                for (; ind < projB.size(); ind++) {
//                    String[] strings = new String[headers.length - 1]; // All but the last two columns filled with blanks
//                    Arrays.fill(strings, "");
//
//                    strings[strings.length - 1] = projB.get(ind).toString();
//                    writeLine(out, strings);
//                }
//
//
//                while(aVersion != null) {
//                    String[] info = new String[]{"","","","",""};
//                    info[0] = aVersion.getVersionString();
//                    info[1] = aVersion.getTimestamp(c, a.getName());
//
//                    for (Dependency dep : aVersion.getDependencies()) {
//                        if (dep.getProjectName().equals(b.getName())) {
//                            info[2] = dep.getVersion();
//                            info[3] = dep.getTimestamp(c);
//                            depTimes.add(dep.getTimestampNullable(c));
//                            break;
//                        }
//                    }
//
//                    currentDep = getCurrentDep(depVersion, b.getFirstVersion());
//
//                    // Col E - Difference in time between B and D (Project A version release - Project B dependency release)
//                    // i.e. how long has the version been out
//                    // Also, how long is the lag between adopting new versions
//                    Timestamp aTimestamp = aVersion.getTimestampNullable();
//                    strings.add(Long.toString(aTimestamp.getTime() - depTimestamp.getTime()));
//
//                    // Col F - What is the newest available release (stable release) of Project B for this version of Project A
//                    ProjectVersionInfo bLatestVersion = latestVersion(aTimestamp, b.getFirstVersion());
//                    if (bLatestVersion != null) {
//                        strings.add(bLatestVersion.getVersionString());
//
//                        // Col G - How many versions are there between this dependency and the newest one (major or minor)
//                        strings.add(Integer.toString(versionsBetween(currentDep, bLatestVersion)));
//                    } else {
//                        strings.add("");
//                        strings.add("");
//                    }
//
//                    // Col H - Is this the same major version as the newest?
//                    strings.add(Boolean.toString(bLatestVersion.getVersion().sameMajor(currentDep.getVersion())));
//
//                    // Col I - Is this the same minor version as the newest?
//                    strings.add(Boolean.toString(bLatestVersion.getVersion().sameMinor(currentDep.getVersion())));
//
//
//
//                    aVersion = aVersion.getNext();
//                }
//
//
//
//                while (aVersion != null || bVersion != null) {
//                    // Write in
//                    // Cols A and B (Project A's version and release time
//                    List<String> strings = new ArrayList<>();
//                    if (aVersion != null) {
//                        String atime = aVersion.getTimestamp(c, a.getName());
//                        DATES.computeIfAbsent(atime, k -> new LongAdder()).increment();
//                    }
//
//                    strings.add(aVersion == null ? "" : aVersion.getVersionString());
//                    strings.add(aVersion == null ? "" : aVersion.getTimestamp(c, a.getName()));
//
//                    // Cols C and D (Project A's dependency on Project B, its timestamp)
//                    String depVersion = null;
//                    String depTime = null;
//                    Timestamp depTimestamp = null;
//                    if (aVersion != null) {
//                        for (Dependency dep : aVersion.getDependencies()) {
//                            if (dep.getProjectName().equals(b.getName())) {
//                                depVersion = dep.getVersion();
//                                depTime = dep.getTimestamp(c);
//                                depTimestamp = dep.getTimestampNullable(c);
//                                break;
//                            }
//                        }
//                    }
//                    strings.add(depVersion == null ? "" : depVersion);
//                    strings.add(depTime == null ? "" : depTime);
//
//                    if (depVersion == null || depTime == null || depTimestamp == null || aVersion == null) {
//                        strings.add("");
//                        strings.add("");
//                        strings.add("");
//                        strings.add("");
//                        strings.add("");
//                    } else {
//                        currentDep = getCurrentDep(depVersion, b.getFirstVersion());
//
//                        // Col E - Difference in time between B and D (Project A version release - Project B dependency release)
//                        // i.e. how long has the version been out
//                        // Also, how long is the lag between adopting new versions
//                        Timestamp aTimestamp = aVersion.getTimestampNullable();
//                        strings.add(Long.toString(aTimestamp.getTime() - depTimestamp.getTime()));
//
//                        // Col F - What is the newest available release (stable release) of Project B for this version of Project A
//                        ProjectVersionInfo bLatestVersion = latestVersion(aTimestamp, b.getFirstVersion());
//                        if (bLatestVersion != null) {
//                            strings.add(bLatestVersion.getVersionString());
//
//                            // Col G - How many versions are there between this dependency and the newest one (major or minor)
//                            strings.add(Integer.toString(versionsBetween(currentDep, bLatestVersion)));
//                        } else {
//                            strings.add("");
//                            strings.add("");
//                        }
//
//                        // Col H - Is this the same major version as the newest?
//                        strings.add(Boolean.toString(bLatestVersion.getVersion().sameMajor(currentDep.getVersion())));
//
//                        // Col I - Is this the same minor version as the newest?
//                        strings.add(Boolean.toString(bLatestVersion.getVersion().sameMinor(currentDep.getVersion())));
//
//                        // Does it update
//                    }
//
//                    //
//                    if (bVersion != null) {
//                        String btime = bVersion.getTimestamp(c, b.getName());
//                        DATES.computeIfAbsent(btime, k -> new LongAdder()).increment();
//                    }
//                    strings.add(bVersion == null ? "" : bVersion.getVersionString());
//                    strings.add(bVersion == null ? "" : bVersion.getTimestamp(c, b.getName()));
//                    strings.add("\n");
//
//                    out.write(String.join(",", strings));
//
//                    // Iterate
//                    aVersion = aVersion == null ? null : aVersion.getNext();
//                    bVersion = bVersion == null ? null : bVersion.getNext();
//                }
//            } catch (IOException e) {
//                System.err.println(new File("data/timelines/" + pair.replace(":", "$") + ".csv").getAbsolutePath());
//                e.printStackTrace();
//            } finally {
//                // Free up the DB connection
//                connections.add(c);
//            }
//        }
//
//        public void run() {
//            try {
//                a = projects.get(pair.split("::")[0]);
//                b = projects.get(pair.split("::")[1]);
//
//                if (a == null || b == null) {
//                    LOG.warn((a == null ? pair.split("::")[0] : pair.split("::")[1]) + " is null");
//                    return;
//                }
//
//                if (isSemVer(a) && isSemVer((b))) {
//                    SEMVER_PAIRS.getAndIncrement();
//                } else {
//                    NOT_SEMVER_PAIRS.getAndIncrement();
//                    return;
//                }
//
//                // Only check versions with decent version histories to focus on projects that may be interesting
//                if (a.getVersions().size() > MIN_PROJECT_SIZE && b.getVersions().size() > MIN_PROJECT_SIZE) {
//                    writeToFile(a, b);
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }

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
        this.projectPairs.forEach(pair -> executor.execute(new TimelineCreator(pair, connections, false)));

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

        LOG.info("There were " + SEMVER_PAIRS + " pairs where both use semantic versioning");
        LOG.info("There were " + NOT_SEMVER_PAIRS + " pairs that were discarded as they don't use semantic versioning");
        LOG.info("Dates size: " + DATES.size());
        DATES.keySet().stream().sorted().forEach(k -> LOG.trace(String.format("Date: %s \t Number: %d", k, DATES.get(k).longValue())));
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
