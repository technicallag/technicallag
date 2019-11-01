package masters

import masters.dataClasses.Version

/**
 * Created by Jacob Stringer on 1/11/2019.
 */

    fun main(args: Array<String>) {
        println(Version.CACHE.size())
        val vers = Version.create("1.0.0")
    val vers2 = Version.create("1.0.0")
    val vers3 = Version.create("1.0.0")
    println(vers == vers2)
        println(Version.CACHE.size())
    }
