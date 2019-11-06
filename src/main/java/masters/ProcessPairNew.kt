package masters

import masters.dataClasses.VersionRelationship
import masters.utils.Database
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

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
}

data class PairStatistics(val pair: PairWithData, val pm: PairCollector.PackageManager) {
    val classifyUpdates = mutableListOf<Change>()
    val quantityOfLag = mutableListOf<Lag>()

    fun printToFile(filepath: String) {
        val aName = Database.getProjectName(pair.pairIDs.projectID)
        val bName = Database.getProjectName(pair.pairIDs.dependencyID)

        BufferedWriter(FileWriter(filepath)).use {
            it.write("dA\\dB")
            Update.values().forEach { update -> it.write(",$update") }
            Update.values().forEach { update ->
                it.write("\n$update,")
                Update.values().forEach { updateinner ->
                    it.write(classifyUpdates.count { classified -> classified == Change(update, updateinner) }.toString() + ",")
                }
            }

            it.write("\n\nLag (Vers Behind), Major, Minor, Micro\n")
            quantityOfLag.forEach { lag -> it.write(",$lag\n") }

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
            pair.aVersions.sortBy { it.version }

            val stats = PairStatistics(pair, pm)
            classifyChanges(stats)
            quantifyLag(stats)
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

                stats.classifyUpdates.add(Change(projChangeType, depChangeType))
            }
        }

        private fun quantifyLag(stats: PairStatistics) {
            for (version in stats.pair.aVersions) {
                if (version.dependency == null) continue

                val newerVersionsWithType = stats.pair.bVersions
                        .filter { it.time < version.time }
                        .filter { it.version > version.dependency }

                val newestSameMinor = newerVersionsWithType.filter { it.version.sameMinor(version.dependency) }.maxBy { it.version }
                val newestSameMajor = newerVersionsWithType.filter { it.version.sameMajor(version.dependency) }.maxBy { it.version }
                val newestVersion = newerVersionsWithType.maxBy { it.version }

                stats.quantityOfLag.add(Lag(
                        (newestVersion?.version?.major ?: version.dependency.major) - version.dependency.major,
                        (newestSameMajor?.version?.minor ?: version.dependency.minor) - version.dependency.minor,
                        (newestSameMinor?.version?.micro ?: version.dependency.micro) - version.dependency.micro
                ))
            }
        }
    }
}