package masters;

import masters.utils.Database;

import java.io.*;
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

    public static void main(String[] args) {
        Logger log = Logging.getLogger("New memory model working - saving new files each time");
        Main main = new Main(log);
        main.aggregateData();
        main.savePrintedHistory();
        Database.closeConnections();
    }

    private void aggregateData() {
        Map<PairCollector.PackageManager, List<PairIDs>> pairsByPM = new PairCollector().getAvailablePairs();
        Aggregator allData = new Aggregator("ALL");

        for (PairCollector.PackageManager pm: PairCollector.PackageManager.values()) {
            if (pm == PairCollector.PackageManager.MAVEN || pm == PairCollector.PackageManager.NPM) continue;

            log.info("Aggregating data for " + pm.toString());
            Aggregator aggregator = new Aggregator(pm.toString());
            Aggregator largeVersionHistoryAggregator = new Aggregator(pm.toString() + "_A10PLUS_B10PLUS");

            for (PairIDs pairID: pairsByPM.get(pm)) {
                PairWithData data = CollectDataForPair.collectData(pairID);
                if (data.getAVersions().size() == 0) continue;

                PairStatistics ps = ProcessPair.classifyPair(data, pm);
                maybePrint(ps, pm);
                aggregator.addStatistics(ps);
                if (data.getAVersions().size() > 10 && data.getBVersions().size() > 10)
                    largeVersionHistoryAggregator.addStatistics(ps);
            }

            aggregator.printAggregator();
            largeVersionHistoryAggregator.printAggregator();

            allData.addAggreator(aggregator);
        }
        allData.printAggregator();
    }

    // Memory for maybePrint
    int backwardsPrinted = 0;
    PairCollector.PackageManager curPM = null;
    private void maybePrint(PairStatistics ps, PairCollector.PackageManager pm) {
        if (pm != curPM) {
            curPM = pm;
            backwardsPrinted = 0;
        }

        if (ps.hasBackwardsChanges() && backwardsPrinted++ < 10) print(ps, "data/pairwiseResults/backwards");

        // Turn on for general printing
//        Random rand = new Random();
//        boolean MVN_NPM = pm == PairCollector.PackageManager.NPM || pm == PairCollector.PackageManager.MAVEN;
//        if (MVN_NPM && rand.nextInt(100000) == 1 || !MVN_NPM && rand.nextInt(1000) == 1) {
//            print(ps, "data/pairwiseResults");
//        }
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

    private void print(PairStatistics ps, String path) {
        PairIDs pair = ps.getPair().getPairIDs();
        if (alreadyPrinted.contains(pair)) return;

        new File(path).mkdirs();
        ps.printToFile(String.format("%s/%s_%d_%d.csv", path, ps.getPm().toString(), pair.component1(), pair.component2()));
        alreadyPrinted.add(pair);
    }
}
