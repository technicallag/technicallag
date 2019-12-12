package masters.flexilag;

import masters.PairCollector;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public class FlexibleService {

    private static Map<PairCollector.PackageManager, FlexibleMatcher> mapper;

    static {
        mapper = new HashMap<>();
        mapper.put(PairCollector.PackageManager.MAVEN, new MavenFlexibleMatcher());
    }

    public static MatcherResult matcher(PairCollector.PackageManager pm, String version, String classification, String declaration) {
        if (mapper.containsKey(pm)) {
            return mapper.get(pm).matches(version, classification, declaration);
        } else {
            return MatcherResult.NOT_SUPPORTED;
        }
    }
}
