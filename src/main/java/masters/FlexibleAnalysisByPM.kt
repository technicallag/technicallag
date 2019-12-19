package masters

import kotlinx.coroutines.*
import masters.flexilag.LagCheckingService
import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import masters.libiostudy.VersionCategoryWrapper
import masters.utils.Database
import masters.utils.Logging
import java.util.*
import java.util.concurrent.atomic.LongAdder

/**
 * Created by Jacob Stringer on 12/12/2019.
 */

class FlexibleAnalysisByPM(val pm: PairCollector.PackageManager) {
    // stats[classification][matcherresult] = number_of_instances
    val stats: Vector<Array<Int>> = Vector()
    var counter = LongAdder()
    val log = Logging.getLogger("")

    fun getLag() : Vector<Array<Int>> {
        val startTime = System.currentTimeMillis()
        Logging.getLogger("").info("Entered LagService.getLag for package manager $pm")
        val pc = PairCollector()
        Logging.getLogger("").info("Finished pair collector caching after ${(System.currentTimeMillis() - startTime)/1000} seconds")

        Classifications.ALL.forEach {
            stats.add(Array(MatcherResult.values().size) { 0 } )
        }

        val fixed = pc.getPairs(pm, PairCollector.Status.INCLUDED)
        val flexible = pc.getPairs(pm, PairCollector.Status.FLEXIBLE)

        // Coroutine that tracks how quickly pairs are being processed
        GlobalScope.launch {
            while (true) {
                delay(10_000L)
                log.info("$counter pairs processed in the package manager $pm")
            }
        }

        // Run all pairs of projects (both flexible and fixed) except the following slow Packagist pairs
        val avoid = setOf(
            PairIDs(2335219, 49683),
            PairIDs(3310908, 1515029),
            PairIDs(3056579, 56408),
            PairIDs(2150186, 77807)
        )

        // Process each pair
        (fixed + flexible).forEach {
            if (avoid.contains(it))
                return@forEach

            processPair(it)
            counter.increment()
        }

        Logging.getLogger("").info("Finished lag service for $pm after ${(System.currentTimeMillis() - startTime)/1000} seconds")
        return stats
    }

    fun processPair(pair: PairIDs) {
        val projectHistory = Database.getProjectHistoryFlexible(pair)
        val dependencyHistory = Database.getDepHistory(pair)

        val localStats = Array(Classifications.ALL.size) { Array(MatcherResult.values().size) { 0 } }

        projectHistory.forEach { version ->
            version.dependency ?: return@forEach

            val newestDependency = dependencyHistory.filter { it.time < version.time }.maxBy { it.time } ?: return@forEach
            val classification = VersionCategoryWrapper.getClassification(pm.toString(), version.dependency)
            val matchResult = LagCheckingService.matcher(pm, newestDependency.version, classification, version.dependency)

            localStats[Classifications.ALL.indexOf(classification)][matchResult.ordinal]++
        }

        // Combine local results with main results
        for (i in Classifications.ALL.indices) {
            for (j in MatcherResult.values().indices) {
                stats[i][j] += localStats[i][j]
            }
        }
    }
}
