package masters;

import java.sql.*;

import masters.dataClasses.*;
import utils.Database;

import org.apache.log4j.Logger;

/**
 * Created by Jacob Stringer on 28/01/2019.
 */
public class FindProjectVersions {

    public static void main(String[] args) throws Exception {
        Connection c = Database.getConnection();
        Logger log = Logging.getLogger("FindProjectVersions");

        System.out.println("Connection ready");
        c.setAutoCommit(false);
        Statement stmt = c.createStatement();
        stmt.setFetchSize(1000);
        ResultSet rs = stmt.executeQuery("select * from dependencies where platform = 'Maven'");
        System.out.println("DB loaded");

        Results results = new Results(log);
        results.consumeResults(rs);
        results.compareProjects();
        results.printProjectResults();
    }

}


