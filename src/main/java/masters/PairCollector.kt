package masters;

import masters.dataClasses.Version
import masters.utils.Database;
import masters.utils.Logging;
import masters.libiostudy.VersionCategoryWrapper;
import java.lang.NullPointerException

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

public data class PairIDs (val projectID: Int, val dependencyID: Int)

public class PairCollector (pm: PackageManager?) {

    val availablePairs = ConcurrentHashMap<PackageManager, Collection<PairIDs>>()
    val counts = ConcurrentHashMap<PackageManager, Map<Status, Int>>()
    val log = Logging.getLogger("")

    public enum class PackageManager(val nameInDB: String) {
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

        //ATOM("Atom"),
        //CARGO("Cargo"),
        //HEX("Hex"),
        //MAVEN("Maven"),
        //NPM("NPM"),
        //NUGET("NuGet"),
        //PACKAGIST("Packagist"),
        //PYPI("Pypi"),
        RUBYGEMS("Rubygems");

        public override fun toString() : String = nameInDB
    }

    public enum class Status {
        INCLUDED,
        SUBCOMPONENT,
        VERSIONS_NON_SEMVER,
        FLEXIBLE,
        NOT_IN_DATASET
    }

    init {
        if (pm == null) PackageManager.values().forEach { loadPM(it) }
        else loadPM(pm)
    }

    private fun loadPM(it: PackageManager) {
        log.info("Beginning pair data loading for $it")

        val rs = Database.runQueryNoLogs(
                "SELECT projectid, versionnumber, dependencyprojectid, dependencyrequirements FROM dependencies WHERE platform = ?;",
                it.nameInDB)

        val pairs = HashMap<PairIDs, Status>()
        try {
            while(rs.next()) {
                val projVers = rs.getString("versionnumber")
                val vers = rs.getString("dependencyrequirements")
                val projID = rs.getInt("projectid")
                val depID = rs.getInt("dependencyprojectid")

                val pair = PairIDs(projID, depID)
                var status = pairs.getOrDefault(pair, Status.INCLUDED) //{ flagDependenciesNotInRepository(pair) }
                if (status < Status.FLEXIBLE) {
                    if (flagVersionRanges(it, vers) == Status.FLEXIBLE) status = Status.FLEXIBLE
                    else if (violatesSemver(projVers) || violatesSemver(vers))  {
                        status = Status.VERSIONS_NON_SEMVER
                        log.debug(projVers + " violatesSemver: " + violatesSemver(projVers) + "\t" + vers + " violatesSemver: " + violatesSemver(vers))
                    }
                    else if (projVers == vers) status = Status.SUBCOMPONENT
                }

                pairs[pair] = status
            }
        } catch (e: SQLException) {
            log.error(e)
        } catch (e: NullPointerException) { // Was possible out of flagVersionRanges, but should be resolved now
            log.error(e)
            e.printStackTrace()
        }

        val stats = mutableMapOf<Status, Int>()
        Status.values().forEach {
            status -> stats[status] = pairs.count {
                (_, pairstatus) -> pairstatus == status
            }
        }
        counts[it] = stats

        availablePairs[it] = pairs.filter {
            (pair, status) -> status == Status.INCLUDED && flagDependenciesNotInRepository(pair) == Status.INCLUDED
        }.keys

        log.info("Finished pair data loading for $it")
    }

    private fun flagVersionRanges(pm: PackageManager, vers: String) : Status {
        when (VersionCategoryWrapper.getClassification(pm.toString(), vers)) {
            "fixed", "soft" -> return Status.INCLUDED
            else -> return Status.FLEXIBLE
        }
    }

    private fun flagDependenciesNotInRepository(pairIDs: PairIDs) : Status {
        val rs = Database.runQueryNoLogs("SELECT id FROM projects WHERE id = ?;", pairIDs.dependencyID)
        return if (rs.next()) Status.INCLUDED  else Status.NOT_IN_DATASET
    }

    private fun violatesSemver(version: String?) : Boolean {
        if (version.isNullOrBlank()) return true
        return Version.create(version).versionTokens.size < 1
    }

}

