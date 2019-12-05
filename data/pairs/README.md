Contains 3 types of files for each package manager:

1. PM_name.csv - includes the project ids of the pairs (project, dependency)
2. PM_name_ALL.csv - includes all pairwise project ids and their classification
3. PM_name_semver_violations.csv - prints out the project version, if it violates semver, dep version and if it violates semver

Delete the above files if you wish to reload the information from the database on a programme run.


##Semver changes and violation notes:
- Pypi, NPM and Hex have had == added to fixed versions (was listed as a semver violation)
- Cargo has had ^0.0.x added to fixed versions (was listed as a semver violation)
- Maven [1.3.0] style versions (forced fixed) have been accounted for. First 2000 violations for Maven have been validated. Most problems are due to unusual versioning patterns, using tags instead of numbers, and using 'RELEASE' or 'LATEST'
- Packagist has a lot of violations due to large use of 'dev-master', e.g. the latest version. First 2600 versions checked.
- Rubygems violations are due to double version declarations (a syntax based quirk of rubygems). Nothing needs to change here.
 
 
##Subcomponent FP checks:
- Weirdly, the dataset includes projects with dependencies to themselves, and sometimes the same project has two different ids
- Entire atom file checked - fine
- Cargo has 1 problem out of the first 300

-- 3234722,652299,http-codec,httparse (line 99)

- Hex first 200 seem fine
- Maven has 3 problems out of the first 300

-- 360862,348574,org.eclipse.jst.ws.axis2.creation:ui,org.eclipse.jdt:core (line 103)

-- 418989,644438,net.sf.ingenme:nodereled,net.sf.ingenias:ingeniasjgraphmod (line 190)

-- 359906,379242,org.apache.ivy:ivy,org.apache.httpcomponents:httpclient (line 214)

- NPM 1 problem out of the first 300

-- 1613205,323173,tilestrata-underzoom,tilebelt (line 4)

- NuGet 0 problems in the first 200
- Packagist 0 problems in the first 300
- Pypi has 6 potential issues in the first 300:

-- 2196630,1963161,cadcdata,cadcutils (line 27)

-- 2331651,1777630,tensor2tensor,tensorflow-gpu (line 37)

-- 998602,47070,font-ttfa,FontTools (line 96)

-- 990011,1034785,graphene,graphql-core (line 102)

-- 59600,59615,mozregression,mozrunner (line 114)

-- 2918889,36244,dataflows,datapackage (line 300)

- Rubygems 3 in first 300

-- 1999858,1971310,ass_tests,ass_maintainer-info_base (line 46)

-- 3415159,49261,activejob-google_cloud_tasks,activesupport (line 185)

-- 1656567,44213,actionmessage,activejob (line 251)

