package masters.flexilag

import masters.libiostudy.Version
import java.lang.UnsupportedOperationException

/**
 * @author ______
 */
class DubLagChecker : LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        return when (classification) {
            "any" -> Declaration.getAny()
            "fixed" -> Declaration(Version.create(declaration), Version.create(declaration))
            "at-least" -> when {
                declaration.contains('=') -> Declaration(Version.create(declaration.substring(2)), Declaration.maximumVersion)
                else -> Declaration(Declaration.normaliseExclusiveStart(Version.create(declaration.substring(1))), Declaration.maximumVersion)
            }
            "at-most" -> when {
                declaration.contains('=') -> Declaration(Declaration.minimumVersion, Version.create(declaration.substring(2)))
                else -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(Version.create(declaration.substring(1))))
            }
            "range" -> {
                val first = Version.create(declaration.substring(1, declaration.indexOf('<')))
                val second = Version.create(declaration.substring(declaration.indexOf('<') + 1))
                Declaration(if (declaration[1] == '=') first else Declaration.normaliseExclusiveStart(first),
                        if (declaration[declaration.indexOf('<') + 1] == '=') second else Declaration.normaliseExclusiveEnd(second))
            }
            "var-minor"-> {
                val version = Version.create(declaration.substring(2))
                Declaration(version, Declaration.minorEndRange(version))
            }
            "var-micro" -> {
                val version = Version.create(declaration.substring(2))
                Declaration(version, Declaration.microEndRange(version))
            }
            else -> throw UnsupportedOperationException()
        }
    }
}