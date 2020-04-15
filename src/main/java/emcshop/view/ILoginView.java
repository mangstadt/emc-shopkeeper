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
	 * Sets the password
	 * @param password the password
	 */
	void setPassword(String password);

	/**
	 * Gets the two-factor authentication code.
	 * @return the code or null if the user didn't enter one
	 */
	String getTwoFactorAuthCode();

	/**
	 * Sets whether the user wants to save his password.
	 * @param savePassword true to remember the password, false not to
	 */
	void setSavePassword(boolean savePassword);

	/**
	 * Gets whether the user wants to save his password.
	 * @return true to remember the password, false not to
	 */
	boolean getSavePassword();

	/**
	 * Called if a network error occurs during login.
	 */
	void networkError();

	/**
	 * Called if the login credentials are invalid.
	 */
	void badLogin();

	/**
	 * Called if the user didn't enter a two-factor authentication code and one
	 * is required.
	 */
	void twoFactorAuthCodeRequired();

	/**
	 * Called if the two-factor authentication code the user entered is invalid.
	 */
	void badTwoFactorAuthCode();

	/**
	 * Closes the dialog.
	 */
	void close();

	/**
	 * Displays the dialog.
	 */
	void display();
}
