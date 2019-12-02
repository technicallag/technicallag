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
    var projectCounter = 0L
    var lagCounter = 0L

    val totalLag = Lag(0,0,0,0,0,0)

    val pairStats = mutableListOf<String>()

    fun addStatistics(stats: PairStatistics) {
        projectCounter++
        lagCounter += stats.quantityOfLag.size

        totalLag.addLag(stats.totalLag)
        stats.hasThisUpdate.forEach { ge1Update[it] = ge1Update.getOrDefault(it, 0) + 1 }

        for (index in 0 until matrices.size - 1) {
            matrices[index].addMatrix(stats.matrices[index])
        }

        val missingDeps = stats.missingDepsTotal - stats.missingDepsAtEnd
        depsMissingAtEnd[stats.missingDepsAtEnd] = depsMissingAtEnd.getOrDefault(stats.missingDepsAtEnd, 0) + 1
        depsMissingInMiddle[missingDeps] = depsMissingInMiddle.getOrDefault(missingDeps, 0) + 1
        projectsWithoutChangeObjects += if (stats.classifyUpdates.size == 0) 1 else 0

        if (pairStats.size == 0) pairStats.add(stats.header())
        pairStats.add(stats.toString())
    }

    // Note pairStats are not added when combining aggregators currently (performance concerns)
    fun addAggreator(other: Aggregator) {
        projectCounter += other.projectCounter
        projectsWithoutChangeObjects += other.projectsWithoutChangeObjects

        lagCounter += other.lagCounter
        totalLag.addLag(other.totalLag)

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
                out.write(it.toString())
                out.write("Normalised:\n")
                out.write(it.normalizeMatrix().toString())
            }

            out.write("Average lag:\n")
            out.write("${totalLag.header()}\n")
            out.write("${totalLag.averaged(lagCounter)}\n\n")

            out.write("\nDependencies missing at end: \n${depsMissingAtEnd.toSortedMap()}\n")
            out.write("\nDependencies missing in the middle: \n${depsMissingInMiddle.toSortedMap()}\n")
            out.write("\nTypes of changes found at least once in the pair history: \n${ge1Update.toSortedMap()}\n")
            out.write("\nPairs without any change objects at all (i.e. they did not have two contiguous versions with a dependency to B): $projectsWithoutChangeObjects\n")
            out.write("\nTotal pairs considered: $projectCounter")
        }

        if (pairStats.size > 0)
            BufferedWriter(FileWriter("data/aggregations/${name}_individualProjectStats.csv")).use {out ->
                pairStats.forEach { out.write(it); out.write("\n") }
            }

        Logging.getLogger("").info("$name aggregator has been printed")
    }

}