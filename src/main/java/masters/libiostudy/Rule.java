package masters.libiostudy;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Representation of a mapping rule.
 * @author jens dietrich
 */
public class Rule {

    static enum STATE {
            TEST,MATCH,CLASSIFY
    };

    private Pattern match = null;
    private String classification = null;
    private List<String> tests = null;

    public Rule(Pattern match, String classification,List<String> tests) {
        this.match = match;
        this.classification = classification;
        this.tests = tests;
    }

    public Pattern getMatch() {
        return match;
    }

    public String getClassification() {
        return classification;
    }

    public List<String> getTests() {
        return tests;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Rule rule = (Rule) o;

        if (match != null ? !match.equals(rule.match) : rule.match != null) return false;
        return classification != null ? classification.equals(rule.classification) : rule.classification == null;
    }

    @Override
    public int hashCode() {
        int result = match != null ? match.hashCode() : 0;
        result = 31 * result + (classification != null ? classification.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Rule: if matches \"" + match +  "\" then classify \"" + classification + '\"';
    }
}
