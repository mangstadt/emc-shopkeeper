package emcshop.model;

import java.awt.event.ActionListener;
import java.util.Date;
import java.util.List;

public interface IDatabaseStartupErrorModel {
    void addRestoreCompleteListener(ActionListener listener);

    List<Date> getBackups();

    /**
     * Gets the exception that was thrown.
     *
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

    Thread startRestore(Date date);
}
