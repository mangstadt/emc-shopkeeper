package emcshop.view;

import java.awt.event.ActionListener;
import java.util.Date;

import emcshop.scraper.EmcSession;

public interface IUpdateView {
	/**
	 * Queries the user for a new session token.
	 * @return the new session token
	 */
	EmcSession getNewSession();

	/**
	 * Shows an error that occurred during the download.
	 * @param thrown the error
	 * @return true if the user wants to save the transactions that have been
	 * parsed so false, false if the user wants to discard them
	 */
	boolean showDownloadError(Throwable thrown);

	/**
	 * Registers a listener that is called when the user cancels the download.
	 * @param listener
	 */
	void addCancelListener(ActionListener listener);

	/**
	 * Registers a listener that is called when the user stops the download.
	 * @param listener
	 */
	void addStopListener(ActionListener listener);

	/**
	 * Registers a listener that is called when the user wants to report an
	 * error that occurs during the download.
	 * @param listener
	 */
	void addReportErrorListener(ActionListener listener);

	/**
	 * Gets whether the user wants to see the transactions that were downloaded
	 * when the download completes.
	 * @return true to see the transactions, false not to
	 */
	boolean getShowResults();

	/**
	 * Sets whether this is the user's first update.
	 * @param firstUpdate true if it's the user's first update, false if not
	 */
	void setFirstUpdate(boolean firstUpdate);

	/**
	 * Sets the estimated amount of time the download will take.
	 * @param estimatedTime the estimated time in milliseconds or null if there
	 * is no estimate
	 */
	void setEstimatedTime(Long estimatedTime);

	/**
	 * Sets the page that the downloader will stop at.
	 * @param stopAtPage the stop at page or null if there is no stop at page
	 */
	void setStopAtPage(Integer stopAtPage);

	/**
	 * Sets the date of the oldest parsed transaction.
	 * @param date the date
	 */
	void setOldestParsedTransactonDate(Date date);

	/**
	 * Sets the number of pages that have been downloaded so far.
	 * @param pages
	 */
	void setPages(int pages);

	/**
	 * Sets the number of shop transactions that have been downloaded so far.
	 * @param count
	 */
	void setShopTransactions(int count);

	/**
	 * Sets the number of payment transactions that have been downloaded so far.
	 * @param count
	 */
	void setPaymentTransactions(int count);

	/**
	 * Sets the number of bonus/fee transactions that have been downloaded so
	 * far.
	 * @param count
	 */
	void setBonusFeeTransactions(int count);

	/**
	 * Resets the dialog back to its starting state.
	 */
	void reset();

	/**
	 * Shows the dialog.
	 */
	void display();

	/**
	 * Closes the dialog.
	 */
	void close();

}
