package emcshop.model;

import java.awt.event.ActionListener;
import java.time.Duration;
import java.time.LocalDateTime;

import emcshop.scraper.EmcSession;

public interface IUpdateModel {
	/**
	 * Registers a listener that is called when a transaction page is
	 * downloaded.
	 * @param listener
	 */
	void addPageDownloadedListener(ActionListener listener);

	/**
	 * Registers a listener that is called if the session token is invalid.
	 * @param listener
	 */
	void addBadSessionListener(ActionListener listener);

	/**
	 * Registers a listener that is called if an error occurs during the
	 * download.
	 * @param listener
	 */
	void addDownloadErrorListener(ActionListener listener);

	/**
	 * Registers a listener that is called when the download completed
	 * successfully.
	 * @param listener
	 */
	void addDownloadCompleteListener(ActionListener listener);

	/**
	 * Gets whether this is the user's first update or not.
	 * @return true if it's the user's first update, false if not
	 */
	boolean isFirstUpdate();

	/**
	 * Gets the estimated amount of time it will take to perform the download.
	 * @return the estimated time in milliseconds or null if it can't be
	 * computed
	 */
	Duration getEstimatedTime();

	/**
	 * Gets the page that the downloader is configured to stop at.
	 * @return the stop at page or null if none is set
	 */
	Integer getStopAtPage();

	/**
	 * Assigns a session token to the downloader.
	 * @param session
	 */
	void setSession(EmcSession session);

	/**
	 * Gets the number of transaction pages that have been downloaded.
	 * @return the number of downloaded pages
	 */
	int getPagesDownloaded();

	/**
	 * Gets the number of shop transactions that were downloaded.
	 * @return the number of shop transactions
	 */
	int getShopTransactionsDownloaded();

	/**
	 * Gets the number of payment transactions that were downloaded.
	 * @return the number of payment transactions
	 */
	int getPaymentTransactionsDownloaded();

	/**
	 * Gets the number of bonus/fee transactions that were downloaded.
	 * @return the number of bonus/fee transactions
	 */
	int getBonusFeeTransactionsDownloaded();

	/**
	 * Gets the date of the oldest parsed transaction.
	 * @return the date of the oldest transaction
	 */
	LocalDateTime getOldestParsedTransactionDate();

	/**
	 * Gets the time that the download started.
	 * @return the download start time
	 */
	LocalDateTime getStarted();

	/**
	 * Gets the amount of time the download took to complete.
	 * @return the amount of time
	 */
	Duration getTimeTaken();

	/**
	 * Gets the player's parsed rupee balance.
	 * @return the rupee balance or null if not found
	 */
	Integer getRupeeBalance();

	/**
	 * Gets the error that occurred during the update, if any.
	 * @return the error or null if no error occured
	 */
	Throwable getDownloadError();

	/**
	 * Starts the download. This call is non-blocking.
	 * @return the thread that was spawned
	 */
	Thread startDownload();

	/**
	 * Stops the download. This call should be non-blocking.
	 */
	void stopDownload();

	/**
	 * Saves the transactions that have been parsed.
	 */
	void saveTransactions();

	/**
	 * Discards the transactions that have been parsed.
	 */
	void discardTransactions();

	/**
	 * Sends an error report if an error occured during the download.
	 */
	void reportError();
}
