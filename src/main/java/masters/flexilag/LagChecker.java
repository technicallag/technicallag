package masters.flexilag;

import masters.libiostudy.Classifications;
import masters.libiostudy.Version;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public interface LagChecker {
    MatcherResult matches(Version version, String classification, String declaration);
}
