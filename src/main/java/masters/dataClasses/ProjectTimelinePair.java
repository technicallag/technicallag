package masters.dataClasses;

import org.apache.log4j.Logger;
import utils.Logging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

class TimelineStats {
    static AtomicInteger SEMVER_PAIRS = new AtomicInteger(0);
    static AtomicInteger NOT_SEMVER_PAIRS = new AtomicInteger(0);
    static AtomicInteger LARGE_ENOUGH = new AtomicInteger(0);
    static AtomicInteger NOT_LARGE_ENOUGH = new AtomicInteger(0);
}

/*
Takes a given Project A and Project B with dependencies from A to B and describes them.

Can be run in parallel, requires DB connection as timestamps are gathered lazily
 */
public class ProjectTimelinePair implements Runnable {
    /*
    Fields
     */
    String pair;
    Project a, b;
    int pos;

    List<ProjectVersionInfo> orderedVersionsA;
    List<ProjectVersionInfo> orderedVersionsB;

    // Stores two pointers to which versions of B are used and the latest at the time when a version of A is published
    List<LinkAB> linksAtoB;

    // Creates and stores a tree of the major, minor and micro changes within a project
//    ProjectMilestones milestonesA;
    ProjectMilestones milestonesB;

    Connection c;
    ArrayBlockingQueue<Connection> connections;
    Logger LOG = Logging.getLogger("");

    String[] headers = new String[]{
            "Proj_A_version",
            "Proj_A_version_date",
            "Proj_A_dep_declaration_to_Proj_B",
            "Proj_B_dep_publish_date",
            "Proj_B_latest_current_version",
            "Proj_B_latest_version_pub_date",
            "Major_vers_behind",
            "Minor_vers_behind",
            "Micro_vers_behind"
    };

    /*
    Printing Logic
     */
    private void printTimelines(){
        try (BufferedWriter out = new BufferedWriter(new FileWriter(new File("data/timelines2/" + pair.replace(":", "$") + ".csv")))) {
            writeHeader(out);

            for (int i = 0; i < orderedVersionsA.size(); i++) {
                ProjectVersionInfo thisA = orderedVersionsA.get(i);
                LinkAB link = linksAtoB.get(i);

                // It is possible that for a given version of A, B does not exist yet
                ProjectVersionInfo thisDep = link.currentDependency == -1 ? null : orderedVersionsB.get(link.currentDependency);
                ProjectVersionInfo latestDep = link.latestDependency == -1 ? null : orderedVersionsB.get(link.latestDependency);
                VersionsBetween versionsBetween = milestonesB.getDifference(link.currentDependency, link.latestDependency);

                writeLine(out,
                        thisA.getVersion().toString(),
                        thisA.getTimeStringNullable(),
                        thisDep == null ? "" : thisDep.getVersionString(),
                        thisDep == null ? "" : thisDep.getTimeStringNullable(),
                        latestDep == null ? "" : latestDep.getVersionString(),
                        latestDep == null ? "" : latestDep.getTimeStringNullable(),
                        versionsBetween.toString()
                        );
            }

            writeLine(out, "\n\n");
            writeLine(out, "ProjBVersion", "ProjBDate");
            for (ProjectVersionInfo b: orderedVersionsB) {
                writeLine(out, b.getVersionString(), b.getTimeStringNullable());
            }
        } catch (IOException e) {
            System.err.println(new File("data/timelines/" + pair.replace(":", "$") + ".csv").getAbsolutePath());
            e.printStackTrace();
        }
    }

    private void writeLine(BufferedWriter out, String... args) throws IOException {
        out.write(String.join(",", args));
        out.write("\n");
    }

    private void writeHeader(BufferedWriter out) throws IOException {
        out.write(String.join(",", headers));
        out.write("\n");
    }

    /*
    Data classes
     */
    // Links Version X of Project A to Version Y of Project B (and tracks the newest version Z of Project B) in orderedVersionsB
    class LinkAB {
        int currentDependency = -1;
        int latestDependency = -1;
    }

    class VersionsBetween {
        List<Integer> types = new ArrayList<>(3); // Major, minor, micro

        VersionsBetween() {
            for (int i = 0; i < 3; i++)
                types.add(0);
        }

        @Override
        public String toString() {
            return String.join(",", types.get(0).toString(), types.get(1).toString(), types.get(2).toString());
        }
    }

    class ProjectMilestones {
//        List<VersionRelationship> changeTypes = new ArrayList<>();
        Node versionTree = new Node(0, 0);

        /*
        Tree splits by major, then minor then micro.
        Level 0 is the root, Level 1 is major etc. Level 3 is max depth (micro).
        Allows us to tally up major, then minor, then micro differences
         */
        class Node {
            int left, right, level;
            List<Node> children = new ArrayList<>();

            Node(int left, int level) {
                this.left = left;
                this.right = left;
                this.level = level;
                if (level < 3) {
                    children.add(new Node(left, level+1));
                }
                if (pos % 1000 == 1 && level == 0) {
                    LOG.trace(pair);
                }

            }

            void addNext(int cur, int levelChange) {
                if (pos % 1000 == 1) {
                    LOG.trace("addNext. Cur: " + cur + "\tLevelChange: " + levelChange);
                }
                this.right = cur;

                if (level == 3)
                    return;

                if (levelChange == level) {
                    children.add(new Node(cur, level+1));
                }
                children.get(children.size() - 1).addNext(cur, levelChange);
            }

            boolean contains(int i) {
                return i >= left && i <= right;
            }

            VersionsBetween getDifference(int cur, int latest) {
                // If cur == -1, then there is no dependency here yet so this is meaningless. Return a dummy value.
                // Level == 3 is the base case
                if (cur == -1 || level == 3) return new VersionsBetween();

                if (pos % 1000 == 1) {
                    LOG.trace("getDifference1. Cur: " + cur + "\tLatest: " + latest + "\tLevel: " + level);
                    LOG.trace("getDifference2. Children size: " + children.size());
                }

                int first = 0, second = 0;
                for (int i = 0; i < children.size(); i++) {
                    if (pos % 1000 == 1) {
                        LOG.trace("getDifference2a. Child i: " + i + "\tLeft: " + children.get(i).left + "\tRight: " + children.get(i).right);
                    }
                    if (children.get(i).contains(cur)) first = i;
                    if (children.get(i).contains(latest)) second = i;
                }

                VersionsBetween between = children.get(second).getDifference(cur, latest);
                between.types.set(level, second-first);

                if (pos % 1000 == 1) {
                    LOG.trace("getDifference3. First: " + first + "\tSecond: " + second + "\tLevel: " + level);
                }

                return between;
            }
        }

        ProjectMilestones (List<ProjectVersionInfo> versions) {
            versionTree.addNext(0, 3); // Dummy value so there are n entries

            for (int i = 1; i < versions.size(); i++) {
                Version version1 = versions.get(i - 1).getVersion();
                Version version2 = versions.get(i).getVersion();

                if (pos % 1000 == 1) {
                    LOG.trace("Versions: " + version1.toString() + "\t" + version2.toString());
                }

                VersionRelationship versionRelationship = version1.getRelationship(version2);
                switch(versionRelationship) {
                    case DIFFERENT: versionTree.addNext(i, 0); break;
                    case SAME_MAJOR: versionTree.addNext(i, 1); break;
                    case SAME_MINOR: versionTree.addNext(i, 2); break;
                    case SAME_MICRO: versionTree.addNext(i, 3); break;
                }

//                changeTypes.add(version1.getRelationship(version2));
            }
        }

        VersionsBetween getDifference(int cur, int latest) {
            return versionTree.getDifference(cur, latest);
        }
    }

    /*
    Data Gathering Logic
     */
    public ProjectTimelinePair(String pair, Project a, Project b, ArrayBlockingQueue<Connection> connections, int pos) {
        this.pair = pair;
        this.connections = connections;
        this.a = a;
        this.b = b;
        this.pos = pos;
    }

    private boolean isSemVer(Project a) {
        ProjectVersionInfo cur = a.getFirstVersion();
        while (true) {
            if (cur == null) return true;
            if (cur.getVersion().versionTokens.size() == 0) return false;
            cur = cur.getNext();
        }
    }

    @Override
    public void run() {
        // Some projects are null, data errors
        if (a == null || b == null) {
            LOG.warn((a == null ? pair.split("::")[0] : pair.split("::")[1]) + " is null");
            return;
        }

        // We can't say much about projects that don't follow semantic versioning principles with version strings
        if (isSemVer(a) && isSemVer((b))) {
            TimelineStats.SEMVER_PAIRS.getAndIncrement();
        } else {
            TimelineStats.NOT_SEMVER_PAIRS.getAndIncrement();
            return;
        }

        // Focus on projects with a lot of version history to start with
        if (a.getVersions().size() < 50 || b.getVersions().size() < 50) {
            TimelineStats.NOT_LARGE_ENOUGH.getAndIncrement();
            return;
        } else {
            TimelineStats.LARGE_ENOUGH.getAndIncrement();
        }

        // Go ahead and get data for this pair
        init();
        printTimelines();
    }

    private void init() {
        // Requires DB connection to get timestamps off the DB
        try {
            c = connections.take();

            // Order versions using timestamps
            a.getVersions().values().forEach(e -> e.getTimestamp(c, a.getName()));
            b.getVersions().values().forEach(e -> e.getTimestamp(c, b.getName()));

            connections.add(c);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Use timestamps to order versions
        orderedVersionsA = a.getVersions().values().stream()
                .sorted((e1, e2) -> e1.getTimestampNullable().compareTo(e2.getTimestampNullable()))
                .collect(Collectors.toList());
        orderedVersionsB = b.getVersions().values().stream()
                .sorted((e1, e2) -> e1.getTimestampNullable().compareTo(e2.getTimestampNullable()))
                .collect(Collectors.toList());

        // Link dependencies in project A to project B
        linksAtoB = new ArrayList<>();
        for (ProjectVersionInfo version: orderedVersionsA) {
            LinkAB link = new LinkAB();

            // Filter dependencies to only trigger on project B
            for (Dependency dep: version.getDependencies()) {
                if (dep.getProjectName().equals(b.getName())){

                    // Find the linked version, and the newest version
                    String declaredDependency = dep.getVersion();

                    int j = 0;
                    for (ProjectVersionInfo bVersion: orderedVersionsB) {
                        if (declaredDependency.equals(bVersion.getVersionString())){
                            link.currentDependency = j;
                        }
                        if (version.getTimestampNullable().before(bVersion.getTimestampNullable())) {
                            link.latestDependency = j - 1;
                            break;
                        }
                        j++;
                    }

                    // If project A has more recent published history than project B, this will fire
                    if (version.getTimestampNullable().after(orderedVersionsB.get(orderedVersionsB.size()-1).getTimestampNullable())) {
                        link.latestDependency = orderedVersionsB.size()-1;
                    }

                    break;
                }
            }

            linksAtoB.add(link);
        }

        // Describe the changes between versions in timeline A and timeline B
//        milestonesA = new ProjectMilestones(orderedVersionsA);
        milestonesB = new ProjectMilestones(orderedVersionsB);
    }
}
