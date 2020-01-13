package masters.flexilag

import masters.libiostudy.Version
import masters.libiostudy.VersionCategoryWrapper

/**
 * @author Jacob Stringer
 * @date 07/01/2020
 */
class CargoLagChecker : LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        val parts = declaration.split(",")
                .map { it.trim() }

        val accumulator = Declaration(Version.create("0"), Version.create("0"))
        var current = accumulator
        for (part in parts) {
            current.joinAnd(when (VersionCategoryWrapper.getClassification("Cargo", part)) {
                "fixed" -> fixed(part)
                "var-micro" -> micro(part)
                "var-minor" -> minor(part)
                "at-most" -> atmost(part)
                "at-least" -> atleast(part)
                "any" -> Declaration.getAny()
                else -> throw UnsupportedOperationException()
            })

            current = current.nextAnd ?: throw UnsupportedOperationException()
        }

        return accumulator.nextAnd ?: throw UnsupportedOperationException()
    }

    fun firstDigit(declaration: String) : Int? {
        var pointer = 0
        while (pointer < declaration.length && !declaration[pointer].isDigit()) pointer++
        if (!declaration[pointer].isDigit()) return null
        return pointer
    }

    fun fixed(declaration: String) : Declaration {
        val version = Version.create(declaration)
        return Declaration(version, version)
    }

    fun atleast(declaration: String) : Declaration {
        val pointer = firstDigit(declaration) ?: throw UnsupportedOperationException()

        val prefix = declaration.substring(0, pointer)
        val rest = Version.create(declaration.substring(pointer))

        return when {
            prefix.contains("=") -> Declaration(rest, Declaration.maximumVersion)
            else -> Declaration(Declaration.normaliseExclusiveStart(rest), Declaration.maximumVersion)
        }
    }

    fun atmost(declaration: String) : Declaration {
        val pointer = firstDigit(declaration) ?: throw UnsupportedOperationException()

        val prefix = declaration.substring(0, pointer)
        val rest = Version.create(declaration.substring(pointer))

        return when {
            prefix.contains("=") -> Declaration(Declaration.minimumVersion, rest)
            else -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(rest))
        }
    }

    fun micro(declaration: String) : Declaration {
        val pointer = firstDigit(declaration) ?: throw UnsupportedOperationException()
        var wildcard = declaration.indexOf("*")
        while (wildcard > -1 && !declaration[wildcard].isDigit()) wildcard--
        val rest = Version.create(declaration.substring(pointer, if (wildcard > -1) wildcard + 1 else declaration.length))
        return Declaration(rest, Declaration.microEndRange(rest))
    }

    fun minor(declaration: String) : Declaration {
        val pointer = firstDigit(declaration) ?: throw UnsupportedOperationException()
        var wildcard = declaration.indexOf("*")
        while (wildcard > -1 && !declaration[wildcard].isDigit()) wildcard--
        val rest = Version.create(declaration.substring(pointer, if (wildcard > -1) wildcard + 1 else declaration.length))
        return Declaration(rest, Declaration.minorEndRange(rest))
    }
}