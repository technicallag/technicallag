package masters.flexilag;

import masters.PairCollector;
import masters.utils.Logging;

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
        try {
            if (mapper.containsKey(pm)) {
                return mapper.get(pm).matches(version, classification, declaration);
            }
        } catch (Exception e) {
            Logging.getLogger("").error(String.format("Exception in flexible matcher with info pm:%s version:%s classification:%s declaration:%s", pm, version, classification, declaration), e);
        }
        return MatcherResult.NOT_SUPPORTED;
    }
}
