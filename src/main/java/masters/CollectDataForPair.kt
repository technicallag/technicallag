package masters

import masters.utils.Database
import masters.utils.Logging
import java.lang.NullPointerException
import java.sql.SQLException
import java.sql.Timestamp

/**
 * Created by Jacob Stringer on 29/10/2019.
 */

data class ProjectVersion(val version: String, val dependency: String?, val time: Timestamp)
data class DependencyVersion(val version: String, val time: Timestamp)

class PairWithData(pairIDs: PairIDs) {

    val pairIDs: PairIDs = pairIDs
    val aVersions = ArrayList<ProjectVersion>()
    val bVersions = ArrayList<DependencyVersion>()

    init {
        // Download version history of the dependency
        try {
            var rs = Database.runQueryNoLogs("""
                SELECT number, publishedtimestamp
                FROM versions
                WHERE projectid = '${pairIDs.dependencyID}';
            """.trimIndent())

            while(rs.next()) {
                bVersions.add(DependencyVersion(rs.getString("number"), rs.getTimestamp("publishedtimestamp")))
            }

            // Download version history of the main project
            rs = Database.runQueryNoLogs("""
                SELECT number, publishedtimestamp, id
                FROM versions
                WHERE projectid = '${pairIDs.projectID}';
            """.trimIndent())

            while(rs.next()) {
                val versionID = rs.getInt("id")
                val dependencyRS = Database.runQueryNoLogs("""
                SELECT dependencyrequirements
                FROM dependencies
                WHERE projectid = '${pairIDs.projectID}' AND dependencyprojectid = '${pairIDs.dependencyID}' AND versionid = '$versionID';
            """.trimIndent())

                aVersions.add(ProjectVersion(rs.getString("number"), if (dependencyRS.next()) dependencyRS.getString("dependencyrequirements") else null, rs.getTimestamp("publishedtimestamp")))
            }
        } catch (e: SQLException) {
            Logging.getLogger("").error(e)
        } catch (e: NullPointerException) {
            e.printStackTrace()
            println(pairIDs)
        }
    }


}