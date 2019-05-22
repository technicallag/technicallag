package masters;

import utils.Database;
import masters.dataClasses.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;
import utils.Logging;

public class DependencyUpdates {

    public static void main(String[] args) throws SQLException {
        Connection c = Database.getConnection();
        Logger log = Logging.getLogger("DependencyUpdates");

        log.info("Connection ready");
        c.setAutoCommit(false);
        Statement stmt = c.createStatement();
        stmt.setFetchSize(1000);
        ResultSet rs = stmt.executeQuery("select * from dependencies where platform = 'Maven'");
        log.info("DB loaded");

        Results results = new Results(log);
        results.consumeResults(rs);
        c.close();
        log.info("Results consumed");

        results.compareProjects();
        log.info("Projects compared");
//        results.getTimestamps(c);
//        log.info("Timestamps attained");
        results.checkDependenciesAreProjects();
        log.info("Got project pairs");
        results.constructTimeline();
        log.info("Timelines created");
    }

}
