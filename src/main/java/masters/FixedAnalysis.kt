package masters

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import masters.CollectDataForPair.Companion.collectDataForFixedAnalysis
import masters.FixedPairAnalysis.Companion.classifyPair
import masters.utils.Logging

/**
 * Created by Jacob Stringer on 16/12/2019.
 */

fun analysePairs() {
    val pairCollector = PairCollector()
    val allData = Aggregator("ALL")
    val allLargePairs = Aggregator("ALL_LARGE_PAIRS")

    for (pm in PackageManager.values()) {
        val pairsByPM = pairCollector.getPairs(pm, PairCollector.Status.INCLUDED)

        Logging.getLogger("").info("Aggregating data for $pm")
        val aggregator = Aggregator(pm.toString())
        val largeVersionHistoryAggregator = Aggregator(pm.toString() + "_A10PLUS_B10PLUS")

        runBlocking {
            for (pairID in pairsByPM) {
                async {
                    val data = collectDataForFixedAnalysis(pairID)
                    if (data.aVersions.isEmpty()) return@async

                    val ps = classifyPair(data, pm)
                    maybePrint(ps, pm)

                    aggregator.addStatistics(ps)
                    if (data.aVersions.size > 10 && data.bVersions.size > 10) largeVersionHistoryAggregator.addStatistics(ps)
                }
            }
        }

        aggregator.printAggregator()
        largeVersionHistoryAggregator.printAggregator()
        allData.addAggreator(aggregator)
        allLargePairs.addAggreator(largeVersionHistoryAggregator)
    }
    allData.printAggregator()
}

// Toggle which pairs get individually printed for validation
fun maybePrint(ps: PairStatistics, pm: PackageManager) {
    if (ps.hasBackwardsChanges()) ps.printBackwardsInformation("data/backwards/$pm")
}