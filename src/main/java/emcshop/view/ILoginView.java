package emcshop.view;

import java.awt.event.ActionListener;

public interface ILoginView {
	/**
	 * Adds a listener for when the credentials are entered.
	 * @param listener the listener to add
	 */
	void addOnLoginListener(ActionListener listener);

	/**
	 * Adds a listener for when the dialog is canceled.
	 * @param listener the listener to add
	 */
	void addOnCancelListener(ActionListener listener);

	/**
	 * Sets the username.
	 * @param username the username
	 */
	void setUsername(String username);

	/**
	 * Gets the username.
	 * @return the username
	 */
	String getUsername();

	/**
	 * Gets the password.
	 * @return the password
	 */
	String getPassword();

	/**
	 * Sets whether the user wants to remember the session.
	 * @param rememberMe true to remember the session, false not to
	 */
	void setRememberMe(boolean rememberMe);

	/**
	 * Gets whether the user wants to remember the login session.
	 * @return true to remember the session, false not to
	 */
	boolean getRememberMe();

	/**
	 * Called if a network error occurs during login.
	 */
	void networkError();

	/**
	 * Called if the login credentials are invalid.
	 */
	void badLogin();

	/**
	 * Closes the dialog.
	 */
	void close();

	/**
	 * Displays the dialog.
	 */
	void display();
}
