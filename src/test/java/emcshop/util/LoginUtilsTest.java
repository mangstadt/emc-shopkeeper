package emcshop.util;

import static org.junit.Assert.assertEquals;

import org.apache.http.impl.cookie.BasicClientCookie;
import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class LoginUtilsTest {
	@Test
	public void buildSessionCookie() {
		BasicClientCookie cookie = LoginUtils.buildSessionCookie("session-id");
		assertEquals("emc_session", cookie.getName());
		assertEquals("session-id", cookie.getValue());
		assertEquals(".empireminecraft.com", cookie.getDomain());
		assertEquals("/", cookie.getPath());
	}
}
