package masters.utils;

import masters.*;
import masters.libiostudy.Version;

import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;

public class Database {

    private static final int NUM_CONNECTIONS = 1;
    private static ArrayBlockingQueue<Connection> CONNECTIONS = new ArrayBlockingQueue<>(NUM_CONNECTIONS);

    static {
        for (int i = 0; i < NUM_CONNECTIONS; i++) {
            Connection c = getConnection();
            try {
                c.setAutoCommit(false);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            CONNECTIONS.add(c);
        }
    }

    public static void closeConnections() {
        for (Connection c: CONNECTIONS) {
            try {
                c.close();
            } catch (SQLException e) {
                Logging.getLogger("").error(e);
            }
        }
    }

    public static Connection getConnection() {
        try {
            Properties dbProperties = loadDBSettings();
            Class.forName(dbProperties.getProperty("driver"));
            String url = dbProperties.getProperty("url");
            return DriverManager.getConnection(url, dbProperties);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    private static Properties loadDBSettings() {
        String dbConfigFileName = "db.properties";
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(dbConfigFileName)) {
            properties.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return properties;
    }

    public static ResultSet runQueryNoLogs(String sql, Object... params) {
        try {
            Connection c = CONNECTIONS.take();
            c.setAutoCommit(false);

            PreparedStatement stmt = c.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                stmt.setString(i+1, params[i].toString());
            }

            stmt.setFetchSize(1000);
            ResultSet rs = stmt.executeQuery();

            CONNECTIONS.add(c);
            return rs;
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
            return null;
        }
    }

    public static String getProjectName(int id) {
        try {
            Connection c = CONNECTIONS.take();
            PreparedStatement stmt = c.prepareStatement("SELECT name FROM projects WHERE id=?");
            stmt.setString(1, Integer.toString(id));
            ResultSet rs = stmt.executeQuery();
            String result = "";
            if (rs.next()) result = rs.getString("name");

            rs.close();
            stmt.close();
            CONNECTIONS.add(c);

            return result;
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
            return "";
        }
    }

    public static boolean isProjectInDB(int id) {
        try {
            Connection c = CONNECTIONS.take();
            PreparedStatement stmt = c.prepareStatement("SELECT name FROM projects WHERE id=?");
            stmt.setString(1, Integer.toString(id));
            ResultSet rs = stmt.executeQuery();

            boolean result = true;
            if (!rs.next()) result = false;

            rs.close();
            stmt.close();
            CONNECTIONS.add(c);

            return result;
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
            return false;
        }
    }

    public static class CommitInfo {
        String repo, hash;

        @Override
        public String toString() {
            return "CommitInfo{" +
                    "repo='" + repo + '\'' +
                    ", hash='" + hash + '\'' +
                    '}';
        }
    }

    private static String getRepoId(int projectid) {
        try {
            Connection c = CONNECTIONS.take();
            PreparedStatement stmt = c.prepareStatement("SELECT repositoryid FROM projects WHERE id = ?;");
            stmt.setString(1, Integer.toString(projectid));

            ResultSet rs = stmt.executeQuery();
            String res = "";
            if (rs.next()) {
                res = rs.getString("repositoryid");
            }

            rs.close();
            stmt.close();
            CONNECTIONS.add(c);

            return res;
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
            return "";
        }
    }

    public static CommitInfo getCommitInformation(int projectid, String version) {
        try {
            Connection c = CONNECTIONS.take();
            PreparedStatement stmt = c.prepareStatement("SELECT HostType, TagGitSha FROM tags WHERE RepositoryId = ? AND TagName = ?;");
            stmt.setString(1, getRepoId(projectid));
            stmt.setString(2, version);

            ResultSet rs = stmt.executeQuery();
            CommitInfo info = new CommitInfo();
            if (rs.next()) {
                info.repo = rs.getString("HostType");
                info.hash = rs.getString("TagGitSha");
            }

            rs.close();
            stmt.close();
            CONNECTIONS.add(c);

            return info;
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
            return new CommitInfo();
        }
    }

    public static List<DependencyVersion> getDepHistory(PairIDs pair) {
        List<DependencyVersion> results = new ArrayList<>();

        try {
            Connection c = CONNECTIONS.take();
            PreparedStatement stmt = c.prepareStatement("SELECT number, publishedtimestamp FROM versions WHERE projectid = ?;");
            stmt.setString(1, Integer.toString(pair.getDependencyID()));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String time = rs.getString("publishedtimestamp");
                results.add(new DependencyVersion(Version.create(rs.getString("number"), time), time));
            }

            rs.close();
            stmt.close();
            CONNECTIONS.add(c);
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
        }

        return results;
    }

    public static List<ProjectVersionFixed> getProjectHistoryFixed(PairIDs pair) {
        List<ProjectVersionFixed> results = new ArrayList<>();

        try {
            Connection c = CONNECTIONS.take();
            PreparedStatement stmt = c.prepareStatement("SELECT id, number, publishedtimestamp FROM versions WHERE projectid = ?;");
            stmt.setString(1, Integer.toString(pair.getProjectID()));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int versionId = rs.getInt("id");
                PreparedStatement stmt2 = c.prepareStatement("SELECT dependencyrequirements FROM dependencies WHERE projectid = ? AND dependencyprojectid = ? AND versionid = ?;");
                stmt2.setString(1, Integer.toString(pair.getProjectID()));
                stmt2.setString(2, Integer.toString(pair.getDependencyID()));
                stmt2.setString(3, Integer.toString(versionId));

                ResultSet rs2 = stmt2.executeQuery();

                String time = rs.getString("publishedtimestamp");
                results.add(new ProjectVersionFixed(
                        Version.create(rs.getString("number"), time),
                        rs2.next() ? Version.create(rs2.getString("dependencyrequirements")) : null,
                        time)
                );

                rs2.close();
                stmt2.close();
            }

            rs.close();
            stmt.close();
            CONNECTIONS.add(c);
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
        }

        return results;
    }

    public static List<ProjectVersionFlexible> getProjectHistoryFlexible(PairIDs pair) {
        List<ProjectVersionFlexible> results = new ArrayList<>();

        try {
            Connection c = CONNECTIONS.take();
            PreparedStatement stmt = c.prepareStatement("SELECT id, number, publishedtimestamp FROM versions WHERE projectid = ?;");
            stmt.setString(1, Integer.toString(pair.getProjectID()));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int versionId = rs.getInt("id");
                PreparedStatement stmt2 = c.prepareStatement("SELECT dependencyrequirements FROM dependencies WHERE projectid = ? AND dependencyprojectid = ? AND versionid = ?;");
                stmt2.setString(1, Integer.toString(pair.getProjectID()));
                stmt2.setString(2, Integer.toString(pair.getDependencyID()));
                stmt2.setString(3, Integer.toString(versionId));

                ResultSet rs2 = stmt2.executeQuery();

                String time = rs.getString("publishedtimestamp");
                results.add(new ProjectVersionFlexible(
                        Version.create(rs.getString("number"), time),
                        rs2.next() ? rs2.getString("dependencyrequirements") : null,
                        time)
                );

                rs2.close();
                stmt2.close();
            }

            rs.close();
            stmt.close();
            CONNECTIONS.add(c);
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
        }

        return results;
    }

    public static void insert(String sql, Object... params) {
        try {
            Connection c = CONNECTIONS.take();
            PreparedStatement stmt = c.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                stmt.setString(i+1, params[i].toString());
            }
            stmt.executeUpdate();
            stmt.close();
            CONNECTIONS.add(c);
        }

        catch(SQLException | InterruptedException e) {
            Logging.getLogger("").error(e);
        }
    }

    public static String timestampFromDB(Connection c, String projectName, String versionString) {
        try {
            PreparedStatement stmt = c.prepareStatement("select * from versions where ProjectName = ? and Number = ?");
            stmt.setString(1, projectName);
            stmt.setString(2, versionString);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String time = rs.getString("PublishedTimestamp");
                if (time == null) {
                    throw new SQLException();
                }
                return time;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "1970-01-01 01:01:01 UTC";
    }

}
