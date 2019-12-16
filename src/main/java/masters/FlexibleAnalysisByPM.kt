package masters

import kotlinx.coroutines.*
import masters.PairCollector.PackageManager
import masters.flexilag.LagCheckingService
import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import masters.libiostudy.VersionCategoryWrapper
import masters.utils.Database
import masters.utils.Logging
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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
        Logging.getLogger("").info("Entered LagService.getLag")
        val pc = PairCollector()
        Logging.getLogger("").info("Finished pair collector caching after ${(System.currentTimeMillis() - startTime)/1000} seconds")

        Classifications.ALL.forEach {
            stats.add(Array(MatcherResult.values().size) { 0 } )
        }

        if (pm != PackageManager.MAVEN) return stats

        val fixed = pc.getPairs(pm, PairCollector.Status.INCLUDED)
        val flexible = pc.getPairs(pm, PairCollector.Status.FLEXIBLE)

        GlobalScope.launch {
            while (true) {
                delay(10_000L)
                log.trace("$counter pairs processed in the package manager $pm")
            }
        }

        runBlocking {
            fixed.forEach {
                async {
                    processPair(it)
                    counter.increment()
                }
            }
            flexible.forEach {
                async {
                    processPair(it)
                    counter.increment()
                }
            }
        }

        Logging.getLogger("").info("Finished lag service for $pm after ${(System.currentTimeMillis() - startTime)/1000} seconds")
        return stats
    }

    suspend fun processPair(pair: PairIDs) {
//        println("Entered processPair for $pair")

        val projectHistory = Database.getProjectHistoryFlexible(pair)
        val dependencyHistory = Database.getDepHistory(pair)

//        println("Fetched data for $pair")

        val localStats = Array(Classifications.ALL.size) { Array(MatcherResult.values().size) { 0 } }

        projectHistory.forEach { version ->
            version.dependency ?: return@forEach

            val newestDependency = dependencyHistory.filter { it.time < version.time }.maxBy { it.time }
            val classification = VersionCategoryWrapper.getClassification(pm.toString(), version.dependency)
            val matchResult = LagCheckingService.matcher(pm, newestDependency?.version, classification, version.dependency)

            localStats[Classifications.ALL.indexOf(classification)][matchResult.ordinal]++
        }

//        println("Finished crunching numbers for $pair")

        // Combine local results with main results
        for (i in Classifications.ALL.indices) {
            for (j in MatcherResult.values().indices) {
                stats[i][j] += localStats[i][j]
            }
        }

        //println("Finished adding stats to the overarching counter for $pair. Exiting function.")
    }
}
