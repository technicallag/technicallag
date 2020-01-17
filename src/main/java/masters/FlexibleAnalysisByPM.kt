package masters

import kotlinx.coroutines.*
import masters.PairCollector.PackageManager
import masters.PairCollector.Status
import masters.flexilag.LagCheckingService
import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import masters.libiostudy.Version
import masters.libiostudy.VersionCategoryWrapper
import masters.utils.Database
import masters.utils.Logging
import masters.utils.getDaysLag
import java.io.File
import java.io.Serializable
import java.lang.StringBuilder
import java.lang.UnsupportedOperationException
import java.util.*
import java.util.concurrent.atomic.LongAdder

/**
 * Created by Jacob Stringer on 12/12/2019.
 */

class FlexibleAnalysisByPM(val pm: PackageManager) : Serializable {
    // stats[classification][matcherresult] = number_of_instances
    @Transient var stats: Vector<Array<Int>> = Vector()
    @Transient var counter = LongAdder()
    @Transient var log = Logging.getLogger("")
    @Transient var delimiter = "```"

    val lagValueBuckets = mapOf("major" to mutableMapOf<Long, Int>(),
            "majorTime" to mutableMapOf<Long, Int>(),
            "minor" to mutableMapOf<Long, Int>(),
            "minorTime" to mutableMapOf<Long, Int>(),
            "micro" to mutableMapOf<Long, Int>(),
            "microTime" to mutableMapOf<Long, Int>())

    val lagtypes = Array(Lag.Type.values().size) { 0 }

    fun getLag(extraLag: Boolean = false) : Vector<Array<Int>> {
        if (stats.isNullOrEmpty()) {
            stats = Vector()
            counter = LongAdder()
            log = Logging.getLogger("")
            delimiter = "```"
        }

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

        val avoid = setOf(
                PairIDs(2335219, 49683),
                PairIDs(3310908, 1515029),
                PairIDs(3056579, 56408),
                PairIDs(2150186, 77807)
        )

        pairs.forEach {
            if (avoid.contains(it))
                return@forEach

            if (extraLag)
                processPairQuantifyLag(it)
            else
                processPair(it)
            counter.increment()
        }

//        if (pm == PackageManager.PACKAGIST)
//            packagistLogic(pairs)
//        else if (pm == PackageManager.ATOM || pm == PackageManager.NPM)
//            npmLogic(pairs)
//        else
//            pairs.forEach {
//                processPair(it)
//                counter.increment()
//            }

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

        try {
            projectHistory.forEach { version ->
                version.dependency ?: return@forEach

                val dependenciesPublished = dependencyHistory.filter { it.time < version.time }
                val newestDependency = dependenciesPublished.maxBy { it.time } ?: return@forEach
                val classification = VersionCategoryWrapper.getClassification(pm.toString(), version.dependency)
                val matchResult = LagCheckingService.matcher(pm, newestDependency.version, classification, version.dependency)

                localStats[Classifications.ALL.indexOf(classification)][matchResult.ordinal]++
            }
        } catch (e: Exception) {
            log.error(e)
        }

        // Combine local results with main results
        for (i in Classifications.ALL.indices) {
            for (j in MatcherResult.values().indices) {
                stats[i][j] += localStats[i][j]
            }
        }
    }

    fun processPairQuantifyLag(pair: PairIDs) {
        val projectHistory = Database.getProjectHistoryFlexible(pair)
        val dependencyHistory = Database.getDepHistory(pair)

        val lags = mutableListOf<Lag>()

        try {
            projectHistory.forEach { version ->
                version.dependency ?: return@forEach

                try {
                    val dependenciesPublished = dependencyHistory.filter { it.time < version.time }
                    val classification = VersionCategoryWrapper.getClassification(pm.toString(), version.dependency)

                    val declaration = LagCheckingService.getDeclaration(pm, classification, version.dependency)

                    val orderedDependencies = dependenciesPublished.sortedByDescending { it.version }
                    val cannotBeUsed = mutableListOf<DependencyVersion>() // Dependencies newer in terms of version at time t
                    for (dep in orderedDependencies) {
                        if (declaration.matches(dep.version)) {
                            lags.add(getLag(version, dep.version, cannotBeUsed))
                            break
                        }
                        cannotBeUsed.add(dep)
                    }
                } catch (e2: UnsupportedOperationException) {
                    // We expect this. We just skip the version that can't have its declaration parsed
                }
            }
        } catch (e: Exception) {
            log.error(e)
        }

        for (lag in lags) {
            lagtypes[lag.getLagType().ordinal]++

            lagValueBuckets["major"]!!.putIfAbsent(lag.major, 0)
            lagValueBuckets["major"]!![lag.major] = lagValueBuckets["major"]!![lag.major]!! + 1

            lagValueBuckets["majorTime"]!!.putIfAbsent(lag.majorTime, 0)
            lagValueBuckets["majorTime"]!![lag.majorTime] = lagValueBuckets["majorTime"]!![lag.majorTime]!! + 1

            lagValueBuckets["minor"]!!.putIfAbsent(lag.minor, 0)
            lagValueBuckets["minor"]!![lag.minor] = lagValueBuckets["minor"]!![lag.minor]!! + 1

            lagValueBuckets["minorTime"]!!.putIfAbsent(lag.minorTime, 0)
            lagValueBuckets["minorTime"]!![lag.minorTime] = lagValueBuckets["minorTime"]!![lag.minorTime]!! + 1

            lagValueBuckets["micro"]!!.putIfAbsent(lag.micro, 0)
            lagValueBuckets["micro"]!![lag.micro] = lagValueBuckets["micro"]!![lag.micro]!! + 1

            lagValueBuckets["microTime"]!!.putIfAbsent(lag.microTime, 0)
            lagValueBuckets["microTime"]!![lag.microTime] = lagValueBuckets["microTime"]!![lag.microTime]!! + 1
        }
    }

    fun getLag(version: ContainsTime, leastLaggingVersion: Version, cannotBeUsed: List<DependencyVersion>) : Lag {
        return Lag(cannotBeUsed
                        .map { it.version.major }
                        .filter { it > leastLaggingVersion.major }
                        .toSet().size.toLong(),
                cannotBeUsed
                        .filter { it.version.sameMajor(leastLaggingVersion) }
                        .map { it.version.minor }
                        .filter { it > leastLaggingVersion.minor }
                        .toSet().size.toLong(),
                cannotBeUsed
                        .filter { it.version.sameMinor(leastLaggingVersion) }
                        .map { it.version.micro }
                        .filter { it > leastLaggingVersion.micro }
                        .toSet().size.toLong(),
                getDaysLag(version, cannotBeUsed
                        .filter { it.version.major > leastLaggingVersion.major }
                        .minBy { it.version }),
                getDaysLag(version, cannotBeUsed
                        .filter { it.version.sameMajor(leastLaggingVersion) && it.version.minor > leastLaggingVersion.minor }
                        .minBy { it.version }),
                getDaysLag(version, cannotBeUsed
                        .filter { it.version.sameMinor(leastLaggingVersion) && it.version.micro > leastLaggingVersion.micro }
                        .minBy { it.version })
        )
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
