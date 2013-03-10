package emcshop.util;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class LoginUtilsTest {
	@Test
	public void buildLoginCookiesMap() {
		Map<String, String> expected = new HashMap<String, String>();
		expected.put("emc_session", "session-id");
		Map<String, String> actual = LoginUtils.buildLoginCookiesMap("session-id");
		assertEquals(expected, actual);
	}
}
