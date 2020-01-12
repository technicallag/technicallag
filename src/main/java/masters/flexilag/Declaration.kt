package masters.flexilag

import masters.libiostudy.Version
import java.math.BigInteger

/**
 * Created by Jacob Stringer on 12/01/2020.
 */
data class Declaration(val start: Version, val last: Version, val next: Declaration? = null) {

    companion object {
        fun normaliseExclusiveEnd(version: Version) : Version {
            val newVersion = Version.create(version.toString())
            for (i in version.versionTokens.lastIndex .. 0) {
                if (version.versionTokens[i] > BigInteger.ZERO) {
                    newVersion.versionTokens[i].subtract(BigInteger.ONE)
                    for (j in i+1 .. version.versionTokens.lastIndex) {
                        newVersion.versionTokens[j] = BigInteger("99999")
                    }
                    return newVersion
                }
            }

            return Version.create("0.0.0")
        }

        fun normaliseExclusiveStart(version: Version) : Version {
            val newVersion = Version.create(version.toString())
            newVersion.versionTokens[newVersion.versionTokens.lastIndex] = newVersion.versionTokens.last().add(BigInteger.ONE)
            return newVersion
        }
    }

    enum class Update {
        SAME,
        FORWARDS_START,
        FORWARDS_END,
        FORWARDS_START_AND_END,
        BACKWARDS_START,
        BACKWARDS_END,
        BACKWARDS_START_AND_END,
        WIDER,
        NARROWER,
        RANGE_CHANGE
    }

    fun matches(version: Version): Boolean {
        val starterCondition = version > start || version.sameMicro(start)
        val endCondition = version < last || version.sameMicro(last)

        return when {
            starterCondition && endCondition -> true
            next != null -> next.matches(version)
            else -> false
        }
    }

    fun compare(previous: Declaration) : Update {
        if (next != null && this != previous) return Update.RANGE_CHANGE

        return when {
            this.start > previous.start -> when {
                this.last > previous.last -> Update.FORWARDS_START_AND_END
                this.last < previous.last -> Update.NARROWER
                else -> Update.FORWARDS_START
            }
            this.start < previous.start -> when {
                this.last > previous.last -> Update.WIDER
                this.last < previous.last -> Update.BACKWARDS_START_AND_END
                else -> Update.BACKWARDS_START
            }
            else -> when {
                this.last > previous.last -> Update.FORWARDS_END
                this.last < previous.last -> Update.BACKWARDS_END
                else -> Update.SAME
            }
        }
    }
}