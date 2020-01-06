package masters

import kotlinx.coroutines.*
import masters.PairCollector.PackageManager
import masters.PairCollector.Status
import masters.flexilag.LagCheckingService
import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import masters.libiostudy.VersionCategoryWrapper
import masters.utils.Database
import masters.utils.Logging
import java.io.File
import java.lang.StringBuilder
import java.util.*
import java.util.concurrent.atomic.LongAdder

/**
 * Created by Jacob Stringer on 12/12/2019.
 */

class FlexibleAnalysisByPM(val pm: PackageManager) {
    // stats[classification][matcherresult] = number_of_instances
    val stats: Vector<Array<Int>> = Vector()
    var counter = LongAdder()
    val log = Logging.getLogger("")
    val delimiter = "```"

    fun getLag() : Vector<Array<Int>> {
        val startTime = System.currentTimeMillis()
        Logging.getLogger("").info("Entered LagService.getLag for package manager $pm")
        val pc = PairCollector()
        Logging.getLogger("").info("Finished pair collector caching after ${(System.currentTimeMillis() - startTime)/1000} seconds")

        Classifications.ALL.forEach {
            stats.add(Array(MatcherResult.values().size) { 0 } )
        }

        val fixed = pc.getPairs(pm, Status.INCLUDED)
        val flexible = pc.getPairs(pm, Status.FLEXIBLE)
        val pairs = fixed + flexible

        // Coroutine that tracks how quickly pairs are being processed
        val daemon = GlobalScope.launch {
            while (true) {
                delay(10_000L)
                log.info("$counter pairs processed in the package manager $pm")
            }
        }

        if (pm == PackageManager.PACKAGIST)
            packagistLogic(pairs)
        else if (pm == PackageManager.ATOM || pm == PackageManager.NPM)
            npmLogic(pairs)
        else
            pairs.forEach {
                processPair(it)
                counter.increment()
            }

        daemon.cancel()
        Logging.getLogger("").info("Finished lag service for $pm after ${(System.currentTimeMillis() - startTime)/1000} seconds")
        return stats
    }

    fun npmLogic(pairs: List<PairIDs>) {
        dumpPairsToFile(pairs, "../flexible_study/${pm.toString().toLowerCase()}.txt")
        runNode("../flexible_study/${pm.toString().toLowerCase()}_results.txt")
        analyseOfflineFileResults("../flexible_study/${pm.toString().toLowerCase()}_results.txt")
    }

    fun dumpPairsToFile(pairs: List<PairIDs>, filename: String) {
        val file = File(filename)
        if (!file.exists()) {
            file.bufferedWriter().use { out ->
                for (pair in pairs)
                    out.write(collectPairInfoAsString(pair))
            }
        }
        log.info("Pair information written to file")
    }

    fun runNode(resultsfile: String) {
        if (File(resultsfile).exists())
            return

        try {
            val process = Runtime.getRuntime().exec("node \"src/main/java/masters/npm/offline${pm.toString().toLowerCase()}checker.js\"")
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                log.error("Node process has an exception with exit code $exitCode")
            } else {
                log.info("Semver.satisfies() checks completed in node")
            }
        } catch (e: InterruptedException) {
            log.error(e)
        }
    }

    fun analyseOfflineFileResults(filename: String) {
        var counter = 0
        File(filename).bufferedReader().use { input ->
            input.lines().forEach {
                try {
                    counter++
                    if(counter % 100_000 == 0)
                        log.info("$counter pair results analysed")

                    val results = it.split(delimiter)
                    if (results.size != 2) {
                        log.warn("$results doesn't have 2 elements")
                    } else {
                        stats[Classifications.ALL.indexOf(results[0])][if (results[1].toBoolean()) MatcherResult.MATCH.ordinal else MatcherResult.NO_MATCH.ordinal]++
                    }
                } catch (e: Exception) {
                    log.error(e)
                }
            }
        }
    }

    fun packagistLogic(pairs: List<PairIDs>) {
        // Run all pairs of projects (both flexible and fixed) except the following slow Packagist pairs
        val avoid = setOf(
                PairIDs(2335219, 49683),
                PairIDs(3310908, 1515029),
                PairIDs(3056579, 56408),
                PairIDs(2150186, 77807)
        )

        // Process each pair
        pairs.forEach {
            if (avoid.contains(it))
                return@forEach

            processPair(it)
            counter.increment()
        }
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

    fun collectPairInfoAsString(pair: PairIDs) : String {
        val projectHistory = Database.getProjectHistoryFlexible(pair)
        val dependencyHistory = Database.getDepHistory(pair)

        val builder = StringBuilder()

        projectHistory.forEach { version ->
            version.dependency ?: return@forEach

            val newestDependency = dependencyHistory.filter { it.time < version.time }.maxBy { it.time } ?: return@forEach
            val classification = VersionCategoryWrapper.getClassification(pm.toString(), version.dependency)

            builder.append(classification)
            builder.append(delimiter)
            builder.append(newestDependency.version)
            builder.append(delimiter)
            builder.append(version.dependency)
            builder.append("\n")
        }

        return builder.toString()
    }
}
