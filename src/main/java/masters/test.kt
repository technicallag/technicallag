package masters

import masters.utils.Database
import java.io.*
import java.util.regex.Pattern

/**
 * Created by Jacob Stringer on 1/11/2019.
 */

fun main(args: Array<String>) {
    //getSubcomponentInfo()
    val versionNumber = Pattern.compile("\\d+(\\.\\d+){0,2}")
    if (versionNumber.matcher("(,1.0]").find()) {
        println("hi")
    }
}

fun getSubcomponentInfo() {
    try {
        BufferedWriter(FileWriter("data/subcomponent_ids_and_names")).use { out ->
            File("data/pairs/").listFiles().forEach {
                if (it.name.endsWith("_ALL.csv")) {
                    out.write("${it.name}\n")
                    it.bufferedReader().useLines {
                        it.filter { it.endsWith("SUBCOMPONENT") }
                                .forEach {
                                    val ids = it.split(",").subList(0,2).map{ it.toInt() }
                                    out.write("${ids[0]},${ids[1]},${Database.getProjectName(ids[0])},${Database.getProjectName(ids[1])}\n")
                                }
                    }
                    out.write("\n\n\n")
                }
            }
        }

    } catch(e: Exception) { e.printStackTrace() }
}