package masters.flexilag;

import masters.libiostudy.Version;

/**
 * @author ______
 */
public class RubygemsLagChecker implements LagChecker {

    @Override
    public MatcherResult matches(Version version, String classification, String declaration) {
        return MatcherResult.NOT_SUPPORTED;
    }

}
