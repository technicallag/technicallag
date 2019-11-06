package masters

import masters.dataClasses.Version
import masters.utils.Database
import java.io.BufferedWriter
import java.io.FileWriter

/**
 * Created by Jacob Stringer on 1/11/2019.
 */

fun main(args: Array<String>) {
    Database.insert("INSERT INTO pairs VALUES (?, ?, ?, ?)", 1, 2, PairCollector.PackageManager.RUBYGEMS, PairCollector.Status.INCLUDED)
    Thread.sleep(10_000)
}
