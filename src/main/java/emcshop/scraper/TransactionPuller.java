package emcshop.scraper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.client.HttpClient;

/**
 * Downloads and parses transactions from EMC.
 * @author Michael Angstadt
 */
public class TransactionPuller {
	private static final Logger logger = Logger.getLogger(TransactionPuller.class.getName());
	private static final RupeeTransactions noMoreElements = new RupeeTransactions();

	private final BlockingQueue<RupeeTransactions> queue = new LinkedBlockingQueue<RupeeTransactions>();

	private final EmcSession session;
	private final Date stopAtDate;
	private final Integer stopAtPage;
	private final int threads;
	private final TransactionPageScraper pageScraper;

	private final Date oldestPaymentTransactionDate;
	private final Date latestTransactionDate;
	private final Integer rupeeBalance;
	private final AtomicInteger pageCounter;

	private int pageCount = 0, transactionCount = 0;
	private DownloadException thrown = null;

	private int deadThreads = 0;
	private boolean cancel = false;

	/**
	 * Constructs a new transaction puller.
	 * @param session the EMC session
	 * @throws BadSessionException if the EMC session was invalid
	 * @throws IOException if there was a network problem contacting EMC
	 */
	public TransactionPuller(EmcSession session) throws BadSessionException, IOException {
		this(session, new TransactionPullerFactory());
	}

	/**
	 * Constructs a new transaction puller.
	 * @param session the EMC session
	 * @param factory the transaction puller factory
	 * @throws BadSessionException if the EMC session was invalid
	 * @throws IOException if there was a network problem contacting EMC
	 */
	public TransactionPuller(EmcSession session, TransactionPullerFactory factory) throws IOException {
		this.session = session;
		stopAtDate = factory.getStopAtDate();
		stopAtPage = factory.getStopAtPage();
		threads = factory.getThreadCount();
		pageScraper = factory.getTransactionPageScraper();

		TransactionPage firstPage = pageScraper.download(1, session.createHttpClient());

		//is the user logged in?
		if (!firstPage.isLoggedIn()) {
			throw new BadSessionException();
		}

		//calculate how old a payment transaction can be before it is ignored
		Integer maxPaymentTransactionAge = factory.getMaxPaymentTransactionAge();
		if (maxPaymentTransactionAge != null) {
			Calendar c = Calendar.getInstance();
			c.add(Calendar.DATE, -maxPaymentTransactionAge);
			oldestPaymentTransactionDate = c.getTime();
		} else {
			oldestPaymentTransactionDate = null;
		}

		//get the rupee balance
		rupeeBalance = firstPage.getRupeeBalance();

		//get the date of the latest transaction so we know when we've reached the last transaction page
		latestTransactionDate = firstPage.getFirstTransactionDate();

		//start threads
		pageCounter = new AtomicInteger(factory.getStartAtPage());
		for (int i = 0; i < threads; i++) {
			ScrapeThread thread = new ScrapeThread();
			thread.setDaemon(true);
			thread.setName(getClass().getSimpleName() + "-" + i);
			thread.start();
		}
	}

	/**
	 * Gets the next page of transactions. Note that the pages are not
	 * guaranteed to be returned in any particular order.
	 * @return the next page or null if there are no more pages
	 * @throws DownloadException if an error occurred while downloading or
	 * parsing one of the pages
	 */
	public RupeeTransactions nextPage() throws DownloadException {
		synchronized (this) {
			if (queue.isEmpty()) {
				if (thrown != null) {
					throw thrown;
				}
				if (deadThreads == threads) {
					return null;
				}
			}
		}

		try {
			RupeeTransactions transactions = queue.take();
			if (transactions == noMoreElements) {
				synchronized (this) {
					if (thrown != null) {
						throw thrown;
					}
				}
				return null;
			}
			return transactions;
		} catch (InterruptedException e) {
			return null;
		}
	}

	/**
	 * Stops the background threads that are downloading the transactions.
	 */
	public synchronized void cancel() {
		cancel = true;
		queue.add(noMoreElements); //null values cannot be added to the queue
	}

	/**
	 * Determines if the background threads that are downloading the
	 * transactions have been canceled.
	 * @return true if they were canceled, false if not
	 */
	public boolean isCanceled() {
		return cancel;
	}

	/**
	 * Gets the rupee balance that was parsed from the first page.
	 * @return the rupee balance or null if it could not be found
	 */
	public Integer getRupeeBalance() {
		return rupeeBalance;
	}

	/**
	 * Gets the number of pages that have been downloaded and parsed.
	 * @return the page count
	 */
	public int getPageCount() {
		return pageCount;
	}

	/**
	 * Gets the number of transactions that have been downloaded and parsed.
	 * @return the transaction count
	 */
	public int getTransactionCount() {
		return transactionCount;
	}

	private class ScrapeThread extends Thread {
		private HttpClient client;

		public ScrapeThread() {
			this.client = session.createHttpClient();
		}

		@Override
		public void run() {
			int page = 0;
			try {
				while (true) {
					page = pageCounter.getAndIncrement();

					if (stopAtPage != null && page > stopAtPage) {
						break;
					}

					TransactionPage transactionPage = null;
					try {
						transactionPage = pageScraper.download(page, client);
					} catch (ConnectException e) {
						//one user reported getting connection errors at various points while trying to download 12k pages: http://empireminecraft.com/threads/shop-statistics.22507/page-14#post-684085
						//if there's a connection problem, try re-creating the connection
						logger.log(Level.WARNING, "A connection error occurred while downloading transactions.  Re-creating the connection.", e);
						client = session.createHttpClient();
						transactionPage = pageScraper.download(page, client);
					} catch (SocketTimeoutException e) {
						logger.log(Level.WARNING, "A connection error occurred while downloading transactions.  Re-creating the connection.", e);
						client = session.createHttpClient();
						transactionPage = pageScraper.download(page, client);
					}

					//the session shouldn't expire while a download is in progress, but run a check just in case something wonky happens
					if (!transactionPage.isLoggedIn()) {
						throw new BadSessionException();
					}

					//EMC will load the first page if an invalid page number is given (i.e. if we've reached the last page)
					boolean lastPageReached = page > 1 && transactionPage.getFirstTransactionDate().getTime() >= latestTransactionDate.getTime();
					if (lastPageReached) {
						logger.info("Page " + page + " doesn't exist (page " + (page - 1) + " is the last page).");
						break;
					}

					RupeeTransactions transactions = transactionPage.getTransactions();

					//remove old payment transactions
					if (oldestPaymentTransactionDate != null) {
						List<PaymentTransaction> toRemove = new ArrayList<PaymentTransaction>();
						for (PaymentTransaction transaction : transactions.find(PaymentTransaction.class)) {
							long ts = transaction.getTs().getTime();
							if (ts < oldestPaymentTransactionDate.getTime()) {
								toRemove.add(transaction);
							}
						}
						transactions.removeAll(toRemove);
					}

					//remove any transactions that are past the "stop-at" date
					boolean done = false;
					if (stopAtDate != null) {
						int end = -1;
						for (int i = 0; i < transactions.size(); i++) {
							RupeeTransaction transaction = transactions.get(i);
							long ts = transaction.getTs().getTime();

							if (ts <= stopAtDate.getTime()) {
								end = i;
								break;
							}
						}
						if (end >= 0) {
							transactions.subList(end, transactions.size()).clear();
							done = true;
						}
					}

					synchronized (TransactionPuller.this) {
						if (cancel) {
							return;
						}
						pageCount++;
						transactionCount += transactions.size();

						queue.add(transactions);
					}

					if (done) {
						break;
					}
				}
			} catch (Throwable t) {
				synchronized (TransactionPuller.this) {
					if (cancel) {
						return;
					}
					thrown = new DownloadException(page, t);
					cancel();
				}
			} finally {
				synchronized (TransactionPuller.this) {
					deadThreads++;
					if (!cancel && deadThreads == threads) {
						//this is the last thread to terminate
						queue.add(noMoreElements);
					}
				}
			}
		}
	}
}
