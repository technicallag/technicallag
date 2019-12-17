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
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), new MavenLagChecker().matches(Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Rubygems.csv", numLinesToSkip = 1)
    public void testFlexibleLagRubygems(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), new RubygemsLagChecker().matches(Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Packagist.csv", numLinesToSkip = 1)
    public void testFlexibleLagPackagist(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), new RubygemsLagChecker().matches(Version.create(test), classification, declaration));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/NPM.csv", numLinesToSkip = 1)
    public void testFlexibleLagNPM(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), new NPMLagChecker().matches(Version.create(test), classification, declaration));
    }
}