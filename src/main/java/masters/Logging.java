package masters;


import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.log4j.FileAppender;
import org.apache.log4j.SimpleLayout;

import java.io.IOException;
import java.util.Date;

/**
 * Log utility.
 * @author Jacob Stringer
 */
public class Logging {

    static {
        BasicConfigurator.configure();
    }

    public static Logger getLogger(String name) {
        Logger log = Logger.getLogger(name);
        log.setLevel(Level.INFO);

        // Create file for logger
        String path = "logs/" + name + " " + new Date().toString().replaceAll(":", " ") + ".log";
        try {
            log.addAppender(new FileAppender(new SimpleLayout(), path));
        } catch (IOException e) {
            log.error(e);
        }

        return log;
    }
}
