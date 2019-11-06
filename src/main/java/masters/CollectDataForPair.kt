package masters

import masters.dataClasses.Version
import masters.utils.Database
import masters.utils.Logging
import java.lang.NullPointerException
import java.sql.SQLException

/**
 * Created by Jacob Stringer on 29/10/2019.
 */

data class ProjectVersion(val version: Version, val dependency: Version?, val time: String)

data class DependencyVersion(val version: Version, val time: String)

data class PairWithData(val pairIDs: PairIDs, val aVersions: MutableList<ProjectVersion>, var bVersions: MutableList<DependencyVersion>)

class CollectDataForPair(val pairIDs: PairIDs) {
    companion object {
        @JvmStatic
        public fun collectData(pairIDs: PairIDs) : PairWithData {
            val aVersions = mutableListOf<ProjectVersion>()
            val bVersions = mutableListOf<DependencyVersion>()
            val pair = PairWithData(pairIDs, aVersions, bVersions)

            try {
                Database.getDepHistory(pair)
                Database.getProjectHistory(pair)
            } catch (e: SQLException) {
                Logging.getLogger("").error(e)
            } catch (e: NullPointerException) {
                e.printStackTrace()
                println(pairIDs)
            }
            return pair
        }
    }
}