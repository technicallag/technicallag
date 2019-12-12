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

        return when (classification) {
            "latest" -> MatcherResult.MATCH
            "at-most" -> atMost(version, declaration)
            "at-least" -> atLeast(version, declaration)
            "var-minor" -> minor(version, declaration)
            "var-micro" -> micro(version, declaration)
            else -> MatcherResult.NOT_SUPPORTED
        }

    }

    fun atMost(version: String, declaration: String): MatcherResult {
        try {
            val matcher = versionNumber.matcher(declaration)
            if (matcher.find()) {
                val max_version = Version.create(declaration.substring(matcher.start(), matcher.end()))
                val cur_version = Version.create(version)

                val compared = cur_version.compareTo(max_version)
                return when {
                    compared < 0 -> MatcherResult.MATCH
                    compared > 0 -> MatcherResult.NO_MATCH
                    declaration[matcher.end()] == ']' -> MatcherResult.MATCH
                    else -> MatcherResult.NO_MATCH
                }
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }

        return MatcherResult.NOT_SUPPORTED
    }

    fun atLeast(version: String, declaration: String): MatcherResult {


        // TODO finish method
        return MatcherResult.NOT_SUPPORTED
    }

    fun minor(version: String, declaration: String): MatcherResult {


        // TODO finish method
        return MatcherResult.NOT_SUPPORTED
    }

    fun micro(version: String, declaration: String): MatcherResult {


        // TODO finish method
        return MatcherResult.NOT_SUPPORTED
    }

//    fun getAtLeast(version: String) : Version {
//        versionNumber.matcher(version).group(0)
//    }


}