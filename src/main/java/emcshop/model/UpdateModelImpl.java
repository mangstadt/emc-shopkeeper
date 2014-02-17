package emcshop.model;

import static emcshop.scraper.TransactionPuller.filter;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import emcshop.Main;
import emcshop.db.DbDao;
import emcshop.scraper.BadSessionException;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.EmcSession;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.RupeeTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.scraper.TransactionPuller;
import emcshop.util.GuiUtils;

public class UpdateModelImpl implements IUpdateModel {
	private static final Logger logger = Logger.getLogger(UpdateModelImpl.class.getName());

	private final boolean firstUpdate;
	private final TransactionPuller.Config pullerConfig;
	private EmcSession session;
	private final DbDao dao;

	private final List<ActionListener> pageDownloadedListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> badSessionListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> downloadErrorListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> downloadCompleteListeners = new ArrayList<ActionListener>();

	private TransactionPuller puller;
	private long started, timeTaken;
	private int transactionsCount, shopTransactionsCount, paymentTransactionsCount, bonusFeeTransactionsCount, pagesCount;
	private Date earliestParsedTransactionDate;
	private boolean downloadStopped = false;
	private Throwable thrown;

	public UpdateModelImpl(TransactionPuller.Config pullerConfig, EmcSession session, DbDao dao) {
		firstUpdate = (pullerConfig.getStopAtDate() == null);
		this.pullerConfig = pullerConfig;
		this.session = session;
		this.dao = dao;
	}

	@Override
	public void addPageDownloadedListener(ActionListener listener) {
		pageDownloadedListeners.add(listener);
	}

	@Override
	public void addBadSessionListener(ActionListener listener) {
		badSessionListeners.add(listener);
	}

	@Override
	public void addDownloadErrorListener(ActionListener listener) {
		downloadErrorListeners.add(listener);
	}

	@Override
	public void addDownloadCompleteListener(ActionListener listener) {
		downloadCompleteListeners.add(listener);
	}

	@Override
	public boolean isFirstUpdate() {
		return firstUpdate;
	}

	@Override
	public Long getEstimatedTime() {
		Integer stopAtPage = getStopAtPage();
		return (stopAtPage == null) ? null : Main.estimateUpdateTime(stopAtPage);
	}

	@Override
	public Integer getStopAtPage() {
		return pullerConfig.getStopAtPage();
	}

	@Override
	public int getPagesDownloaded() {
		return pagesCount;
	}

	@Override
	public int getShopTransactionsDownloaded() {
		return shopTransactionsCount;
	}

	@Override
	public int getPaymentTransactionsDownloaded() {
		return paymentTransactionsCount;
	}

	@Override
	public int getBonusFeeTransactionsDownloaded() {
		return bonusFeeTransactionsCount;
	}

	@Override
	public Date getOldestParsedTransactionDate() {
		return earliestParsedTransactionDate;
	}

	@Override
	public Thread startDownload() {
		started = System.currentTimeMillis();

		DownloadThread thread = new DownloadThread();
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	@Override
	public synchronized void stopDownload() {
		downloadStopped = true;
		timeTaken = System.currentTimeMillis() - started;
	}

	@Override
	public void setSession(EmcSession session) {
		this.session = session;
	}

	@Override
	public Throwable getDownloadError() {
		return thrown;
	}

	@Override
	public Date getStarted() {
		return new Date(started);
	}

	@Override
	public long getTimeTaken() {
		return timeTaken;
	}

	@Override
	public Integer getRupeeBalance() {
		return puller.getRupeeBalance();
	}

	@Override
	public void saveTransactions() {
		if (transactionsCount == 0) {
			return;
		}

		try {
			if (earliestParsedTransactionDate != null) {
				dao.updateBonusesFeesSince(earliestParsedTransactionDate);
			}

			Integer rupeeBalance = getRupeeBalance();
			if (rupeeBalance != null) {
				dao.updateRupeeBalance(rupeeBalance);
			}

			dao.commit();
		} catch (SQLException e) {
			dao.rollback();
			throw new RuntimeException(e);
		}
	}

	@Override
	public void discardTransactions() {
		dao.rollback();
	}

	TransactionPuller createPuller() throws IOException {
		return new TransactionPuller(session, pullerConfig);
	}

	private class DownloadThread extends Thread {
		public DownloadThread() {
			setName(getClass().getSimpleName());
		}

		@Override
		public void run() {
			try {
				puller = createPuller();
			} catch (BadSessionException e) {
				GuiUtils.fireEvents(badSessionListeners);
				return;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			try {
				List<RupeeTransaction> transactions;
				while ((transactions = puller.nextPage()) != null) {
					synchronized (UpdateModelImpl.this) {
						if (downloadStopped) {
							puller.cancel();
							break;
						}

						//keep track of the oldest transaction date
						if (!transactions.isEmpty()) {
							RupeeTransaction last = transactions.get(transactions.size() - 1); //transactions are ordered date descending
							Date lastTs = last.getTs();
							if (earliestParsedTransactionDate == null || lastTs.before(earliestParsedTransactionDate)) {
								earliestParsedTransactionDate = lastTs;
							}
						}

						List<ShopTransaction> shopTransactions = filter(transactions, ShopTransaction.class);
						for (ShopTransaction shopTransaction : shopTransactions) {
							dao.insertTransaction(shopTransaction, true);
						}
						shopTransactionsCount += shopTransactions.size();

						List<PaymentTransaction> paymentTransactions = filter(transactions, PaymentTransaction.class);
						dao.insertPaymentTransactions(paymentTransactions);
						paymentTransactionsCount += paymentTransactions.size();

						List<BonusFeeTransaction> bonusFeeTransactions = filter(transactions, BonusFeeTransaction.class);
						dao.updateBonusesFees(bonusFeeTransactions);
						bonusFeeTransactionsCount += bonusFeeTransactions.size();

						transactionsCount = shopTransactionsCount + paymentTransactionsCount + bonusFeeTransactionsCount;

						pagesCount++;
					}

					GuiUtils.fireEvents(pageDownloadedListeners);
				}

				//update completed successfully
				synchronized (UpdateModelImpl.this) {
					if (downloadStopped) {
						return;
					}
					timeTaken = System.currentTimeMillis() - started;
				}

				GuiUtils.fireEvents(downloadCompleteListeners);
			} catch (Throwable t) {
				//an error occurred during the update
				synchronized (UpdateModelImpl.this) {
					timeTaken = System.currentTimeMillis() - started;

					if (!firstUpdate || transactionsCount == 0) {
						dao.rollback();
						throw new RuntimeException(t);
					}

					thrown = t;
					logger.log(Level.SEVERE, "Error downloading transactions.", t);
				}

				GuiUtils.fireEvents(downloadErrorListeners);
			}
		}
	}
}
