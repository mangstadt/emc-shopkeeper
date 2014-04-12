package emcshop.scraper;

import java.io.IOException;
import java.util.Date;

/**
 * Creates new instances of {@link TransactionPuller} objects.
 */
public class TransactionPullerFactory {
	private Date stopAtDate;
	private Integer stopAtPage;
	private int startAtPage = 1;
	private Integer maxPaymentTransactionAge;
	private int threadCount = 4;
	private TransactionPageScraper pageScraper = new TransactionPageScraper();

	/**
	 * Gets the date at which the puller should stop parsing transactions.
	 * @return the stop date or null for no end date
	 */
	public Date getStopAtDate() {
		return (stopAtDate == null) ? null : new Date(stopAtDate.getTime());
	}

	/**
	 * Sets the date at which the puller should stop parsing transactions
	 * (defaults to no end date).
	 * @param stopAtDate the stop date or null for no end date
	 */
	public void setStopAtDate(Date stopAtDate) {
		this.stopAtDate = (stopAtDate == null) ? null : new Date(stopAtDate.getTime());
	}

	/**
	 * Gets the page at which the puller should stop parsing transactions.
	 * @return the stop page (inclusive) or null to parse all pages
	 */
	public Integer getStopAtPage() {
		return stopAtPage;
	}

	/**
	 * Sets the page at which the puller should stop parsing transactions
	 * (defaults to all pages).
	 * @param stopAtPage the stop page (inclusive) or null to parse all pages
	 */
	public void setStopAtPage(Integer stopAtPage) {
		if (stopAtPage != null && stopAtPage <= 0) {
			throw new IllegalArgumentException("Stop page must be greater than zero.");
		}
		this.stopAtPage = stopAtPage;
	}

	/**
	 * Gets the page at which the puller should start parsing transactions.
	 * @return the start page
	 */
	public int getStartAtPage() {
		return startAtPage;
	}

	/**
	 * Sets the page at which the puller should start parsing transactions
	 * (defaults to 1).
	 * @param startAtPage the start page
	 */
	public void setStartAtPage(int startAtPage) {
		if (startAtPage <= 0) {
			throw new IllegalArgumentException("Start page must be greater than zero.");
		}
		this.startAtPage = startAtPage;
	}

	/**
	 * Gets the number of number of days old a payment transaction can be before
	 * it is ignored.
	 * @return the number of days or null if disabled
	 */
	public Integer getMaxPaymentTransactionAge() {
		return maxPaymentTransactionAge;
	}

	/**
	 * Sets the number of number of days old a payment transaction can be before
	 * it is ignored (disabled by default).
	 * @param maxPaymentTransactionAge the number of days or null to disable
	 */
	public void setMaxPaymentTransactionAge(Integer maxPaymentTransactionAge) {
		if (maxPaymentTransactionAge != null && maxPaymentTransactionAge <= 0) {
			throw new IllegalArgumentException("Max payment transaction age must be greater than zero.");
		}
		this.maxPaymentTransactionAge = maxPaymentTransactionAge;
	}

	/**
	 * Gets the number of background threads to spawn for downloading and
	 * parsing the transactions.
	 * @return the thread count
	 */
	public int getThreadCount() {
		return threadCount;
	}

	/**
	 * Sets the number of background threads to spawn for downloading and
	 * parsing the transactions (defaults to 4).
	 * @param threadCount the thread count
	 */
	public void setThreadCount(int threadCount) {
		if (threadCount <= 0) {
			throw new IllegalArgumentException("Thread count must be greater than zero.");
		}
		this.threadCount = threadCount;
	}

	/**
	 * Gets the object used to download and scrape transaction pages.
	 * @return the page scraper
	 */
	public TransactionPageScraper getTransactionPageScraper() {
		return pageScraper;
	}

	/**
	 * Sets the object used to download and scrape transaction pages.
	 * @param pageScraper the page scraper
	 */
	public void setTransactionPageScraper(TransactionPageScraper pageScraper) {
		this.pageScraper = pageScraper;
	}

	/**
	 * Creates a new {@link TransactionPuller} instance.
	 * @param session the login session
	 * @return the new instance
	 * @throws IOException if there's a problem initializing the instance
	 */
	public TransactionPuller create(EmcSession session) throws IOException {
		if (stopAtPage != null && startAtPage > stopAtPage) {
			throw new IllegalArgumentException("Start page must come before stop page.");
		}
		return new TransactionPuller(session, this);
	}
}
