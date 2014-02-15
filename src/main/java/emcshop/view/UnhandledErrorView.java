package emcshop.view;

import java.awt.event.ActionListener;

public interface UnhandledErrorView {
	/**
	 * Adds a listener for when an error report is sent.
	 * @param listener the listener
	 */
	void addSendErrorReportListener(ActionListener listener);

	/**
	 * Adds a listener for when the dialog is closed.
	 * @param listener the listener
	 */
	void addCloseListener(ActionListener listener);

	/**
	 * Sets the error message
	 * @param message the message
	 */
	void setMessage(String message);

	/**
	 * Sets the exception that was thrown.
	 * @param thrown the thrown exception
	 */
	void setThrown(Throwable thrown);

	/**
	 * Called when an error report was successfully sent.
	 */
	void errorReportSent();

	/**
	 * Displays the dialog.
	 */
	void display();

	/**
	 * Closes the dialog.
	 */
	void close();
}
