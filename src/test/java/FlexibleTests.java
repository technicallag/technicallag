import masters.flexilag.MatcherResult;
import masters.flexilag.MavenFlexibleMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Created by Jacob Stringer on 12/12/2019.
 */
class FlexibleTests {

    @ParameterizedTest
    @CsvFileSource(resources = "/flexilag-tests/Maven.csv", numLinesToSkip = 1)
    public void testFlexibleLag(String classification, String declaration, String test, String expected) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), new MavenFlexibleMatcher().matches(test, classification, declaration));
    }

}