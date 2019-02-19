package utils;

import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
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

}
