package masters.utils;

import org.apache.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;

public class Database {

    private static final int NUM_CONNECTIONS = 1;
    private static ArrayBlockingQueue<Connection> CONNECTIONS = new ArrayBlockingQueue<>(NUM_CONNECTIONS);

    static {
        for (int i = 0; i < NUM_CONNECTIONS; i++) CONNECTIONS.add(getConnection());
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

    public static ResultSet runQuery(String sql, Object... params) {
        Logger log = Logging.getLogger("");

        log.info("Querying DB for the following SQL:\n" + sql);
        ResultSet rs = runQueryNoLogs(sql, params);
        log.info("Query complete for: \n" + sql);

        return rs;
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
