package masters.flexilag;

import masters.PairCollector.PackageManager;
import masters.PairCollector;
import masters.libiostudy.Version;
import masters.npm.SemVer;
import masters.utils.Logging;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public class LagCheckingService {

    private static Map<PairCollector.PackageManager, LagChecker> mapper;

    // Allows PMs not to call the service if the PM rules have not been implemented
    private static Set<PairCollector.PackageManager> supported = Stream.of(
            PackageManager.MAVEN,
            PackageManager.PACKAGIST,
            PackageManager.RUBYGEMS,
            PackageManager.NPM,
            PackageManager.ATOM
    ).collect(Collectors.toSet());

    static {
        mapper = new HashMap<>();
        mapper.put(PackageManager.ATOM, new NPMLagChecker());
        mapper.put(PackageManager.CARGO, new CargoLagChecker());
        mapper.put(PackageManager.CPAN, new CPANLagChecker());
        mapper.put(PackageManager.CRAN, new CRANLagChecker());
        mapper.put(PackageManager.DUB, new DubLagChecker());
        mapper.put(PackageManager.ELM, new ElmLagChecker());
        mapper.put(PackageManager.HAXELIB, new HaxelibLagChecker());
        mapper.put(PackageManager.HEX, new HexLagChecker());
        mapper.put(PackageManager.HOMEBREW, new HomebrewLagChecker());
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
        return supported.contains(pm);
    }

    public static MatcherResult matcher(PackageManager pm, Version version, String classification, String declaration) {
        try {
            if (pm == PackageManager.NPM || pm == PackageManager.ATOM) {
                if (SemVer.satisfies(version.toString(), declaration)) {
                    return MatcherResult.MATCH;
                } else {
                    return MatcherResult.NO_MATCH;
                }
            }

            else if (mapper.containsKey(pm)) {
                return mapper.get(pm).matches(version, classification, declaration);
            }
        } catch (Exception e) {
            Logging.getLogger("").error(String.format("Exception in flexible matcher with info pm:%s version:%s declaration:%s", pm, version, declaration), e);
        }
        return MatcherResult.NOT_SUPPORTED;
    }
}
