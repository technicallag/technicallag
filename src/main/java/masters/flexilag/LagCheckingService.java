package masters.flexilag;

import masters.PairCollector;
import masters.libiostudy.Version;
import masters.libiostudy.VersionCategoryWrapper;
import masters.utils.Logging;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public class LagCheckingService {

    private static Map<PairCollector.PackageManager, LagChecker> mapper;

    static {
        mapper = new HashMap<>();
        mapper.put(PairCollector.PackageManager.ATOM, new NPMLagChecker());
        mapper.put(PairCollector.PackageManager.CARGO, new CargoLagChecker());
        mapper.put(PairCollector.PackageManager.CPAN, new CPANLagChecker());
        mapper.put(PairCollector.PackageManager.CRAN, new CRANLagChecker());
        mapper.put(PairCollector.PackageManager.DUB, new DubLagChecker());
        mapper.put(PairCollector.PackageManager.ELM, new ElmLagChecker());
        mapper.put(PairCollector.PackageManager.HAXELIB, new HaxelibLagChecker());
        mapper.put(PairCollector.PackageManager.HEX, new HexLagChecker());
        mapper.put(PairCollector.PackageManager.HOMEBREW, new HomebrewLagChecker());
        mapper.put(PairCollector.PackageManager.MAVEN, new MavenLagChecker());
        mapper.put(PairCollector.PackageManager.NPM, new NPMLagChecker());
        mapper.put(PairCollector.PackageManager.NUGET, new NuGetLagChecker());
        mapper.put(PairCollector.PackageManager.PACKAGIST, new PackagistLagChecker());
        mapper.put(PairCollector.PackageManager.PUB, new PubLagChecker());
        mapper.put(PairCollector.PackageManager.PUPPET, new PuppetLagChecker());
        mapper.put(PairCollector.PackageManager.PYPI, new PypiLagChecker());
        mapper.put(PairCollector.PackageManager.RUBYGEMS, new RubygemsLagChecker());
    }

    public static MatcherResult matcher(PairCollector.PackageManager pm, Version version, String classification, String declaration) {
        try {
            if (mapper.containsKey(pm)) {
                return mapper.get(pm).matches(version, classification, declaration);
            }
        } catch (Exception e) {
            Logging.getLogger("").error(String.format("Exception in flexible matcher with info pm:%s version:%s declaration:%s", pm, version, declaration), e);
        }
        return MatcherResult.NOT_SUPPORTED;
    }
}
