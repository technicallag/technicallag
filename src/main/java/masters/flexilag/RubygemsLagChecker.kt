package masters.flexilag

import masters.libiostudy.Version

/**
 * @author Jacob Stringer
 * 17/12/2019
 */
class RubygemsLagChecker : LagChecker {
    override fun getDeclaration(classification: String, declaration: String): Declaration {
        val decPieces = splitter(declaration)

        val acc = Declaration(Version.create("0"), Version.create("0"))
        var cur = acc
        for (dec in decPieces) {
            dec ?: throw UnsupportedOperationException()
            dec.version ?: throw UnsupportedOperationException()

            // If there are ranges on pre-release tags, it is only within that pre-release range
            if (dec.version.additionalInfo.isNotBlank())
                cur.joinAnd(Declaration(dec.version, dec.version, prereleasesOnly = true))
            else
                cur.joinAnd(when (dec.prefix) {
                    "=" -> Declaration(dec.version, dec.version)
                    "~>" -> when {
                        dec.version.versionTokens.size < 3 -> Declaration(dec.version, Declaration.minorEndRange(dec.version))
                        else -> Declaration(dec.version, Declaration.microEndRange(dec.version))
                    }
                    ">=" -> Declaration(dec.version, Declaration.maximumVersion)
                    ">" -> Declaration(Declaration.normaliseExclusiveStart(dec.version), Declaration.maximumVersion)
                    "<" -> Declaration(Declaration.minimumVersion, Declaration.normaliseExclusiveEnd(dec.version))
                    "<=" -> Declaration(Declaration.minimumVersion, dec.version)
                    "!=" -> Declaration(dec.version, dec.version, not = true)
                    else -> throw UnsupportedOperationException()
                })

            cur = cur.nextAnd!!
        }

        return acc.nextAnd ?: throw UnsupportedOperationException()
    }

    data class PartialDeclaration(val prefix: String, val version: Version?)

    fun splitter(declaration: String) : List<PartialDeclaration?> {
        return declaration.split(",")
                .map { it.trim() }
                .map { classify(it) }
    }

    fun classify(rawString: String) : PartialDeclaration? {
        for (i in rawString.indices) {
            if (rawString[i].isDigit()) {
                return PartialDeclaration(
                        if (i==0) "" else rawString.substring(0, i).trim(),
                        Version.create(rawString.substring(i))
                )
            }
        }

        return null
    }
}
