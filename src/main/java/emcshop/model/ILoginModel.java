package emcshop.model;

import java.io.IOException;

import com.github.mangstadt.emc.net.InvalidCredentialsException;
import com.github.mangstadt.emc.net.TwoFactorAuthException;

import emcshop.scraper.EmcSession;

public interface ILoginModel {
	/**
	 * Logs a user into EMC
	 * @param username the username
	 * @param password the password
	 * @param twoFactorAuthCode the two-factor authentication code or null if
	 * the user does not have two-factor authentication enabled
	 * @return the session token
	 * @throws InvalidCredentialsException if the username/password is incorrect
	 * @throws TwoFactorAuthException if a two-factor authentication code is
	 * required or if the provided code is invalid
	 * @throws IOException if there's a problem contacting the EMC website
	 */
	EmcSession login(String username, String password, String twoFactorAuthCode) throws InvalidCredentialsException, TwoFactorAuthException, IOException;

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
