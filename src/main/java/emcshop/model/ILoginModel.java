package emcshop.model;

import java.io.IOException;

import emcshop.scraper.EmcSession;

public interface ILoginModel {
	/**
	 * Logs a user into EMC
	 * @param username the username
	 * @param password the password
	 * @param rememberMe true if "remember me" is checked
	 * @return the session token or null if the credentials were invalid
	 * @throws IOException
	 */
	String login(String username, String password, boolean rememberMe) throws IOException;

	/**
	 * Gets the saved username.
	 * @return the saved username or null if not saved
	 */
	String getSavedUsername();

	/**
	 * Gets the saved "remember me" value.
	 * @return the saved "remember me" value
	 */
	boolean getSavedRememberMe();

	/**
	 * Saves the given session.
	 * @param session the session to save
	 * @param rememberMe whether to remember the session or not
	 */
	void saveSession(EmcSession session, boolean rememberMe);

	/**
	 * Gets the login session.
	 * @return the login session
	 */
	EmcSession getSession();

	/**
	 * Logs a network error.
	 * @param error the network error
	 */
	void logNetworkError(IOException error);
}
