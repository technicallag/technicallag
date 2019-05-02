package utils;

import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class Database {

    public static Connection getConnection() {
        try {
            Properties dbProperties = loadDBSettings();
            Class.forName(dbProperties.getProperty("driver"));
            String url = dbProperties.getProperty("url");
            // String user = dbProperties.getProperty("user");
            // String pwd = dbProperties.getProperty("pwd");
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

    public static String timestampFromDB(Connection c, String projectName, String versionString) {
        try {
            PreparedStatement stmt = c.prepareStatement("select * from versions where ProjectName = ? and Number = ?");
            stmt.setString(1, projectName);
            stmt.setString(2, versionString);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String time = rs.getString("CreatedTimestamp");
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
