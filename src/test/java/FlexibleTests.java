import masters.PairCollector;
import masters.flexilag.*;
import masters.libiostudy.Version;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Created by Jacob Stringer on 12/12/2019.
 */
class FlexibleTests {

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Maven.csv", numLinesToSkip = 1)
    public void testFlexibleLagMaven(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.MAVEN, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Rubygems.csv", numLinesToSkip = 1)
    public void testFlexibleLagRubygems(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.RUBYGEMS, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Packagist.csv", numLinesToSkip = 1)
    public void testFlexibleLagPackagist(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.PACKAGIST, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Cargo.csv", numLinesToSkip = 1)
    public void testFlexibleLagCargo(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.CARGO, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Elm.csv", numLinesToSkip = 1)
    public void testFlexibleLagElm(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.ELM, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/NPM.csv", numLinesToSkip = 1)
    public void testFlexibleLagNPM(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.NPM, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Hex.csv", numLinesToSkip = 1)
    public void testFlexibleLagHex(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.HEX, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/NuGet.csv", numLinesToSkip = 1)
    public void testFlexibleLagNuGet(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.NUGET, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Pypi.csv", numLinesToSkip = 1)
    public void testFlexibleLagPypi(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.PYPI, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Pub.csv", numLinesToSkip = 1)
    public void testFlexibleLagPub(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.PUB, Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Puppet.csv", numLinesToSkip = 1)
    public void testFlexibleLagPuppet(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), LagCheckingService.matcher(PairCollector.PackageManager.PUPPET, Version.create(test), classification, declaration));
    }
}