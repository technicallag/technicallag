package masters.flexilag

import masters.libiostudy.Version
import java.util.regex.Pattern

/**
 * Created by Jacob Stringer on 12/12/2019.
 */
class MavenLagChecker: LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        return when (classification) {
            "unresolved" -> throw UnsupportedOperationException()
            "fixed", "soft" -> fixed(declaration)
            "latest" -> Declaration.getAny()
            else -> {
                val parts = declaration.split(",").map { it.trim() }
                if (parts.size > 2) // It's a pain to deal with multi ranges and there are so few of them
                    throw UnsupportedOperationException()

                val accumulator = Declaration(Version.create("0"), Version.create("0"))
                var current = accumulator

                for (part in parts) {
                    current.joinAnd(when {
                        part.contains("+") -> wildcard(part)
                        else -> brackets(part)
                    })
                    println(current)
                    current = current.nextAnd ?: throw UnsupportedOperationException()
                }

                accumulator.nextAnd ?: throw UnsupportedOperationException()
            }
        }
    }

    private fun wildcard(part: String) : Declaration {
        val or = part.indexOf('+')
        if (or == 0)
            return Declaration.getAny()

        var newString = part.substring(0, or)
        if (!newString.last().isDigit()) newString += "0"

        return semverRange(newString)
    }

    private fun semverRange(declaration: String) : Declaration {
        val dec = Version.create(declaration)
        return when (dec.versionTokens.size) {
            1 -> Declaration(dec, Declaration.maximumVersion)
            2 -> Declaration(dec, Declaration.minorEndRange(dec))
            else -> Declaration(dec, Declaration.microEndRange(dec))
        }
    }

    fun fixed(declaration: String): Declaration {
        val decVersion = Version.create(declaration)
        return Declaration(decVersion, decVersion)
    }

    fun brackets(declaration: String): Declaration {
        return when {
            declaration.length == 1 -> Declaration.getAny()
            declaration.startsWith("]") || declaration.startsWith("(") ->
                Declaration(Declaration.normaliseExclusiveStart(Version.create(declaration.substring(1))), Declaration.maximumVersion)
            declaration.startsWith("[") -> Declaration(Version.create(declaration.substring(1)), Declaration.maximumVersion)
            declaration.endsWith("]") -> Declaration(Declaration.minimumVersion, Version.create(declaration.substring(0, declaration.length - 1)))
            else -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(Version.create(declaration.substring(0, declaration.length - 1))))
        }
    }
}