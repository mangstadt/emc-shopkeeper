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

	public EmcSession(CookieStore cookieStore) {
		this.cookieStore = cookieStore;
	}

	/**
	 * Creates a new HTTP client for this session.
	 * @return the HTTP client
	 */
	public EmcWebsiteConnection createConnection() {
		return new EmcWebsiteConnectionImpl(cookieStore);
	}
}
