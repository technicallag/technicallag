package masters;

import utils.Database;
import masters.dataClasses.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

public class DependencyUpdates {

    public static void main(String[] args) throws SQLException, IOException {
        Connection c = Database.getConnection();
        Logger log = Logging.getLogger("DependencyUpdates");

        log.info("Connection ready");
        c.setAutoCommit(false);
        Statement stmt = c.createStatement();
        stmt.setFetchSize(1000);
        ResultSet rs = stmt.executeQuery("select * from dependencies where platform = 'Maven' fetch first 100 rows only");
        log.info("DB loaded");

        Results results = new Results(log);
        results.consumeResults(rs);
        log.info("Results consumed");
        results.compareProjects();
        log.info("Projects compared");
        results.getTimestamps(c);
        log.info("Timestamps attained");
        results.checkDependenciesAreProjects();
        log.info("Complete");

    }

}
