package masters.flexilag

import masters.libiostudy.Version
import java.lang.UnsupportedOperationException

/**
 * @author Jacob Stringer
 * @date 13/01/2020
 */
class PubLagChecker : LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        return when (classification) {
            "any" -> Declaration.getAny()
            "fixed" -> {
                var pointer = 0
                while (pointer < declaration.length && !declaration[pointer].isDigit()) pointer++
                if (pointer == declaration.length) throw UnsupportedOperationException()

                val version = Version.create(declaration.substring(pointer))
                Declaration(version, version)
            }
            "at-least" -> when {
                declaration.contains('=') -> Declaration(Version.create(declaration.substring(2)), Declaration.maximumVersion)
                else -> Declaration(Declaration.normaliseExclusiveStart(Version.create(declaration.substring(1))), Declaration.maximumVersion)
            }
            "at-most" -> when {
                declaration.contains('=') -> Declaration(Declaration.minimumVersion, Version.create(declaration.substring(2)))
                else -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(Version.create(declaration.substring(1))))
            }
            "range" -> when {
                declaration.startsWith('^') -> {
                    val vers = Version.create(declaration.substring(1))
                    Declaration(vers, Declaration.minorEndRange(vers))
                }
                else -> {
                    val first = Version.create(declaration.substring(1, declaration.indexOf('<')))
                    val second = Version.create(declaration.substring(declaration.indexOf('<') + 1))
                    Declaration(if (declaration[1] == '=') first else Declaration.normaliseExclusiveStart(first),
                            if (declaration[declaration.indexOf('<') + 1] == '=') second else Declaration.normaliseExclusiveEnd(second))
                }
            }
            else -> throw UnsupportedOperationException()
        }
    }
}