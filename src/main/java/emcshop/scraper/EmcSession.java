package emcshop.scraper;

import java.io.IOException;
import java.util.Date;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;

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
		this.created = new Date(created.getTime());
	}

	public static EmcSession login(String username, String password) throws IOException {
		String sessionId = LoginUtils.login(username, password);
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

	/**
	 * Creates a new HTTP client for this session.
	 * @return the HTTP client
	 */
	public HttpClient createHttpClient() {
		DefaultHttpClient client = new DefaultHttpClient();

		//set a high timeout because old transaction pages take a while to load
		HttpParams params = client.getParams();
		params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10 * 60 * 1000);
		params.setParameter(CoreConnectionPNames.SO_TIMEOUT, 10 * 60 * 1000);

		BasicClientCookie sessionCookie = LoginUtils.buildSessionCookie(sessionId);
		client.getCookieStore().addCookie(sessionCookie);
		return client;
	}
}
