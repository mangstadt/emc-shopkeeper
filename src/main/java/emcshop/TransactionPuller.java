package emcshop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	private int threadCount = 4;
	private Date latestTransactionDate;
	private int curPage = 1;
	private int pageCount, transactionCount;
	private long started;
	private Throwable thrown = null;
	private boolean cancel = false;

	/**
	 * @param session the website login session
	 */
	public TransactionPuller(EmcSession session) {
		loginCookies = session.getCookiesMap();
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
	 * Sets the page to start on (defaults to "1").
	 * @param page the page number to start on
	 */
	public void setStartAtPage(int page) {
		this.curPage = page;
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

		TransactionPage page1 = new TransactionPage(getPage(1));
		if (!page1.isLoggedIn()) {
			return Result.notLoggedIn();
		}

		if (stopAtDate == null) {
			//database is empty
			//keep scraping until there are no more pages
			//since EMC will just display the first page if we give it too large of a page number, we need to know when the first page has been loaded
			latestTransactionDate = page1.getFirstTransactionDate();
		}

		//start threads
		List<ScrapeThread> threads = new ArrayList<ScrapeThread>(threadCount);
		for (int i = 0; i < threadCount; i++) {
			ScrapeThread thread = new ScrapeThread(listener);
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
			return Result.completed(pageCount, transactionCount, timeTaken);
		}
	}

	/**
	 * Cancels the download operation.
	 */
	public void cancel() {
		cancel = true;
	}

	private synchronized int nextPage() {
		return curPage++;
	}

	protected Document getPage(int page) throws IOException {
		String url = "http://empireminecraft.com/rupees/transactions/?page=" + page;
		return Jsoup.connect(url).timeout(30000).cookies(loginCookies).get();
	}

	private class ScrapeThread extends Thread {
		private Listener listener;

		public ScrapeThread(Listener listener) {
			this.listener = listener;
		}

		@Override
		public void run() {
			try {
				boolean quit = false;
				while (!cancel && !quit) {
					int page = nextPage();

					if (stopAtPage != null && page > stopAtPage) {
						break;
					}

					logger.fine("Getting page " + page + ".");
					Document document = getPage(page);
					TransactionPage transactionPage = new TransactionPage(document);
					List<ShopTransaction> transactions = transactionPage.getShopTransactions();

					if (stopAtDate != null) {
						int end = -1;
						for (int i = 0; i < transactions.size(); i++) {
							ShopTransaction transaction = transactions.get(i);
							if (transaction.getTs().getTime() <= stopAtDate.getTime()) {
								end = i;
								break;
							}
						}
						if (end >= 0) {
							transactions = transactions.subList(0, end);
							quit = true;
						}
					} else if (latestTransactionDate != null && page > 1) {
						int end = -1;
						for (int i = 0; i < transactions.size(); i++) {
							ShopTransaction transaction = transactions.get(i);
							if (transaction.getTs().getTime() >= latestTransactionDate.getTime()) {
								end = i;
								break;
							}
						}
						if (end >= 0) {
							transactions = transactions.subList(0, end);
							quit = true;
						}
					}

					synchronized (this) {
						pageCount++;
						transactionCount += transactions.size();
					}

					listener.onPageScraped(page, transactions);
				}
			} catch (Throwable e) {
				thrown = e;
				cancel = true;
			}
		}
	}

	public static interface Listener {
		/**
		 * Called when a page has been scraped.
		 * @param page the page number
		 * @param transactions the scraped shop transactions (may be empty)
		 */
		void onPageScraped(int page, List<ShopTransaction> transactions);
	}

	public static class Result {
		private final State state;
		private Throwable thrown;
		private int pageCount;
		private int transactionCount;
		private long timeTaken;

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

		public static Result completed(int pageCount, int transactionCount, long timeTaken) {
			Result result = new Result(State.COMPLETED);
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
