package masters.old;

import masters.old.dataClasses.Dependency;
import masters.old.dataClasses.Project;
import masters.old.dataClasses.ProjectVersionInfo;
import org.apache.log4j.Logger;
import masters.utils.Logging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;

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

    List<ProjectVersionInfo> orderedByVersionA;
    List<ProjectVersionInfo> orderedByTimeA;
    List<ProjectVersionInfo> orderedByTimeB;

//    List<Integer> mapOrderedToUnordered;
    List<Integer> linksTimeOrderedAtoB;
    List<Integer> linksVersionOrderedAtoTimeB;

    // Creates and stores a tree of the major, minor and micro changes within a project
    ProcessPairOld processPairOld;

    Connection c;
    ArrayBlockingQueue<Connection> connections;
    Logger LOG = Logging.getLogger("");
    Vector<String> cumulativeStats;

    /*
 Data Gathering Logic
  */
    public ProjectTimelinePair(String pair, Project a, Project b, ArrayBlockingQueue<Connection> connections, int pos, Vector<String> cumulativeStats) {
        this.pair = pair;
        this.connections = connections;
        this.a = a;
        this.b = b;
        this.pos = pos;
        this.cumulativeStats = cumulativeStats;
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
        cumulativeStats.add(pair + "," + processPairOld.getCumulativeStats());
    }

    List<ProjectVersionInfo> orderVersionsByTimestamp(Project a) {
        return a.getVersions().values().stream()
                .sorted(Comparator.comparing(ProjectVersionInfo::getTimestampNullable))
                .collect(Collectors.toList());
    }

    private void init() {
        // Requires DB connection to get timestamps off the DB
        // Lazily take DB connections so that the thread pool doesn't exhaust its supply
        try {
            c = connections.take();

            // Order versions using timestamps
            a.getVersions().values().forEach(e -> e.getTimestamp(c, a.getName()));
            b.getVersions().values().forEach(e -> e.getTimestamp(c, b.getName()));

            connections.add(c);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Natural version ordering (i.e. Semantic Version ordering)
        orderedByVersionA = a.getVersions().values().stream().sorted().collect(Collectors.toList());

        // Use timestamps to order versions
        orderedByTimeA = orderVersionsByTimestamp(a);
        orderedByTimeB = orderVersionsByTimestamp(b);

        // Link dependencies in project A to project B
        linksTimeOrderedAtoB = getLinks(orderedByTimeA, orderedByTimeB);
        linksVersionOrderedAtoTimeB = getLinks(orderedByVersionA, orderedByTimeB);

        // Describe the changes between versions in timeline A and timeline B
        processPairOld = new ProcessPairOld(orderedByTimeA, orderedByTimeB, linksTimeOrderedAtoB, linksVersionOrderedAtoTimeB);
    }

    // Give it the ordering of the versionOrdered, and it will give you where the timeOrdered one is
    List<Integer> mapVersionOrderToTimeOrder(List<ProjectVersionInfo> versionOrdered, List<ProjectVersionInfo> timeOrdered) {
        List<Integer> list = new ArrayList<>();

        for (int i = 0; i < versionOrdered.size(); i++) {
            for (int j = 0; j < timeOrdered.size(); j++) {
                if (versionOrdered.get(i) == timeOrdered.get(j)) {
                    list.add(j);
                    break;
                }
            }
        }

        return list;
    }

    List<Integer> getLinks(List<ProjectVersionInfo> versionsA, List<ProjectVersionInfo> versionsB) {
        List<Integer> linker = new ArrayList<>();

        for (ProjectVersionInfo version: versionsA) {
            // Filter dependencies to only trigger on project B
            int result = -1;

            for (Dependency dep: version.getDependencies()) {
                if (dep.getProjectName().equals(b.getName())){
                    // Find the linked version, and the newest version
                    String declaredDependency = dep.getVersion();
                    int j = 0;

                    for (ProjectVersionInfo bVersion: versionsB) {
                        if (declaredDependency.equals(bVersion.getVersionString())){
                            result = j;
                            break;
                        }
                        j++;
                    }
                    break;
                }
            }

            linker.add(result);
        }

        return linker;
    }

    /*
    Printing Logic
     */
    private void printTimelines(){
        List<Integer> mapTimeToVersion = mapVersionOrderToTimeOrder(orderedByVersionA, orderedByTimeA);
        try (BufferedWriter out = new BufferedWriter(new FileWriter(new File("data/timelines2/" + pair.replace(":", "$") + ".csv")))) {
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
            writeLine(out, headers);

            // Write data out ordered by Version
            for (int i = 0; i < orderedByVersionA.size(); i++) {
                ProjectVersionInfo thisA = orderedByVersionA.get(i);

                // It is possible that for a given version of A, B does not exist yet
                ProjectVersionInfo thisDep = linksVersionOrderedAtoTimeB.get(i) == -1 ? null : orderedByTimeB.get(linksVersionOrderedAtoTimeB.get(i));
                VersionsBehind versionsBehindTags = processPairOld.howFarBehindIsA[mapTimeToVersion.get(i)];
                VersionsBehind versionsBehindNoTags = processPairOld.howFarBehindIsANoTags[mapTimeToVersion.get(i)];

                writeLine(out,
                        thisA.getVersion().toString(),
                        thisA.getTimeStringNullable(),
                        thisDep == null ? "" : thisDep.getVersionString(),
                        thisDep == null ? "" : thisDep.getTimeStringNullable(),
                        versionsBehindTags == null ? VersionsBehind.emptyToString(",") : versionsBehindTags.toString(","),
                        versionsBehindNoTags == null ? VersionsBehind.emptyToString(",") : versionsBehindNoTags.toString(",")
                );
            }

            writeLine(out, "\n\n");
            writeLine(out, "Data ordered by Publish Date");

            // Write data out ordered by Publish Date
            for (int i = 0; i < orderedByVersionA.size(); i++) {
                ProjectVersionInfo thisA = orderedByVersionA.get(i);

                // It is possible that for a given version of A, B does not exist yet
                ProjectVersionInfo thisDep = linksTimeOrderedAtoB.get(i) == -1 ? null : orderedByTimeB.get(linksTimeOrderedAtoB.get(i));
                VersionsBehind versionsBehindTags = processPairOld.howFarBehindIsA[i];
                VersionsBehind versionsBehindNoTags = processPairOld.howFarBehindIsANoTags[i];

                writeLine(out,
                        thisA.getVersion().toString(),
                        thisA.getTimeStringNullable(),
                        thisDep == null ? "" : thisDep.getVersionString(),
                        thisDep == null ? "" : thisDep.getTimeStringNullable(),
                        versionsBehindTags == null ? VersionsBehind.emptyToString(",") : versionsBehindTags.toString(","),
                        versionsBehindNoTags == null ? VersionsBehind.emptyToString(",") : versionsBehindNoTags.toString(",")
                );
            }

            writeLine(out, "\n\n");

            // Project B's time ordered data for reference
            writeLine(out, "ProjBVersion", "ProjBDate");
            for (ProjectVersionInfo b: orderedByTimeB) {
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
}
