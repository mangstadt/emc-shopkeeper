package emcshop.scraper;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import emcshop.util.LoginUtils;

/**
 * Represents a login session to the EMC website.
 * @author Michael Angstadt
 */
public class EmcSession {
	private final String username;
	private final String sessionId;
	private final Date created;

	public EmcSession(String username, String sessionId, Date created) {
		this.sessionId = sessionId;
		this.username = username;
		this.created = created;
	}

	public static EmcSession login(String username, String password, boolean rememberMe) throws IOException {
		String sessionId = LoginUtils.login(username, password, rememberMe);
		return (sessionId == null) ? null : new EmcSession(username, sessionId, new Date());
	}

	public void logout() throws IOException {
		LoginUtils.logout(sessionId);
	}

	public String getUsername() {
		return username;
	}

	public String getSessionId() {
		return sessionId;
	}

	public Date getCreated() {
		return created;
	}

	public Map<String, String> getCookiesMap() {
		return LoginUtils.buildLoginCookiesMap(sessionId);
	}
}
