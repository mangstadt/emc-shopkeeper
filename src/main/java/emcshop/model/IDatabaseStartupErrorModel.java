package emcshop.model;

import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.util.List;

public interface IDatabaseStartupErrorModel {
	void addRestoreCompleteListener(ActionListener listener);

	List<LocalDateTime> getBackups();

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

	Thread startRestore(LocalDateTime date);
}
