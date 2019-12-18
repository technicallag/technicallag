package masters.npm;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Java interface to the NPM semver package.
 * See README.md for how to set this up. A node-side JS adapter is provided sv.js in root which evaluates a version against a constraint, and writes
 * the values into a temp file .npm-semver.result .
 * @author jens dietrich
 */
public class SemVer {

    private static final File NODE_SEMVER_SATISFIES_RESULT = new File(".npm-semver.result");

    // utility to capture native method output from
    // https://www.baeldung.com/run-shell-command-in-java
    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }
        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }


    public static boolean satisfies (String version, String versionConstraint) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        Process process = processBuilder
            .command("node","sv.js",version,versionConstraint)
            .start();

        StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
        Executors.newSingleThreadExecutor().submit(streamGobbler);

        int exitCode = process.waitFor();

        assert exitCode==0;
        assert NODE_SEMVER_SATISFIES_RESULT.exists();

        try (BufferedReader r = new BufferedReader(new FileReader(NODE_SEMVER_SATISFIES_RESULT))) {
            String value = r.readLine();
            return Boolean.valueOf(value);
        }
    }
}
