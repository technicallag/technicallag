package masters.flexilag

import masters.libiostudy.Version

/**
 * @author Jacob Stringer
 * @date 13/01/2020
 */
class NuGetLagChecker : LagChecker {

    override fun getDeclaration(classification: String, declaration: String): Declaration {
        val parts = declaration.split(" ").map { it.trim() }
        val parts2 = mutableListOf<String>()

        val accumulator = Declaration.getAny()
        var current = accumulator

        // Secondary parsing rules because symbols can be split from versions with whitespace, as can separate phrases
        // e.g. >= 3.2.0.11 < 3.3.0
        for (i in 0..parts.lastIndex) {
            if (hasDigits(parts[i])) {
                if (i > 0 && !hasDigits(parts[i - 1]) && !(parts[i-1] == "[" || parts[i-1] == "(")) {
                    parts2.add(parts[i - 1] + parts[i])
                } else {
                    parts2.add(parts[i])
                }
            }
            else if(parts[i] == "*" || parts[i] == "=") {
                current.joinAnd(Declaration.getAny())
                current = current.nextAnd ?: current
            }
        }

        for (part in parts2.flatMap { it.split(",") }.filter { hasDigits(it) }) {
            current.joinAnd(rules(part))
            current = current.nextAnd ?: throw UnsupportedOperationException()
        }

        return accumulator.nextAnd ?: throw UnsupportedOperationException()
    }

    private fun rules(part: String) : Declaration {
        return when {
            part == "=" -> Declaration.getAny()
            part.startsWith("^") -> semverRange(part.substring(1))
            part.startsWith("[") && part.endsWith("]") -> {
                if (part.contains("*")) {
                    var versString = part.substring(1, part.indexOf("*"))
                    if (!versString.last().isDigit()) versString += "0"
                    semverRange(versString)
                } else {
                    val vers = Version.create(part.substring(1, part.lastIndex))
                    Declaration(vers, vers)
                }
            }
            part.startsWith("=") -> Declaration(Version.create(part.substring(1)), Version.create(part.substring(1)))
            part.startsWith("[") || part.startsWith(">=") -> Declaration(Version.create(part.substring(1)), Declaration.maximumVersion)
            part.startsWith("(") || part.startsWith(">") -> Declaration(Declaration.normaliseExclusiveStart(Version.create(part.substring(1))), Declaration.maximumVersion)
            part.endsWith("]") -> Declaration(Declaration.minimumVersion, Version.create(part))
            part.startsWith("<=") -> Declaration(Declaration.minimumVersion, Version.create(part.substring(2)))
            part.endsWith(")") -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(Version.create(part)))
            part.startsWith("<") -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(Version.create(part.substring(1))))
            else -> throw UnsupportedOperationException()
        }
    }

    private fun semverRange(declaration: String) : Declaration {
        val dec = Version.create(declaration)
        return when (dec.versionTokens.size) {
            1 -> Declaration.getAny()
            2 -> Declaration(dec, Declaration.minorEndRange(dec))
            else -> Declaration(dec, Declaration.microEndRange(dec))
        }
    }

    private fun hasDigits(string: String) : Boolean {
        return string.any { it.isDigit() }
    }
}