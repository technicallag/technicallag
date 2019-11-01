package masters

import masters.dataClasses.VersionRelationship

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

class ProcessPairNew(val pair: PairWithData) {

    val classifyUpdates = mutableListOf<Change>()

    init {
        classifyChanges()
    }

    private fun classifyChanges() {
        pair.aVersions.sort()
        for ((first, second) in pair.aVersions.zipWithNext()) {
            val versionRel = first.version.getRelationship(second.version)
            val projChangeType: Update = Update.mapVRtoUpdate(versionRel)

            val depRel = first.dependency?.getRelationship(second.dependency) ?: continue
            val depChangeType: Update = if (first.dependency.compareTo(second.dependency) > 0)
                Update.mapVRtoUpdateBackwards(depRel) else
                Update.mapVRtoUpdate(depRel)

            classifyUpdates.add(Change(projChangeType, depChangeType))
        }
    }


}

class VersionTree()