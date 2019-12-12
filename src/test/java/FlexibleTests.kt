package masters

import masters.flexilag.MatcherResult
import masters.flexilag.MavenFlexibleMatcher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource


/**
 * Created by Jacob Stringer on 12/12/2019.
 */
class FlexibleTests {

    @ParameterizedTest
    @CsvFileSource(resources = ["/flexilag-tests/Maven.csv"], numLinesToSkip = 1)
    fun testFlexibleLag(classification: String, declaration: String, test: String, expected: String) {
        assertEquals(MatcherResult.valueOf(expected.toUpperCase()), MavenFlexibleMatcher().matches(test, classification, declaration))
    }

}