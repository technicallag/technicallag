package masters.flexilag

import masters.libiostudy.Version
import java.util.regex.Pattern

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
class MavenFlexibleMatcher: FlexibleMatcher {

    companion object {
        val versionNumber = Pattern.compile("\\d+(\\.\\d+){0,2}")
    }

    override fun matches(version: String?, classification: String?, declaration: String?): MatcherResult {
        version ?: return MatcherResult.NOT_SUPPORTED
        classification ?: return MatcherResult.NOT_SUPPORTED
        declaration ?: return MatcherResult.NOT_SUPPORTED

        when (classification) {
            "latest" -> return MatcherResult.MATCH


        }


        // TODO finish method
        return MatcherResult.NOT_SUPPORTED
    }

//    fun getAtLeast(version: String) : Version {
//        versionNumber.matcher(version).group(0)
//    }


}