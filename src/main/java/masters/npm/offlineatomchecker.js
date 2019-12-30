const semver = require("semver");
const fs = require("fs");
const readline = require("readline");

/* Takes a file with format of
classification
version
constraint

and gives a file back of type
classification
result (boolean)
*/

//function checkPM(input, output) {
//    var outputStream = fs.createWriteStream(output, {encoding: 'utf8'});
//    var current = [];
//
//    const lineReader = require('line-reader');
//    lineReader.eachLine(input, function(line, last) {
//        current.push(line);
//        if (current.length > 2) {
//            outputStream.write(current[0] + "\n");
//            outputStream.write(semver.satisfies(current[1], current[2]) + "\n");
//            current = [];
//        }
//
//        if (last) {
//            console.log("closed");
//            outputStream.end();
//        }
//    });
//}

function checkPM(input, output) {
    var outputStream = fs.createWriteStream(output, {encoding: 'utf8'});
    const lineReader = require('line-reader');

    lineReader.eachLine(input, function(line, last) {
        try {
            var values = line.split("```");
            if (values.length > 2)
                outputStream.write(values[0] + "```" + semver.satisfies(values[1], values[2]) + "\n");

            if (last) {
                console.log("closed");
                outputStream.end();
            }
        } catch(err) {
            console.log(err);
        }
    });
}

checkPM("../flexible_study/atom.txt", "../flexible_study/atom_results.txt");