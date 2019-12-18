import masters.npm.SemVer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the NPM SemVer integration.
 * @author jens dietrich
 */
public class NPMSemVerTests {

    @Test
    public void test1() throws Exception {
        assertTrue(SemVer.satisfies("1.2.3","1.x"));
    }

    @Test
    public void test2() throws Exception {
        assertFalse(SemVer.satisfies("1.2.3","2.x"));
    }
}
