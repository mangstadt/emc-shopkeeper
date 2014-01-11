package emcshop.scraper;

import java.util.Date;

/**
 * Creates new instances of the {@link TransactionPuller} class.
 */
public class TransactionPullerFactory {
	private Date stopAtDate;
	private Integer stopAtPage;
	private Integer maxPaymentTransactionAge;
	private int threadCount = 4;
	private int startAtPage = 1;

	/**
	 * Gets the date at which it should stop parsing transactions. If not
	 * specified, the entire transaction history will be parsed (unless a
	 * stop-at-page is specified).
	 * @return the date to stop parsing transactions (exclusive)
	 */
	public Date getStopAtDate() {
		return stopAtDate;
	}

	/**
	 * Sets the date at which it should stop parsing transactions. If not
	 * specified, the entire transaction history will be parsed (unless a
	 * stop-at-page is specified).
	 * @param date the date to stop parsing transactions (exclusive)
	 */
	public void setStopAtDate(Date stopAtDate) {
		this.stopAtDate = stopAtDate;
	}

	/**
	 * Gets the page number to stop at. If not specified, the entire transaction
	 * history will be parsed (unless a stop-at-date is specified).
	 * @return the page number to stop at
	 */
	public Integer getStopAtPage() {
		return stopAtPage;
	}

	/**
	 * Sets the page number to stop at. If not specified, the entire transaction
	 * history will be parsed (unless a stop-at-date is specified).
	 * @param page the page number to stop at
	 */
	public void setStopAtPage(Integer stopAtPage) {
		this.stopAtPage = stopAtPage;
	}

	/**
	 * Gets the number of days old a payment transaction can be in order for it
	 * to be parsed.
	 * @return the number of days
	 */
	public Integer getMaxPaymentTransactionAge() {
		return maxPaymentTransactionAge;
	}

	/**
	 * Sets the number of days old a payment transaction can be in order for it
	 * to be parsed.
	 * @param days the number of days
	 */
	public void setMaxPaymentTransactionAge(Integer maxPaymentTransactionAge) {
		this.maxPaymentTransactionAge = maxPaymentTransactionAge;
	}

	/**
	 * Gets the number of threads to use for downloading and parsing transaction
	 * pages (defaults to 4).
	 * @return the number of threads
	 */
	public int getThreadCount() {
		return threadCount;
	}

	/**
	 * Sets the number of threads to use for downloading and parsing transaction
	 * pages (defaults to 4).
	 * @param threadCount the number of threads
	 */
	public void setThreadCount(int threadCount) {
		this.threadCount = threadCount;
	}

	/**
	 * Gets the page to start on (defaults to "1").
	 * @return the page number to start on
	 */
	public int getStartAtPage() {
		return startAtPage;
	}

	/**
	 * Sets the page to start on (defaults to "1").
	 * @param page the page number to start on
	 */
	public void setStartAtPage(int startAtPage) {
		this.startAtPage = startAtPage;
	}

	/**
	 * Creates a new {@link TransactionPuller} instance.
	 * @return the new instance
	 */
	public TransactionPuller newInstance() {
		return new TransactionPuller(this);
	}
}
