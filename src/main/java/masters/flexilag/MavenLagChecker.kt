package masters.flexilag

import masters.libiostudy.Version
import java.util.regex.Pattern

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
class MavenLagChecker: LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        val versionNumber = Pattern.compile("\\d+(\\.\\d+){0,2}")
    }

    override fun matches(version: Version?, classification: String?, declaration: String?): MatcherResult {
        version ?: return MatcherResult.NOT_SUPPORTED
        classification ?: return MatcherResult.NOT_SUPPORTED
        declaration ?: return MatcherResult.NOT_SUPPORTED

        return when (classification) {
            "fixed", "soft" -> fixed(version, declaration)
            "latest" -> MatcherResult.MATCH
            "at-most" -> atMost(version, declaration)
            "at-least" -> atLeast(version, declaration)
            "var-minor" -> minor(version, declaration)
            "var-micro" -> micro(version, declaration)
            else -> MatcherResult.NOT_SUPPORTED
        }
    }

    fun fixed(curVersion: Version, declaration: String): MatcherResult {
        val decVersion = Version.create(declaration)

        return when {
            decVersion.equals(curVersion) -> MatcherResult.MATCH
            else -> MatcherResult.NO_MATCH
        }
    }

    // (,version] style
    fun atMost(curVersion: Version, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val maxVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))

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

    // Main logic for [version,] style. 1+ style goes to major()
    fun atLeast(curVersion: Version, declaration: String): MatcherResult {
        if (declaration.contains("+")) return major(curVersion, declaration)

        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val minVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))

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

    // 1+ style
    fun major(curVersion: Version, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val minVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))

            val compared = curVersion.compareTo(minVersion)
            return when {
                compared >= 0 -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        }

        return MatcherResult.NOT_SUPPORTED
    }

    // 1.+ or 1.2+ style
    fun minor(curVersion: Version, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val minVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))

            val compared = curVersion.compareTo(minVersion)
            val sameMajor = curVersion.sameMajor(minVersion)
            return when {
                compared >= 0 && sameMajor -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        }

        return MatcherResult.NOT_SUPPORTED
    }

    // 1.1.+ or 1.2.1+ style
    fun micro(curVersion: Version, declaration: String): MatcherResult {
        val matcher = versionNumber.matcher(declaration)
        if (matcher.find()) {
            val minVersion = Version.create(declaration.substring(matcher.start(), matcher.end()))

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