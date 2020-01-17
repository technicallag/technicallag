package masters

import java.io.File

/**
 * Created by Jacob Stringer on 15/01/2020.
 */

fun printUpdatesOfAToLatex() {
    // Uses data from matrices_detailed.csv (ALL aggregator, all types matrix)
    File("data/updates_A_B.tex").bufferedWriter().use { out ->
        out.write("\\begin{tabular}{|l|rrr|}\n")
        out.write("\\hline\n")
        out.write("Update Type in A & B\\_MAJOR & B\\_MINOR & B\\_MICRO \\\\\n")
        out.write("\\hline\n")

        // Dividing all by 100 so that it is in percentages rather than decimals
        val majorTotal = (11741.0+12333+8712+194094+93+229+119)/100
        val minorTotal = (15411.0+69776+50508+1468412+519+808+480)/100
        val microTotal = (18351.0+61917+147238+6489523+1674+3163+1568)/100
        val tagTotal = (4281.0+32011+40756+1422918+851+6257+733)/100

        println("$majorTotal, $minorTotal, $microTotal, $tagTotal, ${microTotal / (majorTotal + minorTotal + microTotal + tagTotal)}")

        out.write("Major & %.2f\\%% & %.2f\\%% & %.2f\\%% \\\\\n".format(11741.0/majorTotal, 12333.0/majorTotal, 8712.0/majorTotal))
        out.write("Minor & %.2f\\%% & %.2f\\%% & %.2f\\%% \\\\\n".format(15411.0/minorTotal, 69776.0/minorTotal, 50508.0/minorTotal))
        out.write("Micro & %.2f\\%% & %.2f\\%% & %.2f\\%% \\\\\n".format(18351.0/microTotal, 61917.0/microTotal, 147238.0/microTotal))
        out.write("Tag update & %.2f\\%% & %.2f\\%% & %.2f\\%% \\\\\n".format(4281.0/tagTotal, 32011.0/tagTotal, 40756.0/tagTotal))

        out.write("\\hline\n")
        out.write("\\end{tabular}\n")
    }


}