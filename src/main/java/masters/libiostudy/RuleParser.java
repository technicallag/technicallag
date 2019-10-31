package masters.libiostudy;

import masters.utils.Logging;

import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Rule parser.
 * Can also be used to check rule definition files.
 * @author jens dietrich
 */
public class RuleParser {

    public static Logger LOGGER = Logging.getLogger("ruleparser");

    private boolean warnings = false;

    public List<Rule> parse (File ruleDefs,boolean strictMode) throws IOException {

        LOGGER.info("parsing rules from " + ruleDefs.getAbsolutePath());
        LOGGER.info("\trunning in " + (strictMode?"strict":"relaxed") + " mode");
        List<Rule> rules = new ArrayList<>();

        // parse
        Rule.STATE state = Rule.STATE.TEST;
        List<String> lines = FileUtils.readLines(ruleDefs, Charset.defaultCharset());

        Pattern pattern = null;
        String classification = null;
        List<String> tests = new ArrayList<>();

        for (int i=0;i<lines.size();i++) {
            String line = lines.get(i).trim();
            int lineNo = i+1;

            if (line.startsWith("#")) {}
            else if (line.length()==0) {}
            else if (line.startsWith("test:")) {
                if (state == Rule.STATE.MATCH) {
                    handleParserError("Line " + lineNo + ": \"match:\" clause must be followed by \"classify:\" clause",strictMode);
                }
                else {
                    String test = line.substring("test:".length()).trim();
                    tests.add(test);
                }
                state = Rule.STATE.TEST;
            }
            else if (line.startsWith("match:")) {
                if (state == Rule.STATE.MATCH) {
                    handleParserError("line " + lineNo + ": \"match:\" clause must be followed by \"classify:\" clause",strictMode);
                }
                else {
                    String match = line.substring("match:".length()).trim();
                    try {
                        pattern = Pattern.compile(match);
                    } catch (PatternSyntaxException x) {
                        handleParserError("line " + lineNo + ": incorrect regex syntax found: " + match,strictMode);
                    }
                }
                state = Rule.STATE.MATCH;
            }
            else if (line.startsWith("classify:")) {
                if (state != Rule.STATE.MATCH) {
                    handleParserError("line " + lineNo + ": \"classify:\" clause must be preceded by \"match:\" clause",strictMode);
                }
                else {
                    classification = line.substring("classify:".length()).trim();
                    if (!Classifications.ALL.contains(classification)) {
                        handleParserError("line " + lineNo + ": undefined target classification: \"" + classification + "\"",strictMode);
                        classification = null;
                    }
                }
                state = Rule.STATE.CLASSIFY;

                if (pattern!=null && classification!=null) {
                    Rule rule = new Rule(pattern, classification, tests);
                    if (test(rule,lineNo,strictMode)) {
                        LOGGER.debug("\tadding: " + rule + " // def. in line " + lineNo);
                        rules.add(rule);
                    }
                }

                // reset
                pattern = null;
                classification = null;
                tests = new ArrayList<>();

            }
            else {
                handleParserError("line " + lineNo + ": unknown token, lines should either be empty, or start with \"#\" (for comments), \"test:\", \"match:\" or \"classify:\"",strictMode);
            }
        }

        return rules;
    }

    private boolean test(Rule rule, int lineNo,boolean strictMode) {
        boolean success = true;
        for (String test:rule.getTests()) {
            if  (!rule.getMatch().matcher(test).matches()) {
                handleParserError("line " + lineNo + ": rule violates one of its tests: " + test,strictMode);
                success = false;
            }
        }
        return success;
    }

    private void handleParserError(String s,boolean strictMode)  {
        this.warnings = true;
        if (strictMode) {
            LOGGER.error("\t"+s);
            System.exit(0);
        }
        else {
            LOGGER.warn("\t"+s);
        }
    }

}
