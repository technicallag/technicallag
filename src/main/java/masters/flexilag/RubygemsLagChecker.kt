package masters.flexilag

import masters.libiostudy.Version

/**
 * @author Jacob Stringer
 * 17/12/2019
 */
class RubygemsLagChecker : LagChecker {
    override fun disambiguate(classification: String, declaration: String): Declaration {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun matches(version: Version, classification: String, declaration: String) : MatcherResult {
        val decPieces = splitter(declaration)

        for (dec in decPieces) {
            dec ?: MatcherResult.NOT_SUPPORTED
            dec?.version ?: MatcherResult.NOT_SUPPORTED

            when (dec?.prefix) {
                "=" -> return if (version == dec.version) MatcherResult.MATCH else MatcherResult.NO_MATCH
                "~>" -> if (!version.sameMinor(dec.version) || version < dec.version) return MatcherResult.NO_MATCH
                ">=" -> if (version < dec.version) return MatcherResult.NO_MATCH
                ">" -> if (version <= dec.version) return MatcherResult.NO_MATCH
                "<" -> if (version >= dec.version) return MatcherResult.NO_MATCH
                "<=" -> if (version > dec.version) return MatcherResult.NO_MATCH
                "!=" -> if (version == dec.version) return MatcherResult.NO_MATCH
                else -> return MatcherResult.NOT_SUPPORTED
            }

            // If there are ranges on pre-release tags, it is only within that pre-release range
            if (dec.version.additionalInfo != "" && !dec.version.sameMicro(version)) return MatcherResult.NO_MATCH
        }

        return MatcherResult.MATCH;
    }

    data class PartialDeclaration(val prefix: String, val version: Version)

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
