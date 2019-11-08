package masters

/**
 * Created by Jacob Stringer on 4/11/2019.
 */

import masters.old.dataClasses.VersionRelationship
import org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProcessPairNewTests {

    private fun getResults(aID: Int, bID: Int, pm: PairCollector.PackageManager) : PairStatistics {
        val results = ProcessPairNew.classifyPair(CollectDataForPair.collectData(PairIDs(aID,bID)), pm)
        results.printToFile("data/pairwiseResults/errors/${pm}_${aID}_${bID}_latest.csv")
        return results
    }

    private fun getRawData(aID: Int, bID: Int, pm: PairCollector.PackageManager) : PairWithData {
        val rawData = CollectDataForPair.collectData(PairIDs(aID,bID))
        val results = ProcessPairNew.classifyPair(rawData, pm)
        results.printToFile("data/pairwiseResults/errors/${pm}_${aID}_${bID}_latest.csv")
        return rawData
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

    @Test
    fun orderingTags_369494_651538() {
        // PROBLEM: Multiple types of tags have been used within the same micro version - check that it is the same style of tag before using the number to compare
        // FIXED: Updated Version.compareTo() - Checks the letter prefix of the the tags and sorts alphabetically first before considering numbers
        val results = getRawData(369494, 651538, PairCollector.PackageManager.MAVEN)
        for ((first, second) in results.aVersions.zipWithNext()) {
            val alphabeticalTag = first.version.additionalInfo < second.version.additionalInfo
            val sameMicro = first.version.getRelationship(second.version) == VersionRelationship.SAME_MICRO
            assertTrue(!sameMicro || alphabeticalTag)
        }
    }

    @Test
    fun lagMissingVersions_372752_340080() {
        // PROBLEM: version is 20041127.091804 - this will break aggregated data as it thinks it is 20041126 major versions behind
        // SIDENOTE: This will think that it is 1 major version behind even though it isn't. This cannot be easily solved automatically but it is hoped that almost all instances that would break this will get filtered out in the PairCollector.
        // FIX:
        // 1. Versions with tokens over 10000 will now be considered not to be a semantic version and will be filtered out
        // 2. For those that aren't filtered out, the programme will now look at distinct major/minor/micro versions, ignoring any gaps in version numbers
        val results = getResults(372752, 340080, PairCollector.PackageManager.MAVEN)
        for (lag in results.quantityOfLag) {
            assertTrue(lag.major == 1)
        }
    }


}
