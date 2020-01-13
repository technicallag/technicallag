package masters.flexilag

import masters.libiostudy.Version

/**
 * @author Jacob Stringer
 * @date 13/01/2020
 */
class NPMLagChecker : LagChecker {
    override fun matches(version: Version, classification: String, declaration: String): MatcherResult {
        return try {
            when (getDeclaration(classification, declaration).matches(version)) {
                true -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        } catch (e: UnsupportedOperationException) {
            MatcherResult.NOT_SUPPORTED
        }
    }

    override fun getDeclaration(classification: String, declaration: String): Declaration {
        val parts = declaration.split(" ").map { it.trim() }
        val stripped = if (!declaration[0].isDigit()) declaration.substring(1) else declaration
        return when (classification) {
            "any", "latest" -> Declaration.getAny()
            "fixed" -> Declaration(Version.create(declaration), Version.create(declaration))
            "other", "unclassified" -> throw UnsupportedOperationException()
            "var-micro" -> Declaration(Version.create(stripped), Declaration.microEndRange(Version.create(stripped)))
            "var-minor" -> Declaration(Version.create(stripped), Declaration.minorEndRange(Version.create(stripped)))
            "at-least", "at-most" -> resolve(declaration)
            "not" -> {
                val accumulator = Declaration.getAny()
                var current = accumulator
                for (part in parts) {
                    current.joinOr(resolve(part))
                    current = current.nextOr ?: throw UnsupportedOperationException()
                }
                accumulator.nextOr ?: throw UnsupportedOperationException()
            }
            "range" -> {
                if (parts.size == 3 && parts[1] == "-") {
                    Declaration(Version.create(parts[0]), Version.create(parts[2]))
                } else {
                    val accumulator = Declaration.getAny()
                    var current = accumulator
                    for (part in parts) {
                        current.joinAnd(resolve(part))
                        current = current.nextAnd ?: throw UnsupportedOperationException()
                    }
                    accumulator.nextAnd ?: throw UnsupportedOperationException()
                }
            }
            else -> throw UnsupportedOperationException()
        }
    }

    private fun resolve(part: String): Declaration {
        return when {
            part.startsWith(">=") -> Declaration(Version.create(part.substring(2)), Declaration.maximumVersion)
            part.startsWith(">") -> Declaration(Declaration.normaliseExclusiveStart(Version.create(part.substring(1))), Declaration.maximumVersion)
            part.startsWith("<=") -> Declaration(Declaration.minimumVersion, Version.create(part.substring(2)))
            part.startsWith("<") -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(Version.create(part.substring(1))))
            else -> throw UnsupportedOperationException()
        }
    }
}