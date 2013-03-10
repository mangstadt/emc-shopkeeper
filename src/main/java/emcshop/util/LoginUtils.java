package emcshop.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * Logs the user in/out of EMC.
 * @author Michael Angstadt
 */
public class LoginUtils {
	private static final String SESSION_COOKIE_NAME = "emc_session";

	/**
	 * Logs the user in.
	 * @param username the username
	 * @param password the password
	 * @return the session ID or null if the credentials were invalid
	 * @throws IOException if there's a problem connecting to the website
	 */
	public static String login(String username, String password) throws IOException {
		DefaultHttpClient client = new DefaultHttpClient();
		client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);

		try {
			//load the home page in order to get the initial session cookie
			loadHomePage(client);

			//log the user in
			if (!login(username, password, client)) {
				return null;
			}

			//return the new session cookie that was generated
			List<Cookie> cookies = client.getCookieStore().getCookies();
			for (Cookie cookie : cookies) {
				if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
			return null;
		} finally {
			client.getConnectionManager().shutdown();
		}
	}

	/**
	 * Logs the user out.
	 * @param sessionId the session ID
	 * @throws IOException if there's a problem connecting to the website
	 */
	public static void logout(String sessionId) throws IOException {
		//TODO make logouts work
		//the request is successful, but the session ID doesn't get invalidated
		//don't know what's in the "_xfToken" parameter
		//URL: http://empireminecraft.com/logout/?_xfToken=12110%2C1362951239%2C66a8eb56001c9a53e3ba0d56b05750c3193817d3
		//                                                 ??   ,  timestamp ,  ??

		DefaultHttpClient client = new DefaultHttpClient();
		client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);

		BasicClientCookie cookie = new BasicClientCookie(SESSION_COOKIE_NAME, sessionId);
		cookie.setDomain(".empireminecraft.com");
		cookie.setPath("/");
		client.getCookieStore().addCookie(new BasicClientCookie(SESSION_COOKIE_NAME, sessionId));

		try {
			URIBuilder uri = new URIBuilder("http://empireminecraft.com/logout/");
			uri.addParameter("_xfToken", "12110," + (System.currentTimeMillis() / 1000) + ",abcd");

			HttpGet request = new HttpGet(uri.build());
			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();
			EntityUtils.consume(entity);
		} catch (URISyntaxException e) {
			//never thrown, hard-coded URL
		} finally {
			client.getConnectionManager().shutdown();
		}
	}

	/**
	 * Builds a map containing the login cookies.
	 * @param sessionId the session ID
	 * @return the cookies map
	 */
	public static Map<String, String> buildLoginCookiesMap(String sessionId) {
		Map<String, String> cookies = new HashMap<String, String>();
		cookies.put(SESSION_COOKIE_NAME, sessionId);
		return cookies;
	}

	private static void loadHomePage(DefaultHttpClient client) throws IOException {
		HttpGet request = new HttpGet("http://empireminecraft.com");
		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		EntityUtils.consume(entity);
	}

	private static boolean login(String username, String password, DefaultHttpClient client) throws IOException {
		HttpPost request = new HttpPost("http://empireminecraft.com/login/login");

		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("login", username));
		params.add(new BasicNameValuePair("password", password));
		request.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));

		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		EntityUtils.consume(entity);

		return response.getStatusLine().getStatusCode() == 303;
	}
}
