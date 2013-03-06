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
	private Date stopAt;
	private Date latestTransactionDate;
	private final Map<String, String> cookies;
	private int curPage = 1;
	private Throwable thrown = null;
	private boolean cancel = false;

	/**
	 * @param cookies cookies for logging the user in
	 */
	public TransactionPuller(Map<String, String> cookies) {
		this.cookies = cookies;
	}

	/**
	 * Sets the date at which it should stop parsing transactions. If not
	 * specified, it will parse the entire history.
	 * @param stopAt the date to stop parsing transactions (exclusive)
	 */
	public void setStopAtDate(Date stopAt) {
		this.stopAt = stopAt;
	}

	/**
	 * Starts the download.
	 * @param listener for handling events
	 * @throws IOException if there's a network error
	 */
	public void start(TransactionPullerListener listener) throws IOException {
		if (stopAt == null) {
			//database is empty
			//keep scraping until there are no more pages
			//since EMC will just display the first page if we give it too large of a page number, we need to know when the first page has been loaded
			latestTransactionDate = getLatestTransactionDate();
		}

		//start threads
		int threadCount = 4;
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
			listener.onError(thrown);
		} else if (cancel) {
			listener.onCancel();
		} else {
			listener.onSuccess();
		}
	}

	/**
	 * Cancels the download operation.
	 */
	public void cancel() {
		cancel = true;
	}

	private Date getLatestTransactionDate() throws IOException {
		TransactionPage page = new TransactionPage(getPage(1));
		return page.getFirstTransactionDate();
	}

	private synchronized int nextPage() {
		return curPage++;
	}

	protected Document getPage(int page) throws IOException {
		String url = "http://empireminecraft.com/rupees/transactions/?page=" + page;
		return Jsoup.connect(url).timeout(30000).cookies(cookies).get();
	}

	private class ScrapeThread extends Thread {
		private TransactionPullerListener listener;

		public ScrapeThread(TransactionPullerListener listener) {
			this.listener = listener;
		}

		@Override
		public void run() {
			try {
				boolean quit = false;
				while (!cancel && !quit) {
					int page = nextPage();

					logger.info("Getting page " + page + ".");
					Document document = getPage(page);
					TransactionPage transactionPage = new TransactionPage(document);
					List<ShopTransaction> transactions = transactionPage.getShopTransactions();

					if (stopAt != null) {
						int end = -1;
						for (int i = 0; i < transactions.size(); i++) {
							ShopTransaction transaction = transactions.get(i);
							if (transaction.getTs().getTime() <= stopAt.getTime()) {
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

					listener.onPageScraped(page, transactions);
				}
			} catch (Throwable e) {
				thrown = e;
				cancel = true;
			}
		}
	}
}
