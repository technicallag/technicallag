package masters.flexilag

import masters.libiostudy.Version
import java.math.BigInteger

/**
 * @author Jacob Stringer
 * 17/12/2019
 */
class PackagistLagChecker : LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        // Packagist uses whitespace as logical 'AND' and ' || ' as logical 'OR' (AND is resolved before OR). Hyphen ranges dealt with as a third option.
        val parts = splitter(declaration)
        return resolveRecursive(parts)
    }

    override fun matches(version: Version, classification: String, declaration: String) : MatcherResult {
        return try {
            when (getDeclaration(classification, declaration).matches(version)) {
                true -> MatcherResult.MATCH
                else -> MatcherResult.NO_MATCH
            }
        } catch (e: UnsupportedOperationException) {
            MatcherResult.NOT_SUPPORTED
        }
    }

    private fun resolveRecursive(parts: List<String>) : Declaration {
        // pipes() and hyphen() are mutually recursive functions with resolveRecursive() that break the tokens up into smaller logical segments
        if (parts.contains("||")) return pipes(parts)
        if (parts.contains("-")) return hyphen(parts)

        // Dummy declaration to link each token to
        val accumulator = Declaration(Declaration.minimumVersion, Declaration.minimumVersion)
        var current = accumulator

        for (part in parts) {
            val next = when {
                part.contains('*') -> wildcard(part)
                part.contains('~') -> tilde(part)
                part.contains('^') -> caret(part)
                part.startsWith("!=") -> not(part)
                part.startsWith(">=") -> atleast(part, equal=true)
                part.startsWith('>') -> atleast(part)
                part.startsWith("<=") -> atmost(part, equal=true)
                part.startsWith('<') -> atmost(part)
                //!part.contains("[0-9]") -> MatcherResult.NOT_SUPPORTED // Not version-like
                else -> Declaration(Version.create(part), Version.create(part))
            }
            current.nextAnd = next
            current = next
        }
        return accumulator.nextAnd ?: throw UnsupportedOperationException()
    }

    private fun pipes(parts: List<String>) : Declaration {
        val or = parts.indexOf("||")
        val first = resolveRecursive(parts.subList(0,or))
        val second = resolveRecursive(parts.subList(or+1, parts.lastIndex+1))
        return first.joinOr(second)
    }

    private fun hyphen(parts: List<String>) : Declaration {
        val hyphen = parts.indexOf("-")
        val prior = if (hyphen > 1) resolveRecursive(parts.subList(0, if (parts[hyphen - 2] == "||") hyphen-2 else hyphen-1)) else null
        val post = if (hyphen < parts.lastIndex - 1) resolveRecursive(parts.subList(if (parts[hyphen + 2] == "||") hyphen+3 else hyphen+2, parts.lastIndex+1)) else null

        val startVers = Version.create(parts[hyphen-1])
        val endVers = Version.create(parts[hyphen+1])

        // 2.3 allows 2.3.9, 2 allows 2.9.9. This accounts for that rule
        endVers.versionTokens[endVers.versionTokens.lastIndex] = endVers.versionTokens[endVers.versionTokens.lastIndex].add(BigInteger.ONE)
        val dec = Declaration(startVers, Declaration.normaliseExclusiveEnd(endVers))

        val total = (if (hyphen > 2 && parts[hyphen - 2] == "||") prior?.joinOr(dec) else prior?.joinAnd(dec)) ?: dec
        return (if (parts.lastIndex > hyphen + 2 && parts[hyphen + 2] == "||") post?.joinOr(total) else post?.joinAnd(total)) ?: total
    }

    private fun wildcard(part: String) : Declaration {
        val or = part.indexOf('*')
        if (or == 0)
            return Declaration.any

        var newString = part.substring(0, or)
        if (!newString.last().isDigit()) newString += "0"

        return semverRange(newString)
    }

    private fun tilde(part: String) : Declaration {
        return semverRange(part.substring(1))
    }

    private fun caret(part: String) : Declaration {
        val decVersion = Version.create(part.substring(1))
        return when {
            decVersion.major == 0 -> Declaration(decVersion, Declaration.microEndRange(decVersion))
            else -> Declaration(decVersion, Declaration.minorEndRange(decVersion))
        }
    }

    private fun not(part: String) : Declaration {
        val decVersion = Version.create(part.substring(2))
        return Declaration(decVersion, decVersion, not = true)
    }

    private fun atleast(part: String, equal: Boolean = false) : Declaration {
        val decVersion = Version.create(part.substring(if (equal) 2 else 1))
        return Declaration(if (equal) decVersion else Declaration.normaliseExclusiveStart(decVersion), Declaration.maximumVersion)
    }

    private fun atmost(part: String, equal: Boolean = false) : Declaration {
        val decVersion = Version.create(part.substring(if (equal) 2 else 1))
        return Declaration(Declaration.minimumVersion, if (equal) decVersion else Declaration.normaliseExclusiveEnd(decVersion))
    }

    private fun semverRange(declaration: String) : Declaration {
        val dec = Version.create(declaration)
        return when (dec.versionTokens.size) {
            1 -> Declaration.any
            2 -> Declaration(dec, Declaration.minorEndRange(dec))
            else -> Declaration(dec, Declaration.microEndRange(dec))
        }
    }

    private fun splitter(declaration: String) : List<String> {
        return declaration.split(" ", ",")
                .map { it.trim() }
    }
}