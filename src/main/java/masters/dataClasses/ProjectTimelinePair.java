package masters.dataClasses;

import org.apache.log4j.Logger;
import masters.utils.Logging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

class TimelineStats {
    // General statistics about how projects are filtered
    static AtomicInteger SEMVER_PAIRS = new AtomicInteger(0);
    static AtomicInteger NOT_SEMVER_PAIRS = new AtomicInteger(0);
    static AtomicInteger LARGE_ENOUGH = new AtomicInteger(0);
    static AtomicInteger NOT_LARGE_ENOUGH = new AtomicInteger(0);

    // Timestamp anomaly quantification
    static AtomicLong TIME_PAST_DECLARATION = new AtomicLong(0);
    static AtomicLong NUMBER_PAST_DECLARATION = new AtomicLong(0);

    // What strategy do project pairs use
    static AtomicInteger NEVER_UPDATE = new AtomicInteger(0);
    static AtomicInteger UPDATE_TO_NEWEST = new AtomicInteger(0);
    static AtomicInteger LAG_BEHIND = new AtomicInteger(0);
    static AtomicInteger WENT_BACKWARDS = new AtomicInteger(0);

    static void log() {
        Logger LOG = Logging.getLogger("");

        LOG.info("There were " + SEMVER_PAIRS.toString() + " pairs where both use semantic versioning");
        LOG.info("There were " + NOT_SEMVER_PAIRS.toString() + " pairs that were discarded as they don't use semantic versioning");
        LOG.info("There were " + NOT_LARGE_ENOUGH.toString() + " pairs discarded due to small size of history");

        LOG.info("There were " + NUMBER_PAST_DECLARATION.toString() + " dependencies that were declared before publish timestamp");
        if (NUMBER_PAST_DECLARATION.longValue() > 0)
            LOG.info("These were published an average of " + TIME_PAST_DECLARATION.longValue()/ NUMBER_PAST_DECLARATION.longValue()/3_600_000 + " hours after being declared");

        LOG.info("There were " + LARGE_ENOUGH.toString() + " pairs with sufficient history to write to file");

        LOG.info(NEVER_UPDATE.toString() + " project pairs never updated their dependency");
        LOG.info(UPDATE_TO_NEWEST.toString() + " project pairs had an update which went to the newest version");
        LOG.info(LAG_BEHIND.toString() + " project pairs updated but never to the newest version");
        LOG.info(WENT_BACKWARDS.toString() + " project pairs had at least one version that went backwards");
    }
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

    List<ProjectVersionInfo> orderedByVersionA;
    List<ProjectVersionInfo> orderedByTimeA;
    List<ProjectVersionInfo> orderedByTimeB;

//    List<Integer> mapOrderedToUnordered;
    List<Integer> linksTimeOrderedAtoB;
    List<Integer> linksVersionOrderedAtoTimeB;

    // Creates and stores a tree of the major, minor and micro changes within a project
    ProcessPair processPair;

    Connection c;
    ArrayBlockingQueue<Connection> connections;
    Logger LOG = Logging.getLogger("");
    Vector<String> cumulativeStats;

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
        List<Integer> mapTimeToVersion = mapVersionOrderToTimeOrder(orderedByVersionA, orderedByTimeA);
        try (BufferedWriter out = new BufferedWriter(new FileWriter(new File("data/timelines2/" + pair.replace(":", "$") + ".csv")))) {
            writeLine(out, headers);

            // Write data out ordered by Version
            for (int i = 0; i < orderedByVersionA.size(); i++) {
                ProjectVersionInfo thisA = orderedByVersionA.get(i);

                // It is possible that for a given version of A, B does not exist yet
                ProjectVersionInfo thisDep = linksVersionOrderedAtoTimeB.get(i) == -1 ? null : orderedByTimeB.get(linksVersionOrderedAtoTimeB.get(i));
                VersionsBehind versionsBehindTags = processPair.howFarBehindIsA[mapTimeToVersion.get(i)];
                VersionsBehind versionsBehindNoTags = processPair.howFarBehindIsANoTags[mapTimeToVersion.get(i)];

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
                VersionsBehind versionsBehindTags = processPair.howFarBehindIsA[i];
                VersionsBehind versionsBehindNoTags = processPair.howFarBehindIsANoTags[i];

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

//    private void writeHeader(BufferedWriter out) throws IOException {
//        out.write(String.join(",", headers));
//        out.write("\n");
//    }

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
        cumulativeStats.add(pair + "," + processPair.getCumulativeStats());
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
        orderedByVersionA = a.getVersions().values().stream().sorted().collect(Collectors.toList());
        orderedByTimeA = a.getVersions().values().stream()
                .sorted((e1, e2) -> e1.getTimestampNullable().compareTo(e2.getTimestampNullable()))
                .collect(Collectors.toList());
        orderedByTimeB = b.getVersions().values().stream()
                .sorted((e1, e2) -> e1.getTimestampNullable().compareTo(e2.getTimestampNullable()))
                .collect(Collectors.toList());

        // Link dependencies in project A to project B
        linksTimeOrderedAtoB = getLinks(orderedByTimeA, orderedByTimeB);
        linksVersionOrderedAtoTimeB = getLinks(orderedByVersionA, orderedByTimeB);

        // Describe the changes between versions in timeline A and timeline B
        processPair = new ProcessPair(orderedByTimeA, orderedByTimeB, linksTimeOrderedAtoB, linksVersionOrderedAtoTimeB);
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
}

class VersionsBehind {
    long[] numberBehind = new long[3]; // Major, minor, micro
    String[] latestVersion = new String[]{"", "", ""};

    String toString(String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(numberBehind[i]);
            sb.append(sep);
            sb.append(latestVersion[i]);
            if (i < 2) sb.append(sep);
        }
        return sb.toString();
    }

    static String emptyToString(String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(sep);
        }
        return sb.toString();
    }
}

class Trie {
    /*
    I cannot be sure that later timestamp implies later version (2.5.1 could be released before 2.3.2!)
    Therefore I would rather not impose a time ordering that a List would give.
    Key = the value of the major/minor/micro version
    Trie goes to level 3, with level 0 being the root.
     */
    Map<Integer, Trie> children;
    Set<Version> dependencies;
    List<Version> dependenciesOrdered;
    Version newestAtThisLevel; // Newest major at level 0, newest minor at level 1 etc
    int level;

    ProcessPair pp;

    // Creates root node
    Trie(int level, ProcessPair pp) {
        this.pp = pp;
        this.children = new HashMap<>();
        this.level = level;
        this.newestAtThisLevel = Version.create("0.0.0");
        this.dependencies = new HashSet<>();
        this.dependenciesOrdered = new ArrayList<>();
    }

    private int getNum(Version v) {
        return v.versionTokens.get(level).intValue();
    }

    // Creates extra nodes where necessary
    void addVersion(Version v, int level) {
        if (level > 2) return;

        int thisnum = v.versionTokens.size() <= level ? 0 : getNum(v); // 4.2 = 4.2.0
        Trie child = children.getOrDefault(thisnum, new Trie(level + 1, pp));

        if (newestAtThisLevel.compareTo(v) < 0) newestAtThisLevel = v;

        child.addVersion(v, level + 1);
        children.put(thisnum, child);
    }

    // Traverses trie to find how many LATER (not newer) versions there are at the given time
    VersionsBehind getVersionsBehind(Version v) {
        if (level == 0) {
            dependencies.add(v);
            dependenciesOrdered.add(v);
        }
        if (level > 2) return new VersionsBehind();

        int thisnum = v.versionTokens.size() <= level ? 0 : getNum(v); // 4.2 = 4.2.0
        try {
            VersionsBehind vb = children.get(thisnum).getVersionsBehind(v);

            vb.numberBehind[level] = children.keySet().stream().filter(num -> num > thisnum).count();
            vb.latestVersion[level] = newestAtThisLevel.toString();
            return vb;
        }

        // Try to resolve why this is happening. The versions are there but are not being added in at the time should be (some timestamp variations)
        // The crux of the problem: timestamps are not quite right in the DB.
        // Question: What information do we need to ascertain if this is problematic or not?
        catch (NullPointerException e) {
            if (pp.nextB.getVersion().equals(v)) { // 55
                TimelineStats.NUMBER_PAST_DECLARATION.getAndIncrement();
                TimelineStats.TIME_PAST_DECLARATION.getAndAdd(pp.nextB.getTimestampNullable().getTime() - pp.aTime.getTime());
            } else if (pp.nextnextB.getVersion().equals(v)) { // 32
                TimelineStats.NUMBER_PAST_DECLARATION.getAndIncrement();
                TimelineStats.TIME_PAST_DECLARATION.getAndAdd(pp.nextnextB.getTimestampNullable().getTime() - pp.aTime.getTime());
            } else if (pp.curB.getVersion().equals(v)) { // 600
                TimelineStats.NUMBER_PAST_DECLARATION.getAndIncrement();
                TimelineStats.TIME_PAST_DECLARATION.getAndAdd(pp.curB.getTimestampNullable().getTime() - pp.aTime.getTime());
            } else { // 50
//                Logging.getLogger("").trace(v.toString() + " " + level + " " + children.keySet().stream().map(k -> k + ";").collect(Collectors.joining()) + " " + newestAtThisLevel.toString());
            }

        }

        return new VersionsBehind();
    }
}


class ProcessPair {
    /*
    Two Tries. One for all versions, the other for only untagged versions.
    Build tries in chronological order - with each published version of A, add in all published versions of B that happened before that.
    VersionsBehind[] stores the information as it is produced. Null if there are no dependencies yet.
     */
    List<ProjectVersionInfo> orderedVersionsA, orderedVersionsB;
    List<Integer> linkedDeps, linkedByStringOrdering;

    Trie versionTree = new Trie(0, this);
    Trie versionTreeNoTags = new Trie(0, this);
    VersionsBehind[] howFarBehindIsA;
    VersionsBehind[] howFarBehindIsANoTags;

    Timestamp aTime;
    ProjectVersionInfo curB, nextB, nextnextB;

    // Cumulative stats about this Project Pair
    int numVersA;
    int numVersB;

    int numDistinctDepDecs;
    int versionsWithDeps;

    double avgMajorVersBehind = 0.0;
    double avgMinorVersBehind = 0.0;
    double avgMicroVersBehind = 0.0;

    int numVersionsNotCurrentMajor = 0;
    int numVersionsNotCurrentMinor = 0;
    int numVersionsNotCurrentMicro = 0;

    double avgMajorVersBehindNoTags = 0.0;
    double avgMinorVersBehindNoTags = 0.0;
    double avgMicroVersBehindNoTags = 0.0;

    int numVersionsNotCurrentMajorNoTags = 0;
    int numVersionsNotCurrentMinorNoTags = 0;
    int numVersionsNotCurrentMicroNoTags = 0;

    int numMajorDecChanges = 0;
    int numMinorDecChanges = 0;
    int numMicroDecChanges = 0;

    int numPossibleMajorDecChanges = 0;
    int numPossibleMinorDecChanges = 0;

    int numBackwardsDecChanges = 0;

    // Filtering versions by tag
    private String[] allowedTags = new String[]{
            "",
            "RELEASE"
    };

    private boolean allowTag(String s) {
        for (String allowed: allowedTags) {
            if (s.toLowerCase().equals(allowed.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public String getCumulativeStats() {
        return String.format("%d,%d,%d,%d,%f,%f,%f,%d,%d,%d,%f,%f,%f,%d,%d,%d,%d,%d,%d,%d",
                numVersA, numVersB, numDistinctDepDecs, versionsWithDeps,
                avgMajorVersBehind, avgMinorVersBehind, avgMicroVersBehind,
                numVersionsNotCurrentMajor, numVersionsNotCurrentMinor, numVersionsNotCurrentMicro,
                avgMajorVersBehindNoTags, avgMinorVersBehindNoTags, avgMicroVersBehindNoTags,
                numVersionsNotCurrentMajorNoTags, numVersionsNotCurrentMinorNoTags, numVersionsNotCurrentMicroNoTags,
                numMajorDecChanges, numMinorDecChanges, numMicroDecChanges, numBackwardsDecChanges);
    }

    private void cumulativeStats() {
        numVersA = orderedVersionsA.size();
        numVersB = orderedVersionsB.size();

        numDistinctDepDecs = versionTree.dependencies.size();
        versionsWithDeps = versionTree.dependenciesOrdered.size();

        // Get average number of dependencies behind
        for (int i = 0; i < linkedDeps.size(); i++) {
            int link = linkedDeps.get(i);
            if (link == -1) continue; // No dependency

            VersionsBehind vb = howFarBehindIsA[i];
            avgMajorVersBehind += (double)vb.numberBehind[0] / versionsWithDeps;
            avgMinorVersBehind += (double)vb.numberBehind[1] / versionsWithDeps;
            avgMicroVersBehind += (double)vb.numberBehind[2] / versionsWithDeps;

            numVersionsNotCurrentMajor += vb.numberBehind[0] > 0 ? 1 : 0;
            numVersionsNotCurrentMinor += vb.numberBehind[1] > 0 ? 1 : 0;
            numVersionsNotCurrentMicro += vb.numberBehind[2] > 0 ? 1 : 0;

            VersionsBehind vbnt = howFarBehindIsANoTags[i];
            avgMajorVersBehindNoTags += (double)vbnt.numberBehind[0] / versionsWithDeps;
            avgMinorVersBehindNoTags += (double)vbnt.numberBehind[1] / versionsWithDeps;
            avgMicroVersBehindNoTags += (double)vbnt.numberBehind[2] / versionsWithDeps;

            numVersionsNotCurrentMajorNoTags += vbnt.numberBehind[0] > 0 ? 1 : 0;
            numVersionsNotCurrentMinorNoTags += vbnt.numberBehind[1] > 0 ? 1 : 0;
            numVersionsNotCurrentMicroNoTags += vbnt.numberBehind[2] > 0 ? 1 : 0;
        }

        // Get version differences (use the version ordering rather than the time ordering in case of parallel development)
        linkedByStringOrdering = linkedByStringOrdering.stream().filter(e -> e > -1).collect(Collectors.toList());
        Version prev = orderedVersionsB.get(linkedByStringOrdering.get(0)).getVersion();
        for (int i = 1; i < linkedByStringOrdering.size(); i++) {
            Version cur = orderedVersionsB.get(linkedByStringOrdering.get(i)).getVersion();
            if (!prev.equals(cur)) {
//                if (cur.compareTo(prev) < 0) { // Backwards
//                    numBackwardsDecChanges++;
//                }

                if (!cur.sameMajor(prev)) numMajorDecChanges++;
                else if (!cur.sameMinor(prev)) numMinorDecChanges++;
                else numMicroDecChanges++;
            }
            prev = cur;
        }

        // Use time ordering to figure out
    }

    private void buildTries() {
        howFarBehindIsA = new VersionsBehind[orderedVersionsA.size()];
        howFarBehindIsANoTags = new VersionsBehind[orderedVersionsA.size()];

        Map<Version, Version> dependenciesSoFar = new HashMap<>();
        Version previous = null;

        int bIndex = 0;
        for (int i = 0; i < orderedVersionsA.size(); i++) {
            // If there's not a declared dependency at version Ai, continue
            if (linkedDeps.get(i) == -1) continue;

            /* This section for the next 14 lines is here to try to get a better backwards declaration change statistic
             * The issue is that simultaneous development renders time ordered and version ordered approaches ineffective.
              * This is not fool proof (if an earlier line of simultaneous development has a newer version, it will think it's a backwards change)
              * To fully solve this, I posit that a simultaneous development tracker would be required, with quite a bit of complexity. */
            Version aVers = orderedVersionsA.get(i).getVersion();
            Version dep = orderedVersionsB.get(linkedDeps.get(i)).getVersion();

            // Only count it the first time going backwards. Will still pick up if a later version is still below a previous version
            if (previous != null && dep.compareTo(previous) < 0) {
                for (Map.Entry<Version, Version> entry: dependenciesSoFar.entrySet()) {
                    // If the previous dep is a higher version and the previous A version is an earlier version
                    if (entry.getKey().compareTo(dep) > 0 && entry.getValue().compareTo(aVers) < 0) {
                        numBackwardsDecChanges++;
                        break;
                    }
                }
            }
            previous = dep;
            dependenciesSoFar.compute(dep, (k,v) -> (v == null) ? aVers : (aVers.compareTo(v) < 0) ? aVers : v);

            // Add in all versions of B that are before A
            ProjectVersionInfo aVersion = orderedVersionsA.get(i);
            Timestamp aPublishTime = aVersion.getTimestampNullable();

            while (bIndex < orderedVersionsB.size()) {
                ProjectVersionInfo bVersion = orderedVersionsB.get(bIndex);
                Timestamp bPublishTime = bVersion.getTimestampNullable();
                if (aPublishTime.getTime() < bPublishTime.getTime()) { // There's some minor discrepancies with version timestamps where a depends on b, where b is released a few minutes after a
//                    Logging.getLogger("").trace(String.format("Broken. AVersion=%s BVersion=%s ATime=%s BTime=%s", aVersion.getVersionString(), bVersion.getVersionString(), aPublishTime.toString(), bPublishTime.toString()));
                    break;
                } else {
                    versionTree.addVersion(bVersion.getVersion(), 0);
                    if (allowTag(bVersion.getVersion().additionalInfo)) {
                        versionTreeNoTags.addVersion(bVersion.getVersion(), 0);
                    }
                    bIndex++;
                }
            }

            // DEBUGGING FOR TIMESTAMP ANOMALIES
            int cur = Math.min(bIndex, orderedVersionsB.size()-1);
            int next = Math.min(bIndex + 1, orderedVersionsB.size()-1);
            int nextnext = Math.min(bIndex + 2, orderedVersionsB.size()-1);
            aTime = aPublishTime;
            curB = orderedVersionsB.get(cur);
            nextB = orderedVersionsB.get(next);
            nextnextB = orderedVersionsB.get(nextnext);

            // Compare this version with the other
            Version declaredVersion = orderedVersionsB.get(linkedDeps.get(i)).getVersion();
            howFarBehindIsA[i] = versionTree.getVersionsBehind(declaredVersion);
            howFarBehindIsANoTags[i] = allowTag(declaredVersion.additionalInfo) ? versionTreeNoTags.getVersionsBehind(declaredVersion) : howFarBehindIsA[i];
        }
    }

    /*
    Constructor progressively adds to the Tries and collects results from projects supplied (and how they link)
     */
    ProcessPair(List<ProjectVersionInfo> orderedVersionsA, List<ProjectVersionInfo> orderedVersionsB, List<Integer> linkedDeps, List<Integer> linkedByStringOrdering) {
        this.orderedVersionsA = orderedVersionsA;
        this.orderedVersionsB = orderedVersionsB;
        this.linkedDeps = linkedDeps;
        this.linkedByStringOrdering = linkedByStringOrdering;

        buildTries();
        cumulativeStats();
    }
}


