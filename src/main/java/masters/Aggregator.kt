package masters

import masters.utils.Logging
import java.io.BufferedWriter
import java.io.FileWriter

/**
 * Created by Jacob Stringer on 7/11/2019.
 */


class Aggregator(val name: String) {

    val matrices = Array(5) { UpdateMatrix(it) }
    val depsMissingAtEnd = mutableMapOf<Int, Int>()
    val depsMissingInMiddle = mutableMapOf<Int, Int>()
    val ge1Update = mutableMapOf<Update, Int>()
    var projectsWithoutChangeObjects = 0
    var counter = 0

    fun addStatistics(stats: PairStatistics) {
        counter++

        stats.hasThisUpdate.forEach { ge1Update[it] = ge1Update.getOrDefault(it, 0) + 1 }

        for (index in 0 until matrices.size - 1) {
            matrices[index].addMatrix(stats.matrices[index])
        }

        val missingDeps = stats.missingDepsTotal - stats.missingDepsAtEnd
        depsMissingAtEnd[stats.missingDepsAtEnd] = depsMissingAtEnd.getOrDefault(stats.missingDepsAtEnd, 0) + 1
        depsMissingInMiddle[missingDeps] = depsMissingInMiddle.getOrDefault(missingDeps, 0) + 1
        projectsWithoutChangeObjects += if (stats.classifyUpdates.size == 0) 1 else 0
    }

    fun addAggreator(other: Aggregator) {
        counter += other.counter
        projectsWithoutChangeObjects += other.projectsWithoutChangeObjects

        other.ge1Update.forEach { (update, int) ->
            ge1Update[update] = ge1Update.getOrDefault(update, 0) + int
        }

        other.depsMissingInMiddle.forEach { (numberDeps, counter) ->
            depsMissingInMiddle[numberDeps] = depsMissingInMiddle.getOrDefault(numberDeps, 0) + counter
        }

        other.depsMissingAtEnd.forEach { (numberDeps, counter) ->
            depsMissingAtEnd[numberDeps] = depsMissingAtEnd.getOrDefault(numberDeps, 0) + counter
        }

        matrices.forEachIndexed { index, updateMatrix ->
            updateMatrix.addMatrix(other.matrices[index])
        }
    }

    fun printAggregator() {
        BufferedWriter(FileWriter("data/aggregations/${name}.csv")).use {out ->
            for (i in 0 until matrices.size - 1) matrices[matrices.size-1].addMatrix(matrices[i])

            matrices.forEach {
                it.normalizeMatrix()
                out.write(it.toString())
            }
            out.write("\nDependencies missing at end: \n${depsMissingAtEnd.toSortedMap()}\n")
            out.write("\nDependencies missing in the middle: \n${depsMissingInMiddle.toSortedMap()}\n")
            out.write("\nTypes of changes found at least once in the pair history: \n${ge1Update.toSortedMap()}\n")
            out.write("\nPairs without any change objects at all (i.e. they did not have two contiguous versions with a dependency to B): $projectsWithoutChangeObjects\n")
            out.write("\nTotal pairs considered: $counter")
        }

        Logging.getLogger("").info("Finished writing aggregated results for $name")
    }
}