package masters;

import masters.dataClasses.Version
import masters.utils.Database;
import masters.utils.Logging;
import masters.libiostudy.VersionCategoryWrapper;
import java.io.*

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap

/**
 * Created by Jacob Stringer on 24/10/2019.
 *
 * Finds pairs of projects A and B where A depends on B
 * All the filtering processes are done in this class
 *
 */

public data class PairIDs (val projectID: Int, val dependencyID: Int) : Serializable

public class PairCollector {

    val availablePairs = HashMap<PackageManager, MutableList<PairIDs>>()
    var counts = HashMap<PackageManager, Map<Status, Int>>()
    private val log = Logging.getLogger("")

    private data class SemverViolations(val f: String, val fviolates: Boolean, val s: String, val sviolates: Boolean) {
        override fun toString(): String {
            return "$f, $fviolates, $s, $sviolates\n"
        }
    }

    public enum class PackageManager(val nameInDB: String) : Serializable {
        /* Removed from analysis due to negligible numbers of fixed pairwise projects (ProjectPairsIncludedByPM.xlsx)
        CPAN("CPAN"),
        CRAN("CRAN"),
        DUB("Dub"),
        ELM("Elm"),
        HAXELIB("Haxelib"),
        HOMEBREW("Homebrew"),
        PUB("Pub"),
        PUPPET("Puppet"),
        */

        ATOM("Atom"),
        CARGO("Cargo"),
        HEX("Hex"),
        MAVEN("Maven"),
        NPM("NPM"),
        NUGET("NuGet"),
        PACKAGIST("Packagist"),
        PYPI("Pypi"),
        RUBYGEMS("Rubygems");

        public override fun toString() : String = nameInDB
    }

    public enum class Status : Serializable {
        INCLUDED,
        SUBCOMPONENT,
        VERSIONS_NON_SEMVER,
        FLEXIBLE,
        NOT_IN_DATASET
    }

    init {
        // Load in any counts data from file
        try {
            val streamin = ObjectInputStream(FileInputStream("data/preprocessedPairData/pair_counts.bin"))
            counts = streamin.readObject() as HashMap<PackageManager, Map<Status, Int>>
            streamin.close()
        } catch (e: FileNotFoundException) {
            log.warn("File data/preprocessedPairData/pair_counts.bin was not found")
        }

        // Preprocess pair data from DB into Files as needed
        PackageManager.values().forEach {
            availablePairs[it] = mutableListOf()
            val pairData = File("data/preprocessedPairData/$it.csv")
            if (!pairData.exists()) loadPMFromDB(it)
        }

        // Load the pair data in last to minimise memory footprint
        PackageManager.values().forEach {
            loadPMFromFile(it)
        }

        // Pretty print aggregated count data
        printPairInfoToCSV()

        // Save counts data back to file
        val streamout = ObjectOutputStream(FileOutputStream("data/preprocessedPairData/pair_counts.bin"))
        streamout.writeObject(counts)
        streamout.close()
    }

    private fun loadPMFromFile(pm: PackageManager) {
        BufferedReader(FileReader("data/preprocessedPairData/$pm.csv")).use {
            for (lineraw in it.readLines()) {
                if (lineraw == "Project, Dependency") continue
                val line = lineraw.split(",").map { it.toInt() }
                availablePairs[pm]?.add(PairIDs(line[0], line[1]))
            }
        }
    }

    private fun loadPMFromDB(pm: PackageManager) {
        log.info("Beginning pair data processing for $pm")

        val pairs = HashMap<PairIDs, Status>()
        val semverCheck = mutableListOf<SemverViolations>()

        Database.runQueryNoLogs("SELECT projectid, versionnumber, dependencyprojectid, dependencyrequirements FROM dependencies WHERE platform = ?;", pm.nameInDB).use {rs ->
            try {
                while(rs.next()) {
                    val projVers = rs.getString("versionnumber")
                    val vers = rs.getString("dependencyrequirements")
                    val projID = rs.getInt("projectid")
                    val depID = rs.getInt("dependencyprojectid")

                    val pair = PairIDs(projID, depID)
                    var status = pairs.getOrDefault(pair, Database.isProjectInDB(pair.dependencyID))
                    if (status < Status.FLEXIBLE) {
                        if (flagVersionRanges(pm, vers) == Status.FLEXIBLE) status = Status.FLEXIBLE
                        else if (violatesSemver(projVers) || violatesSemver(vers))  {
                            status = Status.VERSIONS_NON_SEMVER
                            semverCheck.add(SemverViolations(projVers, violatesSemver(projVers), vers, violatesSemver(vers)))
                        }
                        else if (projVers == vers) status = Status.SUBCOMPONENT
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

        // Store the project pairs, both filtered and unfiltered
        BufferedWriter(FileWriter("data/preprocessedPairData/$pm.csv")).use { included ->
            included.write("Project, Dependency\n")
            BufferedWriter(FileWriter("data/preprocessedPairData/${pm}_ALL.csv")).use {all ->
                all.write("Project, Dependency, Status\n")
                pairs.forEach { entry ->
                    if (entry.value == Status.INCLUDED) included.write("${entry.key.projectID},${entry.key.dependencyID}\n")
                    all.write("${entry.key.projectID},${entry.key.dependencyID},${entry.value}\n")
                }
            }
        }

        log.info("Finished pair data processing for $pm")
    }

    private fun printPairInfoToCSV() {
        try {
            BufferedWriter(FileWriter(File("data/pair_info.csv"))).use { out ->
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
            BufferedWriter(FileWriter("data/preprocessedPairData/${pm}_semver_violations.csv")).use {
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
        return Version.create(version).versionTokens.size < 1
    }

}

