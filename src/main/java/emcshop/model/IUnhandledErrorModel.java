package emcshop.model;

public interface IUnhandledErrorModel {
	/**
	 * Gets the error message.
	 * @return the error message
	 */
	String getMessage();

	/**
	 * Gets the exception that was thrown.
	 * @return the thrown exception
	 */
	Throwable getThrown();

	/**
	 * Logs the error.
	 */
	void logError();

	/**
	 * Sends an error report.
	 */
	void sendErrorReport();
}
