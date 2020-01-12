package masters.flexilag

import masters.libiostudy.Version

/**
 * @author Jacob Stringer
 * @date 07/01/2020
 */
class CargoLagChecker : LagChecker {
    override fun disambiguate(classification: String, declaration: String): Declaration {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matches(version: Version, classification: String, declaration: String): MatcherResult {
        val declaration = declaration.trim()
        return when (classification) {
            "fixed" -> fixed(version, declaration)
            "var-micro" -> micro(version, declaration)
            "var-minor" -> minor(version, declaration)
            "at-most" -> atmost(version, declaration)
            "at-least" -> atleast(version, declaration)
            "any" -> MatcherResult.MATCH
            else -> MatcherResult.NOT_SUPPORTED
        }
    }

    fun firstDigit(declaration: String) : Int? {
        var pointer = 0
        while (pointer < declaration.length && !declaration[pointer].isDigit()) pointer++
        if (!declaration[pointer].isDigit()) return null
        return pointer
    }

    fun fixed(version: Version, declaration: String) : MatcherResult {
        return when (Version.create(declaration).sameMicro(version)) {
            true -> MatcherResult.MATCH
            else -> MatcherResult.NO_MATCH
        }
    }

    fun atleast(version: Version, declaration: String) : MatcherResult {
        val pointer = firstDigit(declaration) ?: return MatcherResult.NOT_SUPPORTED

        val prefix = declaration.substring(0, pointer)
        val rest = Version.create(declaration.substring(pointer))

        if (prefix.contains("=") && rest.sameMicro(version)) return MatcherResult.MATCH
        return when (version > rest) {
            true -> MatcherResult.MATCH
            else -> MatcherResult.NO_MATCH
        }
    }

    fun atmost(version: Version, declaration: String) : MatcherResult {
        val pointer = firstDigit(declaration) ?: return MatcherResult.NOT_SUPPORTED

        val prefix = declaration.substring(0, pointer)
        val rest = Version.create(declaration.substring(pointer))

        if (prefix.contains("=") && rest.sameMicro(version)) return MatcherResult.MATCH
        return when (version < rest && !version.sameMicro(rest)) {
            true -> MatcherResult.MATCH
            else -> MatcherResult.NO_MATCH
        }
    }

    fun micro(version: Version, declaration: String) : MatcherResult {
        val pointer = firstDigit(declaration) ?: return MatcherResult.NOT_SUPPORTED
        var wildcard = declaration.indexOf("*")
        while (wildcard > -1 && !declaration[wildcard].isDigit()) wildcard--
        val rest = Version.create(declaration.substring(pointer, if (wildcard > -1) wildcard + 1 else declaration.length))

        return when (rest.sameMinor(version) && version >= rest) {
            true -> MatcherResult.MATCH
            else -> MatcherResult.NO_MATCH
        }
    }

    fun minor(version: Version, declaration: String) : MatcherResult {
        val pointer = firstDigit(declaration) ?: return MatcherResult.NOT_SUPPORTED
        var wildcard = declaration.indexOf("*")
        while (wildcard > -1 && !declaration[wildcard].isDigit()) wildcard--
        val rest = Version.create(declaration.substring(pointer, if (wildcard > -1) wildcard + 1 else declaration.length))

        return when (rest.sameMajor(version) && version >= rest) {
            true -> MatcherResult.MATCH
            else -> MatcherResult.NO_MATCH
        }
    }
}