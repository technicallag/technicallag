import masters.flexilag.MatcherResult;
import masters.flexilag.MavenLagChecker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Created by Jacob Stringer on 12/12/2019.
 */
class FlexibleTests {

    @ParameterizedTest
    @CsvFileSource(resources = {
            "/flexilag-tests/Cargo.csv",
            "/flexilag-tests/CPAN.csv",
            "/flexilag-tests/CRAN.csv",
            "/flexilag-tests/Dub.csv",
            "/flexilag-tests/Elm.csv",
            "/flexilag-tests/Haxelib.csv",
            "/flexilag-tests/Hex.csv",
            "/flexilag-tests/Homebrew.csv",
            "/flexilag-tests/Maven.csv",
            "/flexilag-tests/NPM.csv",
            "/flexilag-tests/NuGet.csv",
            "/flexilag-tests/Packagist.csv",
            "/flexilag-tests/Pub.csv",
            "/flexilag-tests/Puppet.csv",
            "/flexilag-tests/Pypi.csv",
            "/flexilag-tests/Rubygems.csv",
    }, numLinesToSkip = 1)
    public void testFlexibleLag(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), new MavenLagChecker().matches(test, classification, declaration));
    }

}