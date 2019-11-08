package masters

import masters.old.dataClasses.VersionRelationship
import masters.utils.Database
import java.io.BufferedWriter
import java.io.FileWriter
import java.lang.IllegalArgumentException

/**
 * Created by Jacob Stringer on 1/11/2019.
 */

enum class Update {
    FORWARDS_MAJOR,
    FORWARDS_MINOR,
    FORWARDS_MICRO,
    NO_CHANGE,
    BACKWARD_MAJOR,
    BACKWARD_MINOR,
    BACKWARD_MICRO;

    companion object {
        fun mapVRtoUpdate(vr: VersionRelationship) : Update = when(vr) {
            VersionRelationship.SAME_MICRO -> NO_CHANGE
            VersionRelationship.SAME_MINOR -> FORWARDS_MICRO
            VersionRelationship.SAME_MAJOR -> FORWARDS_MINOR
            VersionRelationship.DIFFERENT -> FORWARDS_MAJOR
        }

        fun mapVRtoUpdateBackwards(vr: VersionRelationship) : Update = when(vr) {
            VersionRelationship.SAME_MICRO -> NO_CHANGE
            VersionRelationship.SAME_MINOR -> BACKWARD_MICRO
            VersionRelationship.SAME_MAJOR -> BACKWARD_MINOR
            VersionRelationship.DIFFERENT -> BACKWARD_MAJOR
        }

        val size = values().size
    }
}

// What sort of version change is it in project A, and how does its dependency on B change?
data class Change (val projUpdateType: Update, val depUpdateType: Update)

// Counts the number of versions behind - it assumes semantic versioning so 2.3 and 2.9 are 6 minor versions apart, even if 2.4 were skipped
data class Lag (val major: Int, val minor: Int, val micro: Int) : Comparable<Lag> {
    override fun toString(): String {
        return "$major,$minor,$micro"
    }

    override fun compareTo(other: Lag): Int {
        if (this.major != other.major) return this.major - other.major
        if (this.minor != other.minor) return this.minor - other.minor
        return this.micro - other.micro
    }

    fun getLagType() : LagType {
        if (this.major > 0) return LagType.MAJOR
        else if (this.minor > 0) return LagType.MINOR
        else if (this.micro > 0) return LagType.MICRO
        else return LagType.NO_LAG
    }

    enum class LagType {
        MAJOR,
        MINOR,
        MICRO,
        NO_LAG
    }
}

data class UpdateMatrix (val index: Int) {
    companion object {
        val matrixLabels = arrayOf("Major Lag", "Minor Lag", "Micro Lag", "No Lag", "All Types")
    }

    val counts = Array(Update.size) { Array(Update.size) { 0.0 } }
    var total = 0.0
    var totalComputed = false

    fun includeChange(change: Change) {
        counts[change.projUpdateType.ordinal][change.depUpdateType.ordinal]++
    }

    fun combine(other: UpdateMatrix) : UpdateMatrix {
        val newMatrix = UpdateMatrix(index)
        repeat(Update.size) {i ->
            repeat(Update.size) { j ->
                newMatrix.counts[i][j] = this.counts[i][j] + other.counts[i][j]
            }
        }
        return newMatrix
    }

    fun addMatrix(other: UpdateMatrix) {
        repeat(Update.size) {i ->
            repeat(Update.size) { j ->
                this.counts[i][j] += other.counts[i][j]
            }
        }
    }

    fun normalizeMatrix() {
        totalComputed = true
        for (i in counts.indices) {
            for (j in counts[i].indices) {
                total += counts[i][j]
            }
        }

        for (i in counts.indices) {
            for (j in counts[i].indices) {
                counts[i][j] = counts[i][j] / total
            }
        }
    }

    override fun toString() : String {
        val sb = StringBuilder()
        val updates = Update.values()

        sb.append("${matrixLabels[index]}\n")
        sb.append("dA\\dB")
        updates.forEach { sb.append(",$it") }

        for (i in updates.indices) {
            sb.append("\n${updates[i]}")
            for (j in updates.indices) {
                sb.append(",%.4f".format(counts[i][j]))
            }
        }
        if (totalComputed) sb.append("\nTotal values: $total")

        sb.append("\n\n")
        return sb.toString()
    }
}

data class PairStatistics(val pair: PairWithData, val pm: PairCollector.PackageManager) {
    val classifyUpdates = mutableListOf<Change>()
    val quantityOfLag = mutableListOf<Lag>()
    val matrices = Array(4) { UpdateMatrix(it) }
    var missingDepsAtEnd = 0
    var missingDepsTotal = 0
    val hasThisUpdate = mutableSetOf<Update>()

    fun addUpdate(proj: Update, dep: Update) {
        classifyUpdates.add(Change(proj, dep))
        hasThisUpdate.add(dep)
    }

    fun processMatricesAndMissingDeps() {
        var latestDep = if (pair.aVersions[0].dependency == null) -1 else 0
        var curLag = latestDep
        var curUpdate = -1

        for (i in 1 until pair.aVersions.size) {
            val dep = pair.aVersions[i-1]
            val dep2 = pair.aVersions[i]

            if (dep2.dependency != null) {
                latestDep = i
                missingDepsAtEnd = 0

                try {
                    val lag = quantityOfLag[++curLag]
                    if (dep.dependency != null) {
                        val update = classifyUpdates[++curUpdate]
                        when (lag.getLagType()) {
                            Lag.LagType.MAJOR -> matrices[0].includeChange(update)
                            Lag.LagType.MINOR -> matrices[1].includeChange(update)
                            Lag.LagType.MICRO -> matrices[2].includeChange(update)
                            Lag.LagType.NO_LAG -> matrices[3].includeChange(update)
                        }
                    }
                } catch (e: IndexOutOfBoundsException) {
                    e.printStackTrace()
                    println("line $i")
                    this.printToFile("temp/${this.pair.pairIDs}")
                }

            }

            else if (latestDep > -1) {
                missingDepsTotal++
                missingDepsAtEnd++
            }
        }
    }

    fun printToFile(filepath: String) {
        val aName = Database.getProjectName(pair.pairIDs.projectID)
        val bName = Database.getProjectName(pair.pairIDs.dependencyID)

        BufferedWriter(FileWriter(filepath)).use {
            matrices.forEachIndexed { index, matrix ->
                it.write(matrix.toString())
            }

            it.write("\n\nLag (Vers Behind), Major, Minor, Micro\n")
            quantityOfLag.forEach { lag -> it.write(",$lag\n") }

            it.write("\nMissing Dependency Information")
            it.write("\nTotal missing deps: ${missingDepsTotal}")
            it.write("\nMissing deps at end: ${missingDepsAtEnd}")

            it.write("\nProject information")
            it.write("\n$aName (${pair.pairIDs.projectID})\t$bName (${pair.pairIDs.dependencyID})\tPackage Manager: $pm")

            it.write("\n\nProject A:")
            pair.aVersions.forEach {version ->
                it.write("\n$version")
                if (version.dependency != null)
                    it.write(",\tLatest, ${pair.bVersions
                            .filter { it.time < version.time }
                            .filter { it.version > version.dependency }
                            .maxBy { it.version }
                    }")
            }

            it.write("\n\nProject B:")
            pair.bVersions.forEach {version ->
                it.write("\n$version")
            }
        }
    }
}

class ProcessPairNew {

    companion object {
        @JvmStatic
        fun classifyPair(pair: PairWithData, pm: PairCollector.PackageManager) : PairStatistics {
            val stats = PairStatistics(pair, pm)

            try {
                pair.aVersions.sortBy { it.version }
            } catch(e: IllegalArgumentException) {
                println(pair.pairIDs)
                pair.aVersions.forEach { println(it) }
                return stats
            }

            classifyChanges(stats)
            quantifyLag(stats)
            stats.processMatricesAndMissingDeps()

            return stats
        }

        private fun classifyChanges(stats: PairStatistics) {
            for ((first, second) in stats.pair.aVersions.zipWithNext()) {
                val versionRel = first.version.getRelationship(second.version)
                val projChangeType: Update = Update.mapVRtoUpdate(versionRel)

                val depRel = first.dependency?.getRelationship(second.dependency) ?: continue
                val depChangeType: Update = if (first.dependency > second.dependency)
                    Update.mapVRtoUpdateBackwards(depRel) else
                    Update.mapVRtoUpdate(depRel)

                stats.addUpdate(projChangeType, depChangeType)
            }
        }

        private fun quantifyLag(stats: PairStatistics) {
            for (version in stats.pair.aVersions) {
                if (version.dependency == null) continue

                // Includes all DependencyVersion objects available at this time that have a higher version than the dependency specified
                val newerVersionsWithType = stats.pair.bVersions
                        .filter { it.time < version.time }
                        .filter { it.version > version.dependency }

                // Filters the available versions by major/minor/micro version and keeps one of each (accounts for gaps in versions)
                stats.quantityOfLag.add(Lag(
                        newerVersionsWithType.map { it.version.major }.filter { it > version.dependency.major }.toSet().size,
                        newerVersionsWithType.filter { it.version.sameMajor(version.dependency) }.map { it.version.minor }.filter { it > version.dependency.minor }.toSet().size,
                        newerVersionsWithType.filter { it.version.sameMinor(version.dependency) }.map { it.version.micro }.filter { it > version.dependency.micro }.toSet().size
                ))
            }
        }
    }
}