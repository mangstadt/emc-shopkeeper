package emcshop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Downloads and parses transactions from the website.
 * @author Michael Angstadt
 */
public class TransactionPuller {
	private static final Logger logger = Logger.getLogger(TransactionPuller.class.getName());
	private final Map<String, String> loginCookies;
	private Date stopAtDate;
	private Integer stopAtPage;
	private Integer maxPaymentTransactionAge;
	private Date oldestPaymentTransactionDate;
	private int threadCount = 4;
	private Date latestTransactionDate;
	private Integer rupeeBalance;
	private AtomicInteger curPage;
	private int pageCount, transactionCount;
	private long started;
	private Throwable thrown = null;
	private boolean cancel = false;

	/**
	 * @param session the website login session
	 */
	public TransactionPuller(EmcSession session) {
		loginCookies = session.getCookiesMap();
		setStartAtPage(1);
	}

	/**
	 * Sets the date at which it should stop parsing transactions. If not
	 * specified, it will parse the entire history.
	 * @param date the date to stop parsing transactions (exclusive)
	 */
	public void setStopAtDate(Date date) {
		this.stopAtDate = date;
	}

	/**
	 * Sets the page number to stop at.
	 * @param page the page number to stop at (inclusive, starts at "1") or null
	 * to not stop
	 */
	public void setStopAtPage(Integer page) {
		this.stopAtPage = page;
	}

	/**
	 * Sets the number of days old a payment transaction can be in order for it
	 * to be parsed.
	 * @param days the number of days
	 */
	public void setMaxPaymentTransactionAge(Integer days) {
		this.maxPaymentTransactionAge = days;
	}

	/**
	 * Sets the page to start on (defaults to "1").
	 * @param page the page number to start on
	 */
	public void setStartAtPage(int page) {
		curPage = new AtomicInteger(page);
	}

	/**
	 * Sets the number of simultaneous transaction page downloads that can occur
	 * at once (defaults to 4).
	 * @param threadCount the number of simultaneous page downloads
	 */
	public void setThreadCount(int threadCount) {
		this.threadCount = threadCount;
	}

	/**
	 * Starts the download.
	 * @param listener for handling events
	 * @throws IOException if there's a network error
	 */
	public Result start(Listener listener) throws IOException {
		started = System.currentTimeMillis();

		//calculate how old a payment transaction can be before it is ignored
		if (maxPaymentTransactionAge != null) {
			Calendar c = Calendar.getInstance();
			c.add(Calendar.DATE, -maxPaymentTransactionAge);
			oldestPaymentTransactionDate = c.getTime();
		}

		TransactionPage firstPage = getPage(1);

		//is the user logged in?		
		if (!firstPage.isLoggedIn()) {
			return Result.notLoggedIn();
		}

		//get the rupee balance
		rupeeBalance = firstPage.getRupeeBalance();

		//get the date of the latest transaction
		latestTransactionDate = firstPage.getFirstTransactionDate();

		//start threads
		List<ScrapeThread> threads = new ArrayList<ScrapeThread>(threadCount);
		for (int i = 0; i < threadCount; i++) {
			ScrapeThread thread = new ScrapeThread(listener);
			thread.setDaemon(true);
			threads.add(thread);
			thread.start();
		}

		//wait for threads to finish
		for (ScrapeThread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				logger.log(Level.WARNING, "Thread interrupted.", e);
			}
		}

		if (thrown != null) {
			return Result.failed(thrown);
		} else if (cancel) {
			return Result.cancelled();
		} else {
			long timeTaken = System.currentTimeMillis() - started;
			return Result.completed(rupeeBalance, pageCount, transactionCount, timeTaken);
		}
	}

	/**
	 * Cancels the download operation.
	 */
	public void cancel() {
		cancel = true;
	}

	public boolean isCanceled() {
		return cancel;
	}

	protected TransactionPage getPage(int page) throws IOException {
		/*
		 * Note: The HttpClient library is used here because using
		 * "Jsoup.connect()" doesn't always work when the application is run as
		 * a Web Start app.
		 * 
		 * The login dialog was repeatedly appearing because, even though the
		 * login was successful (a valid session cookie was generated), the
		 * TransactionPuller would fail when it tried to get the first
		 * transaction from the first page (i.e. when calling "isLoggedIn()").
		 * It was failing because it was getting back the unauthenticated
		 * version of the rupee page. It was as if jsoup wasn't sending the
		 * session cookie with the request.
		 * 
		 * The issue appeared to only occur when running under Web Start. It
		 * could not be reproduced when running via Eclipse.
		 */

		String base = "http://empireminecraft.com/rupees/transactions/";
		String url = base + "?page=" + page;

		DefaultHttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet(url);
		for (Map.Entry<String, String> cookie : loginCookies.entrySet()) {
			String name = cookie.getKey();
			String value = cookie.getValue();
			request.addHeader("Cookie", name + "=" + value);
		}

		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		Document document = Jsoup.parse(entity.getContent(), "UTF-8", base);
		EntityUtils.consume(entity);

		return new TransactionPage(document);
	}

	private class ScrapeThread extends Thread {
		private final Listener listener;

		public ScrapeThread(Listener listener) {
			this.listener = listener;
		}

		@Override
		public void run() {
			try {
				boolean quit = false;
				while (!cancel && !quit) {
					int page = curPage.getAndIncrement();

					if (stopAtPage != null && page > stopAtPage) {
						break;
					}

					TransactionPage transactionPage = getPage(page);

					//EMC will load the first page if an invalid page number is given (i.e. if we've reached the last page)
					boolean lastPageReached = page > 1 && transactionPage.getFirstTransactionDate().getTime() >= latestTransactionDate.getTime();
					if (lastPageReached) {
						logger.info("Page " + page + " doesn't exist (page " + (page - 1) + " is the last page).");
						break;
					}

					List<RupeeTransaction> transactions = transactionPage.getTransactions();

					//remove old payment transactions
					if (oldestPaymentTransactionDate != null) {
						for (int i = 0; i < transactions.size(); i++) {
							RupeeTransaction transaction = transactions.get(i);
							if (!(transaction instanceof PaymentTransaction)) {
								continue;
							}

							long ts = transaction.getTs().getTime();
							if (ts < oldestPaymentTransactionDate.getTime()) {
								transactions.remove(i);
								i--;
								break;
							}
						}
					}

					//ignore any transactions that are past the "stop-at" date
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
							transactions = transactions.subList(0, end);
							quit = true;
						}
					}

					synchronized (TransactionPuller.this) {
						pageCount++;
						transactionCount += transactions.size() + transactions.size();
					}

					if (!cancel) {
						listener.onPageScraped(page, transactions);
					}
				}
			} catch (Throwable e) {
				thrown = e;
				cancel = true;
			}
		}
	}

	public static abstract class Listener {
		/**
		 * Called when a page has been scraped.
		 * @param page the page number
		 * @param transactions the scraped transactions (may be empty)
		 */
		public abstract void onPageScraped(int page, List<RupeeTransaction> transactions);

		public <T extends RupeeTransaction> List<T> filter(List<RupeeTransaction> transactions, Class<T> filterBy) {
			List<T> filtered = new ArrayList<T>();
			for (RupeeTransaction transaction : transactions) {
				if (transaction.getClass() == filterBy) {
					filtered.add(filterBy.cast(transaction));
				}
			}
			return filtered;
		}
	}

	public static class Result {
		private final State state;
		private Throwable thrown;
		private int pageCount;
		private int transactionCount;
		private long timeTaken;
		private Integer rupeeBalance;

		private Result(State state) {
			this.state = state;
		}

		public State getState() {
			return state;
		}

		public Throwable getThrown() {
			return thrown;
		}

		public int getPageCount() {
			return pageCount;
		}

		public int getTransactionCount() {
			return transactionCount;
		}

		public long getTimeTaken() {
			return timeTaken;
		}

		public Integer getRupeeBalance() {
			return rupeeBalance;
		}

		public static Result cancelled() {
			return new Result(State.CANCELLED);
		}

		public static Result notLoggedIn() {
			return new Result(State.NOT_LOGGED_IN);
		}

		public static Result failed(Throwable thrown) {
			Result result = new Result(State.FAILED);
			result.thrown = thrown;
			return result;
		}

		public static Result completed(Integer rupeeBalance, int pageCount, int transactionCount, long timeTaken) {
			Result result = new Result(State.COMPLETED);
			result.rupeeBalance = rupeeBalance;
			result.pageCount = pageCount;
			result.transactionCount = transactionCount;
			result.timeTaken = timeTaken;
			return result;
		}
	}

	public static enum State {
		CANCELLED, NOT_LOGGED_IN, FAILED, COMPLETED
	}
}
