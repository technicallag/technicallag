package masters;

import masters.utils.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;
import masters.utils.Logging;

public class Main {

    Connection c;
    Logger log;
    Results results;

    public Main(Connection c, Logger log) {
        this.c = c;
        this.log = log;
        this.results = new Results();
    }

    public static void main(String[] args) throws SQLException {
        Connection c = Database.getConnection();
        Logger log = Logging.getLogger("CumulativeStats debugging BackwardsChanges simultaneous development issues");
        Main main = new Main(c, log);
        main.createTimelineInformation();
    }

    private void createTimelineInformation() throws SQLException {
        getDataStructuresReady();
        results.checkDependenciesAreProjects();
        results.constructTimeline();
    }

    private void getDataStructuresReady() throws SQLException {
        log.info("Connection ready");
        c.setAutoCommit(false);
        Statement stmt = c.createStatement();
        stmt.setFetchSize(1000);
        ResultSet rs = stmt.executeQuery("select * from dependencies where platform = 'Maven'");
        log.info("DB loaded");

        results.consumeResults(rs);
        c.close();
        results.compareProjects();
    }

    private void proofOfConceptFindProjectVersions() throws SQLException {
        getDataStructuresReady();
        results.printProjectResults();
    }

}
