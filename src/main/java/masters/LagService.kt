package masters

import kotlinx.coroutines.*
import masters.flexilag.LagCheckingService
import masters.flexilag.MatcherResult
import masters.libiostudy.VersionCategoryWrapper
import masters.utils.Database
import masters.utils.Logging
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Jacob Stringer on 12/12/2019.
 */

class LagService(val pm: PairCollector.PackageManager) {
    // stats[classification][matcherresult] = number_of_instances
    val stats = ConcurrentHashMap<String, MutableMap<MatcherResult, Int>>()

    fun getLag() : Map<String, Map<MatcherResult, Int>> {
        val startTime = System.currentTimeMillis()
        Logging.getLogger("").info("Entered LagService.getLag")
        val pc = PairCollector()
        Logging.getLogger("").info("Finished pair collector caching after ${(System.currentTimeMillis() - startTime)/1000} seconds")

        val fixed = pc.getPairs(pm, PairCollector.Status.INCLUDED)
        val flexible = pc.getPairs(pm, PairCollector.Status.FLEXIBLE)

        runBlocking {
            fixed.forEach {
                async {
                    processPair(it)
                }
            }
            flexible.forEach {
                async {
                    processPair(it)
                }
            }
        }

        Logging.getLogger("").info("Finished lag service for $pm after ${(System.currentTimeMillis() - startTime)/1000} seconds")
        return stats
    }

    suspend fun processPair(pair: PairIDs) {
        val projectHistory = Database.getProjectHistoryFlexible(pair)
        val dependencyHistory = Database.getDepHistory(pair)

        val localStats = ConcurrentHashMap<String, MutableMap<MatcherResult, Int>>()

        projectHistory.forEach { version ->
            val newestDependency = dependencyHistory.filter { it.time < version.time }.maxBy { it.time }
            val classification = VersionCategoryWrapper.getClassification(pm.toString(), version.dependency)
            val matchResult = LagCheckingService.matcher(pm, newestDependency?.version, classification, version.dependency)

            localStats.putIfAbsent(classification, mutableMapOf<MatcherResult, Int>())
            localStats[classification]!!.putIfAbsent(matchResult, 0)
//            localStats[classification]!![matchResult]!!++
        }

//        stats.getOrDefault(classification, HashMap()).compute(matchResult) {  }
    }
}
