package masters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import masters.dataClasses.Dependency;
import masters.dataClasses.Project;
import masters.dataClasses.ProjectVersionInfo;
import masters.utils.Logging;
import org.apache.log4j.Logger;
import masters.utils.Database;

// Container class that gathers information from SQL and controls access to the various data classes
public class Results {
    private HashMap<String, Project> projects = new HashMap<>();
    private HashSet<String> projectPairs = new HashSet<>();
    private int FOUND = 0;
    private int NOTFOUND = 0;
    private Logger LOG = Logging.getLogger("");

    public void consumeResults(ResultSet rs) throws SQLException {
        int count = 0;
        while (rs.next()) {
            count++;
            if (count % 100000 == 0)
                LOG.info("Read in line: " + count);

            String name = rs.getString("ProjectName");

            try {
                getProjects().putIfAbsent(name, new Project(name));
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            String version = rs.getString("VersionNumber");

            // Used to avoid submodules being included
            if (version.equals(rs.getString("DependencyRequirements"))) continue;

            Dependency dep = new Dependency(rs.getString("DependencyName"), rs.getString("DependencyRequirements"));
            getProjects().get(name).addProjectInfo(version, dep);
        }

        LOG.info("DB Results consumed");
    }

    public void compareProjects() {
        this.getProjects().forEach((projectStringName, project) -> project.compareVersions());
        LOG.info("Projects compared");
    }

    public void printProjectResults() {
        LOG.info("There were " + getProjects().size() + " projects overall");
        File dir = new File("data/version-comps");
        if (!dir.exists())
            dir.mkdirs();

        this.getProjects().forEach((k, v) -> {
            try {
                File f = new File("data/version-comps/" + k.replace(":", ",") + ".txt");
                v.printChangesToFile(f);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        LOG.info("Project results printed");
    }

    /*
    Dependencies may or may not be projects in the same dataset.
    This function checks for which projects A and B where A depends on B, both A and B are in the dataset.
    When it finds a pair, it adds the pair to project Pairs
     */
    public void checkDependenciesAreProjects() {
        this.getProjects().forEach((projName, project) -> {
            project.getVersions().forEach((versionName, version) -> {
                version.getDependencies().forEach(dep -> {
                    Project p = this.getProjects().get(dep.getProjectName());
                    if (p == null) {
                        dependencyFound(false);
                        return;
                    }
                    ProjectVersionInfo vers = p.getVersions().get(dep.getVersion());
                    if (vers == null) {
                        dependencyFound(false);
                        return;
                    }
                    projectPairs.add(project.getName() + "::" + p.getName());
                    dependencyFound(true);
                });
            });
        });

        LOG.info(String.format("Dependencies FOUND: %d, Not FOUND: %d%n", this.FOUND, this.NOTFOUND));
        LOG.info("Filtering of projects to only include dependency pairs complete");
    }

    private void dependencyFound(boolean found) {
        if (found) this.FOUND++;
        else this.NOTFOUND++;
    }

    // Requires checkDependenciesAreProjects to have run previously
    // Runs ProjectTimelinePair class concurrently
    public void constructTimeline() throws SQLException {
        File dir = new File("data/timelines2");
        if (!dir.exists()) dir.mkdirs();

        // Run concurrently to speed up
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
        ArrayBlockingQueue<Connection> connections = new ArrayBlockingQueue<>(10);
        for (int i = 0; i < 10; i++)
            connections.add(Database.getConnection());

        // Where project a depends on project b
        int i = 0;
        Vector<String> cumulativeInfo = new Vector<>();
        for (String pair: projectPairs) {
            String[] projs = pair.split("::");
            Project a = projects.get(projs[0]);
            Project b = projects.get(projs[1]);
            executor.execute(new ProjectTimelinePair(pair, a, b, connections, i, cumulativeInfo));
            i++;
        }

        // Wait for threads to finish working
        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.MINUTES);
        } catch(InterruptedException e) {
            executor.shutdownNow();
        }

        // Close DB connections
        while(connections.size() > 0)
            connections.poll().close();

        TimelineStats.dumpDataToLogger();
        try (BufferedWriter out = new BufferedWriter(new FileWriter(new File("data/cumulativeStats.csv")))) {
            out.write(String.join(",", "ProjectPair", "numVersA", "numVersB", "numDistinctDepDecs", "versionsWithDeps",
                    "avgMajorVersBehind", "avgMinorVersBehind", "avgMicroVersBehind",
                    "numVersionsBehindCurMajor", "numVersionsBehindCurMinor", "numVersionsBehindCurMicro",
                    "avgMajorVersBehindNoTags", "avgMinorVersBehindNoTags", "avgMicroVersBehindNoTags",
                    "numVersionsBehindCurMajorNoTags", "numVersionsBehindCurMinorNoTags", "numVersionsBehindCurMicroNoTags",
                    "numMajorDecChanges", "numMinorDecChanges", "numMicroDecChanges", "numBackwardsDecChanges") + "\n");
            for (String s: cumulativeInfo) {
                out.write(s + "\n");
            }
        } catch (IOException e) {
            LOG.error(e);
        }

        LOG.info("Timelines constructed and printed");
    }

    public HashMap<String, Project> getProjects() {
        return projects;
    }

    public void setProjects(HashMap<String, Project> projects) {
        this.projects = projects;
    }
}
