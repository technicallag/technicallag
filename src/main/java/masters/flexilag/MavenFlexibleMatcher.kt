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
            "var-major" -> major(version, declaration)
            "var-minor" -> minor(version, declaration)
            "var-micro" -> micro(version, declaration)
            else -> MatcherResult.NOT_SUPPORTED
        }

    }

    fun atMost(version: String, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val maxVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))
            val curVersion = Version.create(version)

            val compared = curVersion.compareTo(maxVersion)
            return when {
                compared < 0 -> MatcherResult.MATCH
                compared > 0 -> MatcherResult.NO_MATCH
                declaration.trim().endsWith(']') -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        }

        return MatcherResult.NOT_SUPPORTED
    }

    fun atLeast(version: String, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val minVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))
            val curVersion = Version.create(version)

            val compared = curVersion.compareTo(minVersion)
            return when {
                compared > 0 -> MatcherResult.MATCH
                compared < 0 -> MatcherResult.NO_MATCH
                declaration.trim().startsWith('[') -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        }

        return MatcherResult.NOT_SUPPORTED
    }

    fun major(version: String, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val minVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))
            val curVersion = Version.create(version)

            val compared = curVersion.compareTo(minVersion)
            return when {
                compared >= 0 -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        }

        return MatcherResult.NOT_SUPPORTED
    }

    fun minor(version: String, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val minVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))
            val curVersion = Version.create(version)

            val compared = curVersion.compareTo(minVersion)
            val sameMajor = curVersion.sameMajor(minVersion)
            return when {
                compared >= 0 && sameMajor -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        }

        return MatcherResult.NOT_SUPPORTED
    }

    fun micro(version: String, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val minVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))
            val curVersion = Version.create(version)

            val compared = curVersion.compareTo(minVersion)
            val sameMinor = curVersion.sameMinor(minVersion)
            return when {
                compared >= 0 && sameMinor -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        }

        return MatcherResult.NOT_SUPPORTED
    }

}