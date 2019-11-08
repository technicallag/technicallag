package masters.old;

import masters.old.dataClasses.ProjectVersionInfo;
import masters.libiostudy.Version;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class ProcessPairOld {
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
    ProcessPairOld(List<ProjectVersionInfo> orderedVersionsA, List<ProjectVersionInfo> orderedVersionsB, List<Integer> linkedDeps, List<Integer> linkedByStringOrdering) {
        this.orderedVersionsA = orderedVersionsA;
        this.orderedVersionsB = orderedVersionsB;
        this.linkedDeps = linkedDeps;
        this.linkedByStringOrdering = linkedByStringOrdering;

        buildTries();
        cumulativeStats();
    }
}
