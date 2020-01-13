package masters.flexilag

import masters.libiostudy.Version
import java.math.BigInteger

/**
 * Created by Jacob Stringer on 12/01/2020.
 */
data class Declaration(val start: Version, val last: Version, var not: Boolean = false, var prereleasesOnly: Boolean = false, var nextOr: Declaration? = null, var nextAnd: Declaration? = null) {

    companion object {
        val minimumVersion = Version.create("0.0.0")
        val maximumVersion = Version.create("999999999999.99999.99999")

        fun getAny() : Declaration {
            return Declaration(minimumVersion, maximumVersion)
        }

        fun normaliseExclusiveEnd(version: Version) : Version {
            val newVersion = version.clone()
            for (i in version.versionTokens.lastIndex downTo 0) {
                if (version.versionTokens[i] > BigInteger.ZERO) {
                    newVersion.versionTokens[i] = newVersion.versionTokens[i].subtract(BigInteger.ONE)
                    for (j in i+1 .. version.versionTokens.lastIndex) {
                        newVersion.versionTokens[j] = BigInteger("99999")
                    }
                    while (newVersion.versionTokens.size < 3) {
                        newVersion.versionTokens.add(BigInteger("99999"))
                    }
                    return newVersion
                }
            }

            return Version.create("0.0.0")
        }

        fun normaliseExclusiveStart(version: Version) : Version {
            val newVersion = version.clone()
            if (newVersion.versionTokens.size < 3)
                newVersion.versionTokens.add(BigInteger.ONE)
            else
                newVersion.versionTokens[newVersion.versionTokens.lastIndex] = newVersion.versionTokens.last().add(BigInteger.ONE)
            return newVersion
        }

        fun microEndRange(version: Version) : Version {
            val newVersion = Version.create(version.toString())
            while (newVersion.versionTokens.size < 3) { newVersion.versionTokens.add(BigInteger.ZERO) }

            newVersion.versionTokens[2] = BigInteger("99999")
            for (i in 3 .. newVersion.versionTokens.lastIndex) {
                newVersion.versionTokens[i] = BigInteger.ZERO
            }
            return newVersion
        }

        fun minorEndRange(version: Version) : Version {
            val newVersion = Version.create(version.toString())
            while (newVersion.versionTokens.size < 2) { newVersion.versionTokens.add(BigInteger.ZERO) }

            newVersion.versionTokens[1] = BigInteger("99999")
            for (i in 2 .. newVersion.versionTokens.lastIndex) {
                newVersion.versionTokens[i] = BigInteger.ZERO
            }
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

    fun joinOr(other: Declaration) : Declaration {
        var current = this
        while (current.nextOr != null) {
            current = current.nextOr!!
        }
        current.nextOr = other
        return this
    }

    fun joinAnd(other: Declaration) : Declaration {
        var current = this
        while (current.nextAnd != null) {
            current = current.nextAnd!!
        }
        current.nextAnd = other
        return this
    }

    fun matches(version: Version): Boolean {
        val starterCondition = version > start || version.sameMicro(start)
        val endCondition = version < last || version.sameMicro(last)

        val nextAnds = if (nextAnd != null) nextAnd!!.matches(version) else true
        val nextOrs = if (nextOr != null) nextOr!!.matches(version) else false

        return nextOrs || nextAnds && when {
            starterCondition && endCondition -> when {
                not -> false
                prereleasesOnly && version.additionalInfo.isEmpty() -> false
                else -> true
            }
            not -> true
            else -> false
        }
    }

    fun compare(previous: Declaration) : Update {
        if (nextOr != null && this != previous) return Update.RANGE_CHANGE

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