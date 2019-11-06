package masters.libiostudy;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Definition of valid classifications.
 * @author jens dietrich
 */
public class Classifications {

    public static final Collection<String> ALL = Stream.of(
        "fixed",
        "soft",
        "var-micro",
        "var-minor",
        "any",
        "at-least",
        "at-most",
        "range",
        "latest",
        "not",
        "other",
        "unresolved",
        "unclassified")
        .collect(Collectors.toList());  // use list to have predictable order

    public static final String DEFAULT = "unclassified";
}
