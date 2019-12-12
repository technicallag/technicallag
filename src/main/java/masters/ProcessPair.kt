package masters

import masters.libiostudy.Version.VersionRelationship
import masters.utils.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat

/**
 * Created by Jacob Stringer on 1/11/2019.
 */

enum class Update {
    FORWARDS_MAJOR,
    FORWARDS_MINOR,
    FORWARDS_MICRO,
    NO_CHANGE,
    BACKWARD_MICRO,
    BACKWARD_MINOR,
    BACKWARD_MAJOR;

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
data class Lag (var major: Long, var minor: Long, var micro: Long, var majorTime: Long, var minorTime: Long, var microTime: Long) : Comparable<Lag> {
    override fun toString(): String {
        return "$major,$majorTime,$minor,$minorTime,$micro,$microTime"
    }

    fun header(): String {
        return "major,majorTime,minor,minorTime,micro,microTime"
    }

    override fun compareTo(other: Lag): Int {
        if (this.major != other.major) return (this.major - other.major).toInt()
        if (this.minor != other.minor) return (this.minor - other.minor).toInt()
        return (this.micro - other.micro).toInt()
    }

    fun getLagType() : Type {
        if (this.major > 0) {
            if (this.minor > 0) {
                if (this.micro > 0) return Type.MAJORMINORMICRO
                return Type.MAJORMINOR
            } else {
                if (this.micro > 0) return Type.MAJORMICRO
                return Type.MAJOR
            }
        }
        else if (this.minor > 0) {
            if (this.micro > 0) return Type.MINORMICRO
            return Type.MINOR
        }
        else if (this.micro > 0) return Type.MICRO
        else return Type.NO_LAG
    }

    fun addLag(other: Lag) {
        this.major += other.major
        this.minor += other.minor
        this.micro += other.micro
        this.majorTime += other.majorTime
        this.minorTime += other.minorTime
        this.microTime += other.microTime

        if (this.majorTime > 1e60 || this.minorTime > 1e60 || this.microTime > 1e60)
            Logging.getLogger("").warn("Lag is getting really large: $this")
    }

    enum class Type {
        MAJOR,
        MAJORMINOR,
        MAJORMICRO,
        MAJORMINORMICRO,
        MINOR,
        MINORMICRO,
        MICRO,
        NO_LAG
    }
}

data class UpdateMatrix (val index: Int) {
    companion object {
        val matrixLabels = arrayOf("Major Lag", "Major Minor Lag", "Major Micro Lag", "Major Minor Micro Lag", "Minor Lag", "Minor Micro Lag", "Micro Lag", "No Lag", "All Types")
    }

    val counts = Array(Update.size) { Array(Update.size) { 0.0 } }
    var total = 0

    fun includeChange(change: Change) {
        counts[change.projUpdateType.ordinal][change.depUpdateType.ordinal]++
        total++
    }

    fun addMatrix(other: UpdateMatrix) {
        repeat(Update.size) {i ->
            repeat(Update.size) { j ->
                this.counts[i][j] += other.counts[i][j]
            }
        }
        this.total += other.total
    }

    fun normalizeMatrix() : UpdateMatrix {
        val newMat = UpdateMatrix(index)
        newMat.total = this.total

        for (i in counts.indices) {
            for (j in counts[i].indices) {
                newMat.counts[i][j] = this.counts[i][j] / this.total
            }
        }
        return newMat
    }

    private fun getColSums() : Array<Double> {
        val sums = Array(Update.size) { 0.0 }
        repeat(Update.size) {i ->
            repeat(Update.size) { j ->
                sums[j] += this.counts[i][j]
            }
        }
        return sums
    }

    fun collapseColsToString() : String {
        return getColSums().joinToString(",") + ",$total"
    }

    fun collapseColsToStringNormalised() : String {
        return getColSums().map { it / this.total }.joinToString(",") { "%.4f".format(it) } + ",$total"
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

        sb.append("\nTotal values: $total")
        sb.append("\n\n")

        return sb.toString()
    }
}

data class PairStatistics(val pair: PairWithData, val pm: PairCollector.PackageManager) {
    val classifyUpdates = mutableListOf<Change>()
    val quantityOfLag = mutableListOf<Lag>()
    val matrices = Array(8) { UpdateMatrix(it) }
    var missingDepsAtEnd = 0
    var missingDepsTotal = 0
    val hasThisUpdate = mutableSetOf<Update>()

    val totalLag = Lag(0,0,0,0,0,0)
    val lagValues = mapOf("major" to mutableListOf<Long>(),
            "majorTime" to mutableListOf<Long>(),
            "minor" to mutableListOf<Long>(),
            "minorTime" to mutableListOf<Long>(),
            "micro" to mutableListOf<Long>(),
            "microTime" to mutableListOf<Long>())

    fun addUpdate(proj: Update, dep: Update) {
        classifyUpdates.add(Change(proj, dep))
        hasThisUpdate.add(dep)
    }

    fun hasBackwardsChanges() : Boolean {
        return this.numBackwardsUpdates() > 0
    }

    fun processMatricesAndMissingDeps() {
        var latestDep = if (pair.aVersions[0].dependency == null) -1 else 0
        var curLag = latestDep
        var curUpdate = -1

        for (lag in quantityOfLag) {
            lagValues["major"]?.add(lag.major)
            lagValues["majorTime"]?.add(lag.majorTime)
            lagValues["minor"]?.add(lag.minor)
            lagValues["minorTime"]?.add(lag.minorTime)
            lagValues["micro"]?.add(lag.micro)
            lagValues["microTime"]?.add(lag.microTime)
        }

        lagValues.forEach { it.value.sort() }

        for (i in 1 until pair.aVersions.size) {
            val dep = pair.aVersions[i-1]
            val dep2 = pair.aVersions[i]

            if (dep2.dependency != null) {
                latestDep = i
                missingDepsAtEnd = 0

                try {
                    val lag = quantityOfLag[++curLag]
                    totalLag.addLag(lag)

                    if (dep.dependency != null) {
                        val update = classifyUpdates[++curUpdate]
                        when (lag.getLagType()) {
                            Lag.Type.MAJOR -> matrices[0].includeChange(update)
                            Lag.Type.MAJORMINOR -> matrices[1].includeChange(update)
                            Lag.Type.MAJORMICRO -> matrices[2].includeChange(update)
                            Lag.Type.MAJORMINORMICRO -> matrices[3].includeChange(update)
                            Lag.Type.MINOR -> matrices[4].includeChange(update)
                            Lag.Type.MINORMICRO -> matrices[5].includeChange(update)
                            Lag.Type.MICRO -> matrices[6].includeChange(update)
                            Lag.Type.NO_LAG -> matrices[7].includeChange(update)
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

        // There should always be some lag - this should be what is causing the next section to trip up
        if (quantityOfLag.size == 0) {
            Logging.getLogger("").warn(pair.toString() + " has no recorded lag information")
            this.printToFile("data/pairwiseResults/no_lag_pairs/${pm}_${pair.pairIDs.projectID}_${pair.pairIDs.dependencyID}.csv")
            return
        }

        // Investigate numbers for lags over 30 years old! Ugly hacking code :(
        try {
            if (lagValues["majorTime"]?.last() ?: 0 > 10000) {
                this.printToFile("data/long_time_lags/${pm}_${pair.pairIDs.projectID}_${pair.pairIDs.dependencyID}.csv")
                return
            }
        } catch (e: NoSuchElementException) { e.printStackTrace() }
        try {
            if (lagValues["minorTime"]?.last() ?: 0 > 10000) {
                this.printToFile("data/long_time_lags/${pm}_${pair.pairIDs.projectID}_${pair.pairIDs.dependencyID}.csv")
                return
            }
        } catch (e: NoSuchElementException) { e.printStackTrace() }
        try {
            if (lagValues["microTime"]?.last() ?: 0 > 10000) {
                this.printToFile("data/long_time_lags/${pm}_${pair.pairIDs.projectID}_${pair.pairIDs.dependencyID}.csv")
                return
            }
        } catch (e: NoSuchElementException) { e.printStackTrace() }
    }

    fun printBackwardsInformation(filepath: String) {
        File(filepath).mkdirs()

        val aName = Database.getProjectName(pair.pairIDs.projectID)
        val bName = Database.getProjectName(pair.pairIDs.dependencyID)

        BufferedWriter(FileWriter("$filepath/${pm}_${pair.pairIDs.projectID}_${pair.pairIDs.dependencyID}")).use {
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
                it.write(Database.getCommitInformation(pair.pairIDs.projectID, version.toString()).toString() + "\n")
            }

            it.write("\n\nProject B:")
            pair.bVersions.forEach {version ->
                it.write("\n$version")
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

            it.write(header() + "\n")
            it.write(this.toString() + "\n\n")

            it.write("\n\nLag (Vers Behind), Major, Days of Lag, Minor, Days of Lag, Micro, Days of Lag\n")
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

    private fun numBackwardsUpdates(): Int {
        return classifyUpdates.filter { it.depUpdateType == Update.BACKWARD_MAJOR || it.depUpdateType == Update.BACKWARD_MINOR || it.depUpdateType == Update.BACKWARD_MICRO }.size
    }

    fun header(): String {
        return "Project,Dependency" +
                ",#MajorLag,#MajorMinor,#MajorMicro,#MajorMinorMicro,#MinorLag,#MinorMicro,#MicroLag,#NoLag" +
                ",MAJOR DESCRIPTION," + descriptiveStatsHeader() + ",Avg" +
                ",MAJOR TIME DESCRIPTION," + descriptiveStatsHeader() + ",Avg" +
                ",MINOR DESCRIPTION," + descriptiveStatsHeader() + ",Avg" +
                ",MINOR TIME DESCRIPTION," + descriptiveStatsHeader() + ",Avg" +
                ",MICRO DESCRIPTION," + descriptiveStatsHeader() + ",Avg" +
                ",MICRO TIME DESCRIPTION," + descriptiveStatsHeader() + ",Avg" +
                ",${Update.values().joinToString(",")},missingDepsEnd,missingDepsTotal"
    }

    override fun toString(): String {
        val updateCounts = mutableListOf<Int>()
        Update.values().forEach { update -> updateCounts.add(classifyUpdates.filter { it.depUpdateType == update }.size) }

        return "${pair.pairIDs.projectID},${pair.pairIDs.dependencyID}" +
                ",${matrices[0].total},${matrices[1].total},${matrices[2].total},${matrices[3].total},${matrices[4].total},${matrices[5].total},${matrices[6].total},${matrices[7].total}" +
                ",,${descriptiveStats(lagValues["major"])},%.4f".format(totalLag.major.toDouble() / quantityOfLag.size) +
                ",,${descriptiveStats(lagValues["majorTime"])},%.4f".format(totalLag.majorTime.toDouble() / quantityOfLag.size) +
                ",,${descriptiveStats(lagValues["minor"])},%.4f".format(totalLag.minor.toDouble() / quantityOfLag.size) +
                ",,${descriptiveStats(lagValues["minorTime"])},%.4f".format(totalLag.minorTime.toDouble() / quantityOfLag.size) +
                ",,${descriptiveStats(lagValues["micro"])},%.4f".format(totalLag.micro.toDouble() / quantityOfLag.size) +
                ",,${descriptiveStats(lagValues["microTime"])},%.4f".format(totalLag.microTime.toDouble() / quantityOfLag.size) +
                ",${updateCounts.joinToString(",")},$missingDepsAtEnd,$missingDepsTotal"
    }
}

class ProcessPair {

    companion object {
        @JvmStatic
        fun classifyPair(pair: PairWithData, pm: PairCollector.PackageManager) : PairStatistics {
            val stats = PairStatistics(pair, pm)

            try {
                pair.aVersions.sortBy { it.version }
            } catch(e: IllegalArgumentException) {
                Logging.getLogger("").error(pair.pairIDs.toString() + " has an issue with the comparator not being transitive - ProcessPair.classifyPair")
                return stats
            }

            classifyChanges(stats)
            quantifyLag(stats)
            stats.processMatricesAndMissingDeps()

            return stats
        }

        private fun getPreviousVersion(stats: PairStatistics, second: ProjectVersion) : Update {
            val prevVersionChronologically = stats.pair.aVersions
                    .filter { it.time < second.version.time }
                    .maxBy { it.time }

            // It is possible that this version is the first one in the timeline
            val depRel = prevVersionChronologically?.dependency?.getRelationship(second.dependency) ?: return Update.NO_CHANGE
            return if (prevVersionChronologically.dependency > second.dependency) {
                Update.mapVRtoUpdateBackwards(depRel)
            } else {
                Update.mapVRtoUpdate(depRel)
            }
        }

        private fun classifyChanges(stats: PairStatistics) {
            for ((first, second) in stats.pair.aVersions.zipWithNext()) {
                val versionRel = first.version.getRelationship(second.version)
                val projChangeType: Update = Update.mapVRtoUpdate(versionRel)

                val depRel = first.dependency?.getRelationship(second.dependency) ?: continue
                val depChangeType: Update = if (first.dependency > second.dependency) {
                    // Sometimes later versions 1.1.0 can be published before earlier versions 1.0.4
                    if (second.version.time < first.version.time) {
                        getPreviousVersion(stats, second)
                    } else {
                        Update.mapVRtoUpdateBackwards(depRel)
                    }
                } else {
                    Update.mapVRtoUpdate(depRel)
                }

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

                //newerVersionsWithType.maxBy { it.time }?.time - version.time


                // Filters the available versions by major/minor/micro version and keeps one of each (accounts for gaps in versions)
                stats.quantityOfLag.add(
                    Lag(
                        newerVersionsWithType
                                .map { it.version.major }
                                .filter { it > version.dependency.major }
                                .toSet().size.toLong(),
                        newerVersionsWithType
                                .filter { it.version.sameMajor(version.dependency) }
                                .map { it.version.minor }
                                .filter { it > version.dependency.minor }
                                .toSet().size.toLong(),
                        newerVersionsWithType
                                .filter { it.version.sameMinor(version.dependency) }
                                .map { it.version.micro }
                                .filter { it > version.dependency.micro }
                                .toSet().size.toLong(),
                        getDaysLag(version, newerVersionsWithType
                                .filter { it.version.major > version.dependency.major }
                                .minBy { it.version }),
                        getDaysLag(version, newerVersionsWithType
                                .filter { it.version.sameMajor(version.dependency) && it.version.minor > version.dependency.minor }
                                .minBy { it.version }),
                        getDaysLag(version, newerVersionsWithType
                                .filter { it.version.sameMinor(version.dependency) && it.version.micro > version.dependency.micro }
                                .minBy { it.version })
                    )
                )
            }
        }
    }
}