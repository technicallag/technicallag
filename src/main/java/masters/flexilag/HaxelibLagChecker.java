package masters.flexilag;

import masters.libiostudy.Version;

/**
 * @author ______
 */
public class HaxelibLagChecker implements LagChecker {

    @Override
    public MatcherResult matches(Version version, String classification, String declaration) {
        return MatcherResult.NOT_SUPPORTED;
    }

    @Override
    public Declaration getDeclaration(String classification, String declaration) {
        return null;
    }

}
