package masters.flexilag;

import masters.libiostudy.Classifications;
import masters.libiostudy.Version;

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
public interface LagChecker {
    /**
     *
     * @param classification - This is the classification based off the MSR'19 study and follows the exact format as in the rules files
     * @param declaration - This is the dependency declaration string in project A (may be any arbitrary string, will generally conform to a syntax specified by the PM - could be fixed or flexible)
     * @return A Declaration that allows versions to be checked for matches
     */
    Declaration getDeclaration(String classification, String declaration);
}
