package emcshop.scraper;

import org.apache.http.client.CookieStore;

import com.github.mangstadt.emc.net.EmcWebsiteConnection;
import com.github.mangstadt.emc.net.EmcWebsiteConnectionImpl;

/**
 * Represents a login session to the EMC website.
 * @author Michael Angstadt
 */
public class EmcSession {
	private final CookieStore cookieStore;
	private final String username, password;

	public EmcSession(String username, String password, CookieStore cookieStore) {
		this.username = username;
		this.password = password;
		this.cookieStore = cookieStore;
	}

	/**
	 * Creates a new HTTP client for this session.
	 * @return the HTTP client
	 */
	public EmcWebsiteConnection createConnection() {
		return new EmcWebsiteConnectionImpl(cookieStore);
	}

	public CookieStore getCookieStore() {
		return cookieStore;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}
}
