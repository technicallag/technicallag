package masters.flexilag;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public enum MatcherResult {
    MATCH,
    NO_MATCH,
    NOT_SUPPORTED;

    // precedence = NOT_SUPPORTED > NO_MATCH > MATCH
    public MatcherResult and(MatcherResult other) {
        if (this == MATCH) {
            return other;
        } else if (this == NO_MATCH) {
            if (other == MATCH) return this;
            return other;
        } else {
            return this;
        }
    }

    // precedence = NOT_SUPPORTED < NO_MATCH < MATCH
    public MatcherResult or(MatcherResult other) {
        if (this == MATCH || other == MATCH) {
            return MATCH;
        } else if (this == NO_MATCH || other == NO_MATCH) {
            return NO_MATCH;
        } else {
            return NOT_SUPPORTED;
        }
    }
}
