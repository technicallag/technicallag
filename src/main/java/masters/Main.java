package masters;

import masters.utils.Database;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

import org.apache.log4j.Logger;
import masters.utils.Logging;

public class Main {

    Logger log;
    HashSet<PairIDs> alreadyPrinted;

    public Main(Logger log) {
        this.log = log;
        this.alreadyPrinted = getPrintedHistory();
    }

    public static void main(String[] args) throws SQLException {
        Logger log = Logging.getLogger("New memory model working - saving new files each time");
        Main main = new Main(log);
        //main.printSomeProjectPairs();
        main.aggregateData();
        main.savePrintedHistory();
        Database.closeConnections();
    }

    private void aggregateData() {
        Map<PairCollector.PackageManager, List<PairIDs>> pairsByPM = new PairCollector().getAvailablePairs();

        for (PairCollector.PackageManager pm: PairCollector.PackageManager.values()) {
            if (pm == PairCollector.PackageManager.MAVEN || pm == PairCollector.PackageManager.NPM) continue;

            log.info("Aggregating data for " + pm.toString());

            Aggregator aggregator = new Aggregator(pm.toString());
            for (PairIDs pairID: pairsByPM.get(pm)) {
                PairWithData data = CollectDataForPair.collectData(pairID);
                if (data.getAVersions().size() == 0) continue;

                PairStatistics ps = ProcessPairNew.classifyPair(data, pm);
                aggregator.addStatistics(ps);
            }
            aggregator.printAggregator();
        }
    }

    private void printSomeProjectPairs() {
        Map<PairCollector.PackageManager, List<PairIDs>> pairsByPM = new PairCollector().getAvailablePairs();

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
}
