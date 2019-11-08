package masters.old;

import masters.utils.Logging;
import org.apache.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class TimelineStats {
    // General statistics about how projects are filtered
    static AtomicInteger SEMVER_PAIRS = new AtomicInteger(0);
    static AtomicInteger NOT_SEMVER_PAIRS = new AtomicInteger(0);
    static AtomicInteger LARGE_ENOUGH = new AtomicInteger(0);
    static AtomicInteger NOT_LARGE_ENOUGH = new AtomicInteger(0);

    // Timestamp anomaly quantification
    static AtomicLong TIME_PAST_DECLARATION = new AtomicLong(0);
    static AtomicLong NUMBER_PAST_DECLARATION = new AtomicLong(0);

    // What strategy do project pairs use
    static AtomicInteger NEVER_UPDATE = new AtomicInteger(0);
    static AtomicInteger UPDATE_TO_NEWEST = new AtomicInteger(0);
    static AtomicInteger LAG_BEHIND = new AtomicInteger(0);
    static AtomicInteger WENT_BACKWARDS = new AtomicInteger(0);

    static void dumpDataToLogger() {
        Logger LOG = Logging.getLogger("");

        LOG.info("There were " + SEMVER_PAIRS.toString() + " pairs where both use semantic versioning");
        LOG.info("There were " + NOT_SEMVER_PAIRS.toString() + " pairs that were discarded as they don't use semantic versioning");
        LOG.info("There were " + NOT_LARGE_ENOUGH.toString() + " pairs discarded due to small size of history");

        LOG.info("There were " + NUMBER_PAST_DECLARATION.toString() + " dependencies that were declared before publish timestamp");
        if (NUMBER_PAST_DECLARATION.longValue() > 0)
            LOG.info("These were published an average of " + TIME_PAST_DECLARATION.longValue()/ NUMBER_PAST_DECLARATION.longValue()/3_600_000 + " hours after being declared");

        LOG.info("There were " + LARGE_ENOUGH.toString() + " pairs with sufficient history to write to file");

        LOG.info(NEVER_UPDATE.toString() + " project pairs never updated their dependency");
        LOG.info(UPDATE_TO_NEWEST.toString() + " project pairs had an update which went to the newest version");
        LOG.info(LAG_BEHIND.toString() + " project pairs updated but never to the newest version");
        LOG.info(WENT_BACKWARDS.toString() + " project pairs had at least one version that went backwards");
    }
}
