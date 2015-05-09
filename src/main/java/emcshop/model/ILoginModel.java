package emcshop.model;

import java.io.IOException;

import emcshop.scraper.EmcSession;

public interface ILoginModel {
	/**
	 * Logs a user into EMC
	 * @param username the username
	 * @param password the password
	 * @return the session token or null if the credentials were invalid
	 * @throws IOException
	 */
	String login(String username, String password) throws IOException;

	/**
	 * Gets the saved username.
	 * @return the saved username or null if not saved
	 */
	String getSavedUsername();

	/**
	 * Gets the saved password.
	 * @return the saved password or null if not saved
	 */
	String getSavedPassword();

	/**
	 * Saves the given session info.
	 * @param username the username to save
	 * @param password the password to save
	 */
	void saveSessionInfo(String username, String password);

	/**
	 * Gets the login session.
	 * @return the login session
	 */
	EmcSession getSession();

	/**
	 * Sets the login session.
	 * @param session the login session
	 */
	void setSession(EmcSession session);

	/**
	 * Logs a network error.
	 * @param error the network error
	 */
	void logNetworkError(IOException error);
}
