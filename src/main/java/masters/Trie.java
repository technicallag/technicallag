package masters;

import masters.dataClasses.Version;

import java.util.*;

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
