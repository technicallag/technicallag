const semver = require('semver');
const fs = require("fs");
module.exports = function (version,constraint) { 
    var result = semver.satisfies(version,constraint);
    fs.writeFile(".npm-semver.result", result, (err) => {
  		if (err) console.log(err);});
    return result;
}
require('make-runnable');