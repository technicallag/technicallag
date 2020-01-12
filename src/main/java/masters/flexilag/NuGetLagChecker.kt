package masters.flexilag

import masters.libiostudy.Version

/**
 * @author ______
 */
class NuGetLagChecker : LagChecker {
    override fun disambiguate(classification: String, declaration: String): Declaration {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matches(version: Version, classification: String, declaration: String): MatcherResult {
        return when (classification) {
            "at-least" -> atleast(version, declaration)
            "fixed" -> fixed(version, declaration)
            "range" -> range(version, declaration)
            "at-most" -> atmost(version, declaration)
            "any" -> MatcherResult.MATCH
            else -> MatcherResult.NOT_SUPPORTED
        }
    }

    fun fixed(version: Version, declaration: String): MatcherResult {
        var string = declaration
        if (declaration[0] == '[')
            string = string.substring(1, string.length-1)
        else if (declaration.contains("="))
            string = string.substring(string.indexOf("=") + 1).trim()
        val dec = Version.create(string)

        return when(dec.sameMicro(version)) {
            true -> MatcherResult.MATCH
            else -> MatcherResult.NO_MATCH
        }
    }

    fun range(version: Version, declaration: String): MatcherResult {


        return MatcherResult.NOT_SUPPORTED
    }

    fun atleast(version: Version, declaration: String): MatcherResult {

        return MatcherResult.NOT_SUPPORTED
    }

    fun atmost(version: Version, declaration: String): MatcherResult {

        return MatcherResult.NOT_SUPPORTED
    }
}