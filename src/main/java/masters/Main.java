package masters;

import masters.utils.Database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

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
        Logger log = Logging.getLogger("Trialling new memory management model - Downloading all package manager pair information simultaneously");
        Main main = new Main(c, log);
        main.findProjectPairs();
        Database.closeConnections();
//        main.createTimelineInformation();
    }

    private void findProjectPairs() {
        long start = System.currentTimeMillis();
        log.info("Beginning collection of pairs");
        PairCollector finder = new PairCollector(null);

        Map<PairCollector.PackageManager, Collection<PairIDs>> pairsByPM = finder.getAvailablePairs();
        Map<PairCollector.PackageManager, Map<PairCollector.Status, Integer>> statsByPM = finder.getCounts();

        printPairInfoToCSV(statsByPM);
        log.debug("It took: " + (System.currentTimeMillis() - start) + " ms to get the filtered pairs");

        start = System.currentTimeMillis();
        for (PairCollector.PackageManager pm: PairCollector.PackageManager.values()) {
            pairsByPM.get(pm).forEach(PairWithData::new);
        }
        log.debug("It took " + (System.currentTimeMillis() - start) + " ms to collect the pair data");
    }

    private void printPairInfoToCSV(Map<PairCollector.PackageManager, Map<PairCollector.Status, Integer>> statsByPM) {
        try(BufferedWriter out = new BufferedWriter(new FileWriter(new File("data/pair_info.csv")))) {
            // Headers
            out.write(",");
            for (PairCollector.Status status: PairCollector.Status.values()) { out.write(status + ","); }
            out.write("\n");

            // Data, one package manager at a time
            for (PairCollector.PackageManager pm: PairCollector.PackageManager.values()) {
                out.write(pm + ",");
                for (PairCollector.Status status: PairCollector.Status.values()) {
                    out.write(statsByPM.get(pm).get(status) + ",");
                }
                out.write("\n");
            }
        } catch(IOException e) {
            log.error(e);
        }
    }

    private void createTimelineInformation() throws SQLException {
        getDataStructuresReady();
        results.checkDependenciesAreProjects();
        results.constructTimeline();
    }

    private void proofOfConceptFindProjectVersions() throws SQLException {
        getDataStructuresReady();
        results.printProjectResults();
    }

    private void getDataStructuresReady() throws SQLException {
        ResultSet rs = Database.runQuery("select * from dependencies where platform = 'Maven'");
        results.consumeResults(rs);
        c.close();
        results.compareProjects();
    }

}
