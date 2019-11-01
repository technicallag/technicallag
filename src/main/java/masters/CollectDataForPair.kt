package masters

import masters.dataClasses.Version
import masters.utils.Database
import masters.utils.Logging
import java.lang.NullPointerException
import java.sql.SQLException
import java.sql.Timestamp

/**
 * Created by Jacob Stringer on 29/10/2019.
 */

data class ProjectVersion(val version: Version, val dependency: Version?, val time: String) : Comparable<ProjectVersion> {
    override fun compareTo(other: ProjectVersion) : Int = this.version.compareTo(other.version)
}

data class DependencyVersion(val version: Version, val time: String) : Comparable<DependencyVersion> {
    override fun compareTo(other: DependencyVersion) : Int = this.time.compareTo(other.time)
}

class PairWithData(val pairIDs: PairIDs) {

    val aVersions = ArrayList<ProjectVersion>()
    val bVersions = ArrayList<DependencyVersion>()

    init {
        try {
            getDepHistory()
            getProjectHistory()
        } catch (e: SQLException) {
            Logging.getLogger("").error(e)
        } catch (e: NullPointerException) {
            e.printStackTrace()
            println(pairIDs)
        }
    }

    private fun getDepHistory() {
        val rs = Database.runQueryNoLogs(
                "SELECT number, publishedtimestamp FROM versions WHERE projectid = ?;",
                pairIDs.dependencyID)

        while(rs.next()) {
            bVersions.add(DependencyVersion(Version.create(rs.getString("number")), rs.getString("publishedtimestamp")))
        }
    }

    private fun getProjectHistory() {
        val rs = Database.runQueryNoLogs(
                "SELECT number, publishedtimestamp, id FROM versions WHERE projectid = ?;",
                pairIDs.projectID)

        while(rs.next()) {
            val versionID = rs.getInt("id")
            val dependencyRS = Database.runQueryNoLogs(
                    "SELECT dependencyrequirements FROM dependencies WHERE projectid = ? AND dependencyprojectid = ? AND versionid = ?;",
                    pairIDs.projectID,
                    pairIDs.dependencyID,
                    versionID
            )

            aVersions.add(ProjectVersion(
                    Version.create(rs.getString("number")),
                    if (dependencyRS.next()) Version.create(dependencyRS.getString("dependencyrequirements")) else null,
                    rs.getString("publishedtimestamp"))
            )
        }
    }
}