package masters

import masters.libiostudy.Version
import masters.utils.Database
import masters.utils.Logging
import java.lang.NullPointerException
import java.sql.SQLException

/**
 * Created by Jacob Stringer on 29/10/2019.
 */

interface ContainsTime {
    val time: String
}

data class ProjectVersionFlexible(val version: Version, val dependency: String?, override val time: String) : ContainsTime

data class ProjectVersionFixed(val version: Version, val dependency: Version?, override val time: String) : ContainsTime

data class DependencyVersion(val version: Version, override val time: String) : ContainsTime

data class PairFullDataFixed(val pairIDs: PairIDs, var aVersions: List<ProjectVersionFixed>, var bVersions: List<DependencyVersion>)

data class PairFullDataFlexible(val pairIDs: PairIDs, var aVersions: List<ProjectVersionFlexible>, var bVersions: List<DependencyVersion>)

class CollectDataForPair(val pairIDs: PairIDs) {
    companion object {
        @JvmStatic
        public fun collectDataForFixedAnalysis(pairIDs: PairIDs) : PairFullDataFixed {
            try {
                return PairFullDataFixed(pairIDs,
                        Database.getProjectHistoryFixed(pairIDs),
                        Database.getDepHistory(pairIDs))
            } catch (e: SQLException) {
                Logging.getLogger("").error(e)
            } catch (e: NullPointerException) {
                e.printStackTrace()
                println(pairIDs)
            }
            return PairFullDataFixed(pairIDs, emptyList<ProjectVersionFixed>(), emptyList<DependencyVersion>())
        }

        @JvmStatic
        public fun collectDataForFlexibleAnalysis(pairIDs: PairIDs) : PairFullDataFlexible {
            try {
                return PairFullDataFlexible(pairIDs,
                        Database.getProjectHistoryFlexible(pairIDs),
                        Database.getDepHistory(pairIDs))
            } catch (e: SQLException) {
                Logging.getLogger("").error(e)
            } catch (e: NullPointerException) {
                e.printStackTrace()
                println(pairIDs)
            }
            return PairFullDataFlexible(pairIDs, emptyList<ProjectVersionFlexible>(), emptyList<DependencyVersion>())
        }
    }
}