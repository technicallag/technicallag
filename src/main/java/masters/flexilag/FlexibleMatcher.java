package masters.flexilag;

import masters.libiostudy.Classifications;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public interface FlexibleMatcher {
    MatcherResult matches(String version, String classification, String declaration);
}
