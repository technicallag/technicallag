package masters.dataClasses;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Class represention versions.
 * The key property is that this is comparable.
 * @author jens dietrich
 * @author jacob stringer - implemented additional methods to compare numberBehind of versions
 */


public class Version implements Comparable<Version> {

//    public static Logger LOGGER = Logging.getLogger("version");

    public List<BigInteger> versionTokens = new ArrayList<>(3);
    public String additionalInfo = null;
    private String string;

    public static final Pattern VDX = Pattern.compile("(v|V)\\d+((\\.|-)(.)*)?");

    public static Cache<String, Version> CACHE = CacheBuilder.newBuilder()
        .maximumSize(5_000_000)
        .softValues()
        .build(
                new CacheLoader<String, Version>() {
                    @Override
                    public Version load(String s) throws Exception {
                        return create(s);
                    }
                }
        );


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

    public static Version create(String versionDef) {
        Version version = new Version();
        version.string = versionDef;

        String versionDef2 = versionDef;

        // trail leading v s
        if (VDX.matcher(versionDef).matches()) {
            versionDef2 = versionDef2.substring(1);
        }

        String leadingDigits = null;
        while ((leadingDigits=extractLeadingDigits(versionDef2))!=null) {
            BigInteger tok = new BigInteger(leadingDigits);
            if (leadingDigits.length()<versionDef2.length()) {
                versionDef2 = versionDef2.substring(leadingDigits.length()+1);
            }
            else {
                versionDef2 = "";
            }
            version.versionTokens.add(tok);
        }
        version.additionalInfo = versionDef2;

        return version;
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


    public static boolean lessThan(String versionDef1,String versionDef2)  {
        try {
            Version version1 = CACHE.get(versionDef1, () -> Version.create(versionDef1));
            Version version2 = CACHE.get(versionDef2, () -> Version.create(versionDef2));
            return version1.compareTo(version2) < 0;
        } catch (Exception x) {
//            LOGGER.error("Error caching versions",x);
            throw new IllegalStateException(x);
        }
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

    public VersionRelationship getRelationship(Version other) {
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

        // next two rules: 1.2.3 > 1.2-beta
        if (size1>size2) {
            return 1;
        }
        else if (size2>size1) {
            return -1;
        }
        else {
//            if (otherVersion.additionalInfo.isEmpty()) {  // additional info is always smaller (-alpha, -beta, -rc)
//                return -1;
//            }
            return this.additionalInfo.compareTo(otherVersion.additionalInfo);
        }
    }
}