package emcshop.view;

import java.awt.event.ActionListener;
import java.util.Date;
import java.util.List;

public interface IDatabaseStartupErrorView {
    /**
     * Adds a listener for when an error report is sent.
     *
     * @param listener the listener
     */
    void addSendErrorReportListener(ActionListener listener);

    void addStartRestoreListener(ActionListener listener);

    /**
     * Adds a listener for when the dialog is closed.
     *
     * @param listener the listener
     */
    void addCloseListener(ActionListener listener);

    Date getSelectedBackup();

    void setBackups(List<Date> backups);

    /**
     * Sets the exception that was thrown.
     *
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
