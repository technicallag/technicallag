package masters.flexilag

import masters.libiostudy.Version

/**
 * @author Jacob Stringer
 * @date 09/01/2020
 */
class ElmLagChecker : LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        val tokens = declaration.split(" ")

        var first = Version.create(tokens[0])
        var second = Version.create(tokens[4])

        if (!tokens[1].contains("=")) first = Declaration.normaliseExclusiveStart(first)
        if (!tokens[3].contains("=")) second = Declaration.normaliseExclusiveEnd(second)

        return Declaration(first, second)
    }

    override fun matches(version: Version, classification: String, declaration: String): MatcherResult {
        return when (getDeclaration(classification, declaration).matches(version)) {
            false -> MatcherResult.NO_MATCH
            else -> MatcherResult.MATCH
        }
    }
}