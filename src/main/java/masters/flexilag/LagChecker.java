package masters.flexilag;

import masters.libiostudy.Classifications;
import masters.libiostudy.Version;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public interface LagChecker {
    /**
     *
     * @param version - This is the latest version of the dependency, of type masters.libiostudy.Version (an extension of the Version class from MSR'19)
     * @param classification - This is the classification based off the MSR'19 study and follows the exact format as in the rules files
     * @param declaration - This is the dependency declaration string in project A (may be any arbitrary string, will generally conform to a syntax specified by the PM - could be fixed or flexible)
     * @return An enum MatcherResult with three options:
     * 1. The version is an element of the set specified by the declaration (MatcherResult.MATCH), or
     * 2. The version is not an element of the set specified by the declaration (MatcherResult.NO_MATCH), or
     * 3. We cannot say (MatcherResult.NOT_SUPPORTED) due to syntax we don't cover or misformed declaration strings.
     */
    MatcherResult matches(Version version, String classification, String declaration);

    Declaration disambiguate(String classification, String declaration);
}
