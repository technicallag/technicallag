package masters.flexilag;

import masters.PairCollector.PackageManager;
import masters.PairCollector;
import masters.libiostudy.Version;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public class LagCheckingService {

    private static Map<PairCollector.PackageManager, LagChecker> mapper;
    static {
        mapper = new HashMap<>();
        mapper.put(PackageManager.ATOM, new NPMLagChecker());
        mapper.put(PackageManager.CARGO, new CargoLagChecker());
        mapper.put(PackageManager.ELM, new ElmLagChecker());
        mapper.put(PackageManager.HEX, new HexLagChecker());
        mapper.put(PackageManager.MAVEN, new MavenLagChecker());
        mapper.put(PackageManager.NPM, new NPMLagChecker());
        mapper.put(PackageManager.NUGET, new NuGetLagChecker());
        mapper.put(PackageManager.PACKAGIST, new PackagistLagChecker());
        mapper.put(PackageManager.PUB, new PubLagChecker());
        mapper.put(PackageManager.PUPPET, new PuppetLagChecker());
        mapper.put(PackageManager.PYPI, new PypiLagChecker());
        mapper.put(PackageManager.RUBYGEMS, new RubygemsLagChecker());
    }

    public static boolean supported(PackageManager pm) {
        return mapper.containsKey(pm);
    }

    public static Declaration getDeclaration(PackageManager pm, String classification, String declaration) throws UnsupportedOperationException {
        if (!supported(pm))
            throw new UnsupportedOperationException();

        return mapper.get(pm).getDeclaration(classification, declaration.trim());
    }

    public static MatcherResult matcher(PackageManager pm, Version version, String classification, String declaration) {
        try {
            if (mapper.containsKey(pm)) {
                if (mapper.get(pm).getDeclaration(classification, declaration.trim()).matches(version))
                    return MatcherResult.MATCH;
                else
                    return MatcherResult.NO_MATCH;
            } else {
                return MatcherResult.NOT_SUPPORTED;
            }
        } catch (UnsupportedOperationException e) {
            return MatcherResult.NOT_SUPPORTED;
        }
    }
}
