package masters.flexilag

import masters.libiostudy.Version

/**
 * @author Jacob Stringer
 * @date 13/01/2020
 */
class HexLagChecker : LagChecker {


    override fun getDeclaration(classification: String, declaration: String): Declaration {
        val parts = declaration.split("or").map { it.split("and").map { it.trim() } }

        val accumulator = Declaration.getAny()
        var current = accumulator

        for (part in parts) {
            current.joinOr(andPhrase(part))
            current = current.nextOr ?: throw UnsupportedOperationException()
        }

        return accumulator.nextOr ?: throw UnsupportedOperationException()
    }

    private fun andPhrase(declaration: List<String>) : Declaration {
        val accumulator = Declaration.getAny()
        var current = accumulator

        for (phrase in declaration) {
            current.joinAnd(resolve(phrase))

            current = current.nextAnd ?: throw UnsupportedOperationException()
        }

        return accumulator.nextAnd ?: throw UnsupportedOperationException()
    }

    private fun resolve(part: String) : Declaration {
         return when {
             part.startsWith(">=") -> Declaration(Version.create(part.substring(2)), Declaration.maximumVersion)
             part.startsWith(">") -> Declaration(Declaration.normaliseExclusiveStart(Version.create(part.substring(1))), Declaration.maximumVersion)
             part.startsWith("<=") -> Declaration(Declaration.minimumVersion, Version.create(part.substring(2)))
             part.startsWith("<") -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(Version.create(part.substring(1))))
             part.startsWith("==") || part[0].isDigit() -> Declaration(Version.create(part), Version.create(part))
             part.startsWith("~>") -> semverRange(part.substring(2))
             else -> throw UnsupportedOperationException()
         }
    }

    private fun semverRange(declaration: String) : Declaration {
        val dec = Version.create(declaration)
        return when (dec.versionTokens.size) {
            1 -> throw UnsupportedOperationException()
            2 -> Declaration(dec, Declaration.minorEndRange(dec))
            else -> Declaration(dec, Declaration.microEndRange(dec))
        }
    }
}