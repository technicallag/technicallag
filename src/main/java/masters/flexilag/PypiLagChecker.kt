package masters.flexilag

import masters.libiostudy.Version

/**
 * @author ______
 */
class PypiLagChecker : LagChecker {

    override fun getDeclaration(classification: String, declaration: String): Declaration {
        val parts = declaration.split(",").map { it.trim() }
        return parts.fold(Declaration.getAny()) { acc, part ->
            acc.joinAnd(resolve(part))
        }.nextAnd ?: throw UnsupportedOperationException()
    }

    private fun resolve(part: String) : Declaration {
        return when {
            part.startsWith(">=") -> Declaration(Version.create(part.substring(2)), Declaration.maximumVersion)
            part.startsWith(">") -> Declaration(Declaration.normaliseExclusiveStart(Version.create(part.substring(1))), Declaration.maximumVersion)
            part.startsWith("<=") -> Declaration(Declaration.minimumVersion, Version.create(part.substring(2)))
            part.startsWith("<") -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(Version.create(part.substring(1))))
            part.startsWith("==") || part[0].isDigit() -> Declaration(Version.create(part), Version.create(part))
            part.startsWith("~=") -> semverRange(part.substring(2))
            part == "*" -> Declaration.getAny()
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