package masters;

import masters.utils.Database;

import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.apache.log4j.Logger;
import masters.utils.Logging;

public class Main {

    Connection c;
    Logger log;
    Results results;
    HashSet<PairIDs> alreadyPrinted;

    public Main(Connection c, Logger log) {
        this.c = c;
        this.log = log;
        this.results = new Results();
        this.alreadyPrinted = getPrintedHistory();
    }

    public static void main(String[] args) throws SQLException {
        Connection c = Database.getConnection();
        Logger log = Logging.getLogger("New memory model working - saving new files each time");
        Main main = new Main(c, log);
        main.findProjectPairs();
        main.savePrintedHistory();
        Database.closeConnections();
//        main.createTimelineInformation();
    }

    private void findProjectPairs() {
        long start = System.currentTimeMillis();
        log.info("Beginning collection of pairs");
        PairCollector finder = new PairCollector();

        Map<PairCollector.PackageManager, List<PairIDs>> pairsByPM = finder.getAvailablePairs();
        log.debug("It took: " + (System.currentTimeMillis() - start) + " ms to get the filtered pairs");

        start = System.currentTimeMillis();

        Random rand = new Random();

        for (PairCollector.PackageManager pm: PairCollector.PackageManager.values()) {
            for (PairIDs pairID: pairsByPM.get(pm)) {
                // Print with 0.00001 probability for Mvn/NPM, else 0.001 probability
                boolean MVN_NPM = pm == PairCollector.PackageManager.NPM || pm == PairCollector.PackageManager.MAVEN;
                if (MVN_NPM && rand.nextInt(100000) == 1 || !MVN_NPM && rand.nextInt(1000) == 1) {
                    PairStatistics ps = ProcessPairNew.classifyPair(CollectDataForPair.collectData(pairID), pm);
                    print(ps);
                }
            }
        }
        log.debug("It took " + (System.currentTimeMillis() - start) + " ms to collect the pair data and process it");
    }

    private HashSet<PairIDs> getPrintedHistory() {
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream("data/pairwiseResults/printhist.bin"));
            HashSet<PairIDs> result = (HashSet<PairIDs>)in.readObject();
            in.close();
            return result;
        } catch (Exception e) {
            log.warn(e);
            return new HashSet<>();
        }
    }

    private void savePrintedHistory() {
        try {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("data/pairwiseResults/printhist.bin"));
            out.writeObject(alreadyPrinted);
            out.close();
        } catch (IOException e) {
            log.warn(e);
        }
    }

    private void print(PairStatistics ps) {
        PairIDs pair = ps.getPair().getPairIDs();
        if (alreadyPrinted.contains(pair)) return;

        new File("data/pairwiseResults").mkdirs();
        ps.printToFile(String.format("data/pairwiseResults/%s_%d_%d.csv", ps.getPm().toString(), pair.component1(), pair.component2()));
        alreadyPrinted.add(pair);
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
        ResultSet rs = Database.runQueryNoLogs("select * from dependencies where platform = 'Maven'");
        results.consumeResults(rs);
        c.close();
        results.compareProjects();
    }

}
