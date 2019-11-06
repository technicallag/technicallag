package masters

/**
 * Created by Jacob Stringer on 4/11/2019.
 */

import org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProcessPairNewTests {

    private fun getResults(aID: Int, bID: Int, pm: PairCollector.PackageManager) : PairStatistics {
        val results = ProcessPairNew.classifyPair(CollectDataForPair.collectData(PairIDs(aID,bID)), pm)
        results.printToFile("data/pairwiseResults/errorToTest/${pm}_${aID}_${bID}_latest.csv")
        return results
    }

    @Test
    fun lagTest_263381_223861() {
        // PROBLEM: Thought lag was 2/25/1 major/minor/micro versions behind on the only dependency - should be 1/0/1
        // FIXED: Elvis operator ?: bound less tightly than arithmetic. Bracketed expression and solved
        val results = getResults(263381, 223861, PairCollector.PackageManager.RUBYGEMS)
        assertEquals(Lag(1,0,1), results.quantityOfLag[0])
    }

    @Test
    fun lagCorrectWhenLatest_229043_239833() {
        // PROBLEM: Thought lag was -4,0,0 versions when the dependency was up to date (dependency was filtered out)
        // FIXED: Added in check when versions were filtered completely that there would be 0 lag
        val results = getResults(229043, 239833, PairCollector.PackageManager.RUBYGEMS)
        assertEquals(Lag(0,0,0), results.quantityOfLag[0])

        // PROBLEM: Thought lag was 4,0,0 versions when dep was 4.0.0 and the latest was 4.0.0.rc2
        // FIXED: Elvis operator ?: bound less tightly than arithmetic. Bracketed expression and solved
        assertEquals(Lag(0,0,0), results.quantityOfLag[1])
    }

    @Test
    fun orderingTags_2427516_245694() {
        // PROBLEM: Release tags are not being ordered correctly (-rc2 comes after -rc10). No dep changes in this pair, so lag should increase with time
        // FIXED: Updated Version.compareTo() - Pattern.matcher().matches() seeks to match the whole string rather than the last part only
        val results = getResults(2427516, 245694, PairCollector.PackageManager.RUBYGEMS)
        for ((first, second) in results.quantityOfLag.zipWithNext())
            assertTrue(first <= second)
    }


}
