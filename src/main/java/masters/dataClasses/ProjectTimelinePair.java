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

    List<Integer> linksAtoB;

    // Creates and stores a tree of the major, minor and micro changes within a project
    ProcessPair processPair;

    Connection c;
    ArrayBlockingQueue<Connection> connections;
    Logger LOG = Logging.getLogger("");

    String[] headers = new String[]{
            "Proj_A_version",
            "Proj_A_version_date",
            "Proj_A_dep_declaration_to_Proj_B",
            "Proj_B_dep_publish_date",
            "Major_vers_behind",
            "Newest_major",
            "Minor_vers_behind",
            "Newest_minor",
            "Micro_vers_behind",
            "Newest_micro",
            "NOTAG_Major_vers_behind",
            "NOTAG_Newest_major",
            "NOTAG_Minor_vers_behind",
            "NOTAG_Newest_minor",
            "NOTAG_Micro_vers_behind",
            "NOTAG_Newest_micro"
    };

    /*
    Printing Logic
     */
    private void printTimelines(){
        try (BufferedWriter out = new BufferedWriter(new FileWriter(new File("data/timelines2/" + pair.replace(":", "$") + ".csv")))) {
            writeHeader(out);

            for (int i = 0; i < orderedVersionsA.size(); i++) {
                ProjectVersionInfo thisA = orderedVersionsA.get(i);

                // It is possible that for a given version of A, B does not exist yet
                ProjectVersionInfo thisDep = linksAtoB.get(i) == -1 ? null : orderedVersionsB.get(linksAtoB.get(i));
                VersionsBehind versionsBehindTags = processPair.howFarBehindIsA[i];
                VersionsBehind versionsBehindNoTags = processPair.howFarBehindIsANoTags[i];

                writeLine(out,
                        thisA.getVersion().toString(),
                        thisA.getTimeStringNullable(),
                        thisDep == null ? "" : thisDep.getVersionString(),
                        thisDep == null ? "" : thisDep.getTimeStringNullable(),
                        versionsBehindTags.toString(","),
                        versionsBehindNoTags.toString(",")
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
            // Filter dependencies to only trigger on project B
            for (Dependency dep: version.getDependencies()) {
                if (dep.getProjectName().equals(b.getName())){
                    // Find the linked version, and the newest version
                    String declaredDependency = dep.getVersion();
                    int j = 0;
                    int result = -1;

                    for (ProjectVersionInfo bVersion: orderedVersionsB) {
                        if (declaredDependency.equals(bVersion.getVersionString())){
                            result = j;
                            break;
                        }
                        j++;
                    }

                    linksAtoB.add(result);
                    break;
                }
            }
        }

        // Describe the changes between versions in timeline A and timeline B
        processPair = new ProcessPair(orderedVersionsA, orderedVersionsB, linksAtoB);
    }
}
