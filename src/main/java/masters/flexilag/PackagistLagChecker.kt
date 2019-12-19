package masters.flexilag

import masters.libiostudy.Version

/**
 * @author Jacob Stringer
 * 17/12/2019
 */
class PackagistLagChecker : LagChecker {

    override fun matches(version: Version, classification: String, declaration: String) : MatcherResult {
        // Packagist uses whitespace as logical 'AND' and ' || ' as logical 'OR' (AND is resolved before OR). Hyphen ranges dealt with as a third option.
        val parts = splitter(declaration)
        return resolveRecursive(parts, version)
    }

    fun resolveRecursive(parts: List<String>, version: Version) : MatcherResult {
        // pipes() and hyphen() are mutually recursive functions with resolveRecursive() that break the tokens up into smaller logical segments
        if (parts.contains("||")) return pipes(parts, version)
        if (parts.contains("-")) return hyphen(parts, version)

        // Deal with each token individually and AND them
        var accumulator = MatcherResult.MATCH
        for (part in parts) {
            accumulator = accumulator.and(when {
                part.contains('*') -> wildcard(part, version)
                part.contains('~') -> tilde(part, version)
                part.contains('^') -> caret(part, version)
                part.startsWith("!=") -> not(part, version)
                part.startsWith(">=") -> atleast(part, version, equal=true)
                part.startsWith('>') -> atleast(part, version)
                part.startsWith("<=") -> atmost(part, version, equal=true)
                part.startsWith('<') -> atmost(part, version)
                //!part.contains("[0-9]") -> MatcherResult.NOT_SUPPORTED // Not version-like
                else -> if (Version.create(part).sameMicro(version)) MatcherResult.MATCH else MatcherResult.NO_MATCH
            })
        }
        return accumulator
    }

    fun pipes(parts: List<String>, version: Version) : MatcherResult {
        val or = parts.indexOf("||")
        return resolveRecursive(parts.subList(0,or), version).or(resolveRecursive(parts.subList(or+1, parts.lastIndex+1), version))
    }

    fun hyphen(parts: List<String>, version: Version) : MatcherResult {
        val hyphen = parts.indexOf("-")
        val prior = if (hyphen > 1) resolveRecursive(parts.subList(0, hyphen-1), version) else MatcherResult.MATCH
        val post = if (hyphen < parts.lastIndex - 1) resolveRecursive(parts.subList(hyphen+2, parts.lastIndex+1), version) else MatcherResult.MATCH

        val startVers = Version.create(parts[hyphen-1])
        val endVers = Version.create(parts[hyphen+1])

        val cur = if (startVers <= version &&
                        (version <= endVers ||
                                endVers.versionTokens.size == 1 && endVers.sameMajor(version) ||
                                endVers.versionTokens.size == 2 && endVers.sameMinor(version) ||
                                endVers.versionTokens.size == 3 && endVers.sameMicro(version)))
            MatcherResult.MATCH else MatcherResult.NO_MATCH

        return prior.and(cur).and(post)
    }

    fun wildcard(part: String, version: Version) : MatcherResult {
        val or = part.indexOf('*')
        if (or == 0)
            return MatcherResult.MATCH

        var newString = part.substring(0, or)
        if (!newString.last().isDigit()) newString += "0"

        return semverRange(version, newString)
    }

    fun tilde(part: String, version: Version) : MatcherResult {
        return semverRange(version, part.substring(1))
    }

    fun caret(part: String, version: Version) : MatcherResult {
        val decVersion = Version.create(part.substring(1))

        if (decVersion > version && !version.sameMicro(decVersion))
            return MatcherResult.NO_MATCH

        return if (decVersion.major == 0 && decVersion.sameMinor(version) || decVersion.sameMajor(version))
            MatcherResult.MATCH
        else
            MatcherResult.NO_MATCH
    }

    fun not(part: String, version: Version) : MatcherResult {
        val decVersion = Version.create(part.substring(2))

        return if (decVersion.sameMicro(version))
            MatcherResult.NO_MATCH
        else
            MatcherResult.MATCH
    }

    fun atleast(part: String, version: Version, equal: Boolean = false) : MatcherResult {
        val decVersion = Version.create(part.substring(if (equal) 2 else 1))

        return if (decVersion < version && !decVersion.sameMicro(version) || (decVersion.sameMicro(version) && equal))
            MatcherResult.MATCH
        else
            MatcherResult.NO_MATCH
    }

    fun atmost(part: String, version: Version, equal: Boolean = false) : MatcherResult {
        val decVersion = Version.create(part.substring(if (equal) 2 else 1))

        return if (decVersion > version && !decVersion.sameMicro(version) || (decVersion.sameMicro(version) && equal))
            MatcherResult.MATCH
        else
            MatcherResult.NO_MATCH
    }

    fun semverRange(version: Version, declaration: String) : MatcherResult {
        val dec = Version.create(declaration)

        if (version < dec && !version.sameMicro(dec))
            return MatcherResult.NO_MATCH

        if (dec.versionTokens.size < 3 && dec.sameMajor(version) || dec.sameMinor(version))
            return MatcherResult.MATCH
        else
            return MatcherResult.NO_MATCH
    }

    fun splitter(declaration: String) : List<String> {
        return declaration.split(" ", ",")
                .map { it.trim() }
    }
}