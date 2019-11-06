package masters;

class VersionsBehind {
    long[] numberBehind = new long[3]; // Major, minor, micro
    String[] latestVersion = new String[]{"", "", ""};

    String toString(String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(numberBehind[i]);
            sb.append(sep);
            sb.append(latestVersion[i]);
            if (i < 2) sb.append(sep);
        }
        return sb.toString();
    }

    static String emptyToString(String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(sep);
        }
        return sb.toString();
    }
}
