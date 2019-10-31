package masters.libiostudy;

import java.util.*;
import java.io.*;
import java.util.stream.*;

import masters.utils.Logging;
import org.apache.log4j.Logger;

/**
 * Created by Jacob Stringer on 24/10/2019.
 * From selected source code in the libio study coauthored and predominantly written by Jens Dietrich
 *
 * This class classifies dependency declarations so that non-fixed versions may be filtered out
 */
public class VersionCategoryWrapper {

    private static Map<String,List<Rule>> RULES_BY_PACKAGE;
    private static Logger LOGGER = Logging.getLogger("Rules parsing");

    static {
        getRulesFromFile();
    }

    public static String getClassification(String platform, String dependency) {
        for (Rule rule: RULES_BY_PACKAGE.get(platform)) {
            if (rule.getMatch().matcher(dependency).matches()) {
                return rule.getClassification();
            }
        }
        return Classifications.DEFAULT;

    }

    private static void getRulesFromFile() {
        // Get all rules available
        File ruleFolder = new File("rules");
        List<String> packageManagersWithRules = Stream.of(ruleFolder.list())
                .filter(f -> f.endsWith(".rules"))
                .map(f -> f.substring(0,f.lastIndexOf('.')))
                .sorted()
                .collect(Collectors.toList());

        RULES_BY_PACKAGE = new HashMap<>();
        for (String packagemanager : packageManagersWithRules) {
            RULES_BY_PACKAGE.put(packagemanager, parseRules(ruleFolder, packagemanager));
        }

    }

    private static List<Rule> parseRules(File ruleFolder, String packageManager) {
        LOGGER.info("Parsing rules for: " + packageManager);
        try {
            return new RuleParser().parse(new File(ruleFolder, packageManager + ".rules"), true);
        }
        catch (IOException x) {
            LOGGER.fatal("Cannot read rules",x);
            return null;
        }
    }


}
