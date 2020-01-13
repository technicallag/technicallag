package masters

import masters.libiostudy.Version
import masters.utils.Database
import masters.utils.Logging
import masters.libiostudy.VersionCategoryWrapper
import java.io.*
import java.lang.Integer.min
import java.math.BigInteger

import java.sql.SQLException
import kotlin.collections.HashMap

/**
 * Created by Jacob Stringer on 24/10/2019.
 *
 * Finds pairs of projects A and B where A depends on B
 * All the filtering processes are done in this class
 *
 */

data class PairIDs (val projectID: Int, val dependencyID: Int) : Serializable

class PairCollector {
    var counts: HashMap<PackageManager, Map<Status, Int>>
    private val log = Logging.getLogger("")

    val PAIR_FOLDER = "../cached_pair_ids"

    private data class SemverViolations(val f: String, val fviolates: Boolean, val s: String, val sviolates: Boolean) {
        override fun toString(): String {
            return "$f, $fviolates, $s, $sviolates\n"
        }
    }

    enum class Status : Serializable {
        INCLUDED,
        VERSIONS_NON_SEMVER,
        FLEXIBLE,
        SUBCOMPONENT,
        NOT_IN_DATASET
    }

    enum class PackageManager(val nameInDB: String) : Serializable {
        ATOM("Atom"),
        CARGO("Cargo"),
        CPAN("CPAN"), // Only any/at least
        CRAN("CRAN"), // Only any/at least
        DUB("Dub"),
        ELM("Elm"),
        HAXELIB("Haxelib"),
        HEX("Hex"),
        HOMEBREW("Homebrew"), // Only any
        MAVEN("Maven"),
        NPM("NPM"),
        NUGET("NuGet"),
        PACKAGIST("Packagist"),
        PUB("Pub"),
        PUPPET("Puppet"),
        PYPI("Pypi"),
        RUBYGEMS("Rubygems");

        override fun toString() : String = nameInDB
    }

    // The initialisation phase collects any information that has not yet been cached and produces pair statistics
    init {
        // Load in any counts data from file
        try {
            val streamin = ObjectInputStream(FileInputStream("$PAIR_FOLDER/pair_counts.bin"))
            counts = streamin.readObject() as HashMap<PackageManager, Map<Status, Int>>
            streamin.close()
        } catch (e: FileNotFoundException) {
            log.warn("File $PAIR_FOLDER/pair_counts.bin was not found")
            counts = HashMap()
        }

        // Preprocess pair data from DB into Files as needed
        // Delete pair files if reload from database is desired
        PackageManager.values().forEach {
            val pairData = File("$PAIR_FOLDER/$it.csv")
            val flexiData = File("$PAIR_FOLDER/${it}_flexible.csv")
            if (!pairData.exists() || !flexiData.exists()) loadPMFromDB(it)
        }

        // Pretty print aggregated count data
        printPairInfoToCSV()

        // Save counts data back to file
        val streamout = ObjectOutputStream(FileOutputStream("$PAIR_FOLDER/pair_counts.bin"))
        streamout.writeObject(counts)
        streamout.close()
    }

    // Call to collect pairs for a given PM
    fun getPairs(pm: PackageManager, status: Status) : List<PairIDs> {
        val availablePairs = mutableListOf<PairIDs>()
        val location = when (status) {
            Status.INCLUDED -> "$PAIR_FOLDER/$pm.csv"
            Status.FLEXIBLE -> "$PAIR_FOLDER/${pm}_flexible.csv"
            else -> return availablePairs
        }

        BufferedReader(FileReader(location)).use {
            for (lineraw in it.readLines()) {
                if (lineraw == "Project, Dependency") continue
                val line = lineraw.split(",").map { it.toInt() }
                availablePairs.add(PairIDs(line[0], line[1]))
            }
        }
        return availablePairs
    }

    private fun loadPMFromDB(pm: PackageManager) {
        log.info("Beginning pair data processing for $pm")

        val pairs = HashMap<PairIDs, Status>()
        val semverCheck = mutableListOf<SemverViolations>()

        Database.runQueryNoLogs("SELECT projectid, versionnumber, dependencyprojectid, dependencyrequirements, projectname, dependencyname FROM dependencies WHERE platform = ?;", pm.nameInDB).use {rs ->
            try {
                while(rs.next()) {
                    val projVers = rs.getString("versionnumber")
                    val vers = rs.getString("dependencyrequirements")
                    val projID = rs.getInt("projectid")
                    val depID = rs.getInt("dependencyprojectid")
                    val projectname = rs.getString("projectname")
                    val dependencyname = rs.getString("dependencyname")

                    val pair = PairIDs(projID, depID)
                    var status = pairs.getOrDefault(pair, initialPairTest(pair, projectname, dependencyname))
                    if (status < Status.FLEXIBLE) {
                        if (flagVersionRanges(pm, vers) == Status.FLEXIBLE) status = Status.FLEXIBLE

                        else if (status == Status.INCLUDED && (violatesSemver(projVers) || violatesSemver(vers)))  {
                            status = Status.VERSIONS_NON_SEMVER
                            semverCheck.add(SemverViolations(projVers, violatesSemver(projVers), vers, violatesSemver(vers)))
                        }
                    }

                    pairs[pair] = status
                }
            } catch (e: SQLException) {
                log.error(e)
            }
        }

        // Update pair counts grouped by status
        val stats = mutableMapOf<Status, Int>()
        Status.values().forEach {
            status -> stats[status] = pairs.count {
                (_, pairstatus) -> pairstatus == status
            }
        }
        counts[pm] = stats

        // Print the semver violations for later checking
        printSemverViolations(semverCheck, pm)

        // Store fixed only project pairs
        BufferedWriter(FileWriter("$PAIR_FOLDER/$pm.csv")).use { included ->
            included.write("Project, Dependency\n")
            pairs.filter { it.value == Status.INCLUDED }
                    .forEach { entry -> included.write("${entry.key.projectID},${entry.key.dependencyID}\n") }
        }

        // Store the flexible project pairs
        BufferedWriter(FileWriter("$PAIR_FOLDER/${pm}_flexible.csv")).use { included ->
            included.write("Project, Dependency\n")
            pairs.filter { it.value == Status.FLEXIBLE }
                    .forEach { entry -> included.write("${entry.key.projectID},${entry.key.dependencyID}\n") }
        }

        // Store subcomponent information (discard remainder)
        BufferedWriter(FileWriter("$PAIR_FOLDER/${pm}_subcomponents.csv")).use { out ->
            out.write("Project, Dependency, ProjName, DepName\n")
            pairs.forEach { entry ->
                if (entry.value == Status.SUBCOMPONENT)
                    out.write("${entry.key.projectID},${entry.key.dependencyID},${Database.getProjectName(entry.key.projectID)},${Database.getProjectName(entry.key.dependencyID)}\n")
            }
        }

        log.info("Finished pair data processing for $pm")
    }

    private fun printPairInfoToCSV() {
        try {
            BufferedWriter(FileWriter(File("$PAIR_FOLDER/pair_info.csv"))).use { out ->
                // Headers
                out.write(",")
                for (status in Status.values()) {
                    out.write("$status,")
                }
                out.write("\n")

                // Data, one package manager at a time
                for (pm in PackageManager.values()) {
                    out.write("$pm,")
                    for (status in Status.values()) {
                        out.write("${(counts[pm]?.get(status)?.toString() ?: 0)},")
                    }
                    out.write("\n")
                }
            }
        } catch (e: IOException) {
            log.error(e)
        }
    }

    private fun printSemverViolations(semverCheck: MutableList<SemverViolations>, pm: PackageManager) {
        try {
            BufferedWriter(FileWriter("$PAIR_FOLDER/${pm}_semver_violations.csv")).use {
                semverCheck.forEach { sem -> it.write(sem.toString()) }
            }
        } catch (e: IOException) {
            log.error(e)
        }

    }

    private fun flagVersionRanges(pm: PackageManager, vers: String) : Status {
        when (VersionCategoryWrapper.getClassification(pm.toString(), vers)) {
            "fixed", "soft" -> return Status.INCLUDED
            else -> return Status.FLEXIBLE
        }
    }

    private fun violatesSemver(version: String?) : Boolean {
        if (version.isNullOrBlank()) return true
        val v = Version.create(version)
        for (token in v.versionTokens)
            if (token > BigInteger.valueOf(10000)) return true
        return Version.create(version).versionTokens.size < 1
    }

    private fun initialPairTest(pair: PairIDs, proj: String, dep: String) : Status {
        if (!Database.isProjectInDB(pair.dependencyID))
            return Status.NOT_IN_DATASET

        // Check the first half of the project and dependency names to see if they match. 4 chars min, or else they have to be exact matches
        // (yes there are exact matches in ids as well as names)
        val firsthalf = min(proj.length / 2, dep.length / 2)
        if (firsthalf > 3 && proj.substring(0, firsthalf) == dep.substring(0, firsthalf)
                || proj == dep
                || pair.dependencyID == pair.projectID)
            return Status.SUBCOMPONENT

        // Default is that we include the pair
        return Status.INCLUDED
    }

}