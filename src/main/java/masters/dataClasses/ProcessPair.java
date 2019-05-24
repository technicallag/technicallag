package masters.dataClasses;

import java.sql.Timestamp;
import java.util.*;

class VersionsBehind {
    long[] numberBehind = new long[3]; // Major, minor, micro
    String[] latestVersion = new String[3];

    public String toString(String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(numberBehind[i]);
            sb.append(sep);
            sb.append(latestVersion[i]);
            if (i < 2) sb.append(sep);
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
    Version newestAtThisLevel; // Newest major at level 0, newest minor at level 1 etc
    int level;

    // Creates root node
    public Trie(int level) {
        this.children = new HashMap<>();
        this.level = level;
        this.newestAtThisLevel = Version.create("0.0.0");
    }

    private int getNum(Version v, int level) {
        return v.versionTokens.get(level).intValue();
    }

    // Creates extra nodes where necessary
    public void addVersion(Version v, int level) {
        if (level == 3 || v.versionTokens.size() == level) return;

        int thisnum = v.versionTokens.size() <= level ? 0 : getNum(v, level); // 4.2 = 4.2.0
        Trie child = children.getOrDefault(thisnum, new Trie(level + 1));

        if (newestAtThisLevel.compareTo(v) < 0) newestAtThisLevel = v;

        child.addVersion(v, level + 1);
        children.put(thisnum, child);
    }

    // Traverses trie to find how many LATER (not newer) versions there are at the given time
    public VersionsBehind getVersionsBehind(Version v, int level) {
        if (level > 2 || v.versionTokens.size() == level) return new VersionsBehind();

        int thisnum = getNum(v, level);
        VersionsBehind vb = children.get(thisnum).getVersionsBehind(v, level + 1);

        vb.numberBehind[level] = children.keySet().stream().filter(num -> num > thisnum).count();
        vb.latestVersion[level] = newestAtThisLevel.toString();
        return vb;
    }
}


public class ProcessPair {
    /*
    Two Tries. One for all versions, the other for only untagged versions.
    Build tries in chronological order - with each published version of A, add in all published versions of B that happened before that.
    VersionsBehind[] stores the information as it is produced. Null if there are no dependencies yet.
     */
    Trie versionTree = new Trie(0);
    Trie versionTreeNoTags = new Trie(0);
    VersionsBehind[] howFarBehindIsA;
    VersionsBehind[] howFarBehindIsANoTags;

    /*
    Constructor progressively adds to the Tries and collects results from projects supplied (and how they link)
     */
    ProcessPair(List<ProjectVersionInfo> orderedVersionsA, List<ProjectVersionInfo> orderedVersionsB, List<Integer> linkedDeps) {
        howFarBehindIsA = new VersionsBehind[orderedVersionsA.size()];

        int bIndex = 0;
        for (int i = 0; i < orderedVersionsA.size(); i++) {
            // If there's not a declared dependency at version Ai, continue
            if (linkedDeps.get(i) == -1) continue;

            // Add in all versions of B that are before A
            ProjectVersionInfo aVersion = orderedVersionsA.get(i);
            Timestamp aPublishTime = aVersion.getTimestampNullable();

            while (bIndex < orderedVersionsB.size()) {
                ProjectVersionInfo bVersion = orderedVersionsB.get(bIndex);
                Timestamp bPublishTime = bVersion.getTimestampNullable();
                if (aPublishTime.before(bPublishTime)) {
                    break;
                } else {
                    versionTree.addVersion(bVersion.getVersion(), 0);
                    if (bVersion.getVersion().additionalInfo == null)
                        versionTreeNoTags.addVersion(bVersion.getVersion(), 0);
                    bIndex++;
                }
            }

            // Compare this version with the other
            Version declaredVersion = orderedVersionsB.get(linkedDeps.get(i)).getVersion();
            howFarBehindIsA[i] = versionTree.getVersionsBehind(declaredVersion, 0);
            if (declaredVersion.additionalInfo == null)
                howFarBehindIsANoTags[i] = versionTreeNoTags.getVersionsBehind(declaredVersion, 0);
        }
    }
}
