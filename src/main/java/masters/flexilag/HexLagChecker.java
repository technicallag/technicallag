package masters.flexilag;

/**
 * @author ______
 */
public class HexLagChecker implements LagChecker {

    @Override
    public MatcherResult matches(String version, String classification, String declaration) {
        return MatcherResult.NOT_SUPPORTED;
    }

}
