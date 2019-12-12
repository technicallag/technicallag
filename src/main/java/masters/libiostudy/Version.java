package masters.libiostudy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import masters.utils.Logging;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static masters.utils.DatesKt.isUnderOneDayDiff;

/**
 * Class represention versions.
 * The key property is that this is comparable.
 * @author jens dietrich
 * @author jacob stringer - implemented most of the additional API methods
 */


public class Version implements Comparable<Version> {

//    public static Logger LOGGER = Logging.getLogger("version");

    public List<BigInteger> versionTokens = new ArrayList<>(3);
    public String additionalInfo = null;
    private String string;
    public String time;

    public int getMicro() { return (versionTokens.size() > 2) ? versionTokens.get(2).intValue() : 0; }
    public int getMinor() { return (versionTokens.size() > 1) ? versionTokens.get(1).intValue() : 0; }
    public int getMajor() { return (versionTokens.size() > 0) ? versionTokens.get(0).intValue() : 0; }

    private static final Pattern VDX = Pattern.compile("[vV=^]{1,2}\\s*\\d+((\\.|-)(.)*)?");
    private static final Pattern TAGNUMBER = Pattern.compile("\\d+$");

    @Override
    public String toString() {
        return this.string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Version version = (Version) o;

        return versionTokens != null ? versionTokens.equals(version.versionTokens) : version.versionTokens == null;
    }

    @Override
    public int hashCode() {
        return versionTokens != null ? versionTokens.hashCode() : 0;
    }

    private static Version createHelper(String versionDef, String time) {
        Version version = new Version();
        version.time = time;
        version.string = versionDef;

        // CLEANING
        // trail leading [vV=^] - ^ is because ^0.0.x is considered fixed in cargo (and some other PMs)
        versionDef = versionDef.trim();
        if (VDX.matcher(versionDef).matches()) {
            versionDef = versionDef.substring(1).trim();

            // Solves pypi version declarations with double equals, e.g. "==1.0.0"
            if (versionDef.startsWith("=")) versionDef = versionDef.substring(1).trim();
        }

        // Maven has [1.3.0] which is a 'forced' fixed version
        if (versionDef.startsWith("[") && versionDef.endsWith("]"))
            versionDef = versionDef.substring(1,versionDef.length() - 1).trim();

        // CREATING
        String versionDef2 = versionDef;

        String leadingDigits = null;
        while ((leadingDigits=extractLeadingDigits(versionDef2))!=null) {
            BigInteger tok = new BigInteger(leadingDigits);
            if (leadingDigits.length()<versionDef2.length()) {
                versionDef2 = versionDef2.substring(leadingDigits.length() + 1);
            }
            else {
                versionDef2 = "";
            }
            version.versionTokens.add(tok);
        }
        version.additionalInfo = versionDef2;
        return version;
    }

    public static Version create(String versionDef) {
        return createHelper(versionDef, null);
    }

    public static Version create(String versionDef, String time) {
        return createHelper(versionDef, time);
    }

    private static String extractLeadingDigits(String versionDef) {
        StringBuffer b = null;
        for (int i=0;i<versionDef.length();i++) {
            char c = versionDef.charAt(i);
            if (Character.isDigit(c)) {
                if (b==null) {
                    b = new StringBuffer();
                }
                b.append(c);
            }
            else {
                return b==null?null:b.toString();
            }
        }
        return b==null?null:b.toString();
    }

    public boolean sameMajor(Version other) {
        if (this.versionTokens.size() == 0 || other.versionTokens.size() == 0) return false;
        return this.versionTokens.get(0).equals(other.versionTokens.get(0));
    }

    public boolean sameMinor(Version other) {
        List<BigInteger> first = new ArrayList<>(2);
        List<BigInteger> second = new ArrayList<>(2);

        first.add(this.versionTokens.size() == 0 ? BigInteger.ZERO : this.versionTokens.get(0));
        first.add(this.versionTokens.size() < 2 ? BigInteger.ZERO : this.versionTokens.get(1));
        second.add(other.versionTokens.size() == 0 ? BigInteger.ZERO : other.versionTokens.get(0));
        second.add(other.versionTokens.size() < 2 ? BigInteger.ZERO : other.versionTokens.get(1));

        return first.get(0).equals(second.get(0)) && first.get(1).equals(second.get(1));
    }

    public enum VersionRelationship {
        SAME_MAJOR,
        SAME_MINOR,
        SAME_MICRO,
        DIFFERENT
    }

    public VersionRelationship getRelationship(Version other) {
        if (other == null) return null;

        List<BigInteger> first = new ArrayList<>(3);
        List<BigInteger> second = new ArrayList<>(3);

        first.add(this.versionTokens.size() == 0 ? BigInteger.ZERO : this.versionTokens.get(0));
        first.add(this.versionTokens.size() < 2 ? BigInteger.ZERO : this.versionTokens.get(1));
        first.add(this.versionTokens.size() < 3 ? BigInteger.ZERO : this.versionTokens.get(2));

        second.add(other.versionTokens.size() == 0 ? BigInteger.ZERO : other.versionTokens.get(0));
        second.add(other.versionTokens.size() < 2 ? BigInteger.ZERO : other.versionTokens.get(1));
        second.add(other.versionTokens.size() < 3 ? BigInteger.ZERO : other.versionTokens.get(2));

        if (!(first.get(0).equals(second.get(0)))) {
            return VersionRelationship.DIFFERENT;
        } else if (!(first.get(1).equals(second.get(1)))) {
            return VersionRelationship.SAME_MAJOR;
        } else if (!(first.get(2).equals(second.get(2)))) {
            return VersionRelationship.SAME_MINOR;
        } else {
            return VersionRelationship.SAME_MICRO;
        }
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    @Override
    public int compareTo(Version otherVersion) {
        int size1 = versionTokens.size();
        int size2 = otherVersion.versionTokens.size();
        for (int i=0;i<Math.min(size1,size2);i++) {
            int diff = this.versionTokens.get(i).compareTo(otherVersion.versionTokens.get(i));
            if (diff!=0) {
                return diff;
            }
        }

        // Order on timestamp (granularity of one day, as published timestamps are not fully accurate below that
        if (this.time != null && otherVersion.time != null && !isUnderOneDayDiff(this.time, otherVersion.time)) {
            int timecomp = this.time.substring(0,10).compareTo(otherVersion.time.substring(0,10));
            if (timecomp != 0) return timecomp;
        }

        // next two rules: 1.2.3 > 1.2-beta
        if (size1>size2) {
            return 1;
        }
        else if (size2>size1) {
            return -1;
        }
        else {
            // Check that the tag type is the same - else do an alphabetical ordering.
            // Assumption: tags are constructed as '-<letters><digits>'
            for (int i = 0; i < Math.min(additionalInfo.length(), otherVersion.additionalInfo.length()); i++) {
                char first = additionalInfo.charAt(i), second = otherVersion.additionalInfo.charAt(i);

                // If they are both digits, they have matching letter parts of the tag, so can go to the next stage
                if (isDigit(first) && isDigit(second)) break;

                // If the letters differ, then they are different types of tags - alphabetical ordering
                else if (first != second) {
                    return first - second;
                }
            }

            // Some release are along the lines of -rc9 and -rc10. This will take the number off the end and compare by that first.
            Matcher m1 = TAGNUMBER.matcher(additionalInfo), m2 = TAGNUMBER.matcher(otherVersion.additionalInfo);
            if (m1.find() && m2.find()) {
                return new BigInteger(m1.group()).subtract(new BigInteger(m2.group())).signum();
            }

            // Do an alphabetical ordering failing the above
            return this.additionalInfo.compareTo(otherVersion.additionalInfo);
        }
    }
}