package emcshop.model;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableInt;

import com.github.mangstadt.emc.net.InvalidCredentialsException;
import com.github.mangstadt.emc.rupees.RupeeTransactionReader;
import com.github.mangstadt.emc.rupees.dto.PaymentTransaction;
import com.github.mangstadt.emc.rupees.dto.RupeeTransaction;
import com.github.mangstadt.emc.rupees.dto.ShopTransaction;

import emcshop.AppContext;
import emcshop.EMCShopkeeper;
import emcshop.ItemIndex;
import emcshop.ReportSender;
import emcshop.db.DbDao;
import emcshop.db.PaymentTransactionDb;
import emcshop.db.ShopTransactionDb;
import emcshop.scraper.EmcSession;
import emcshop.util.Listeners;
import emcshop.util.TimeUtils;

public class UpdateModelImpl implements IUpdateModel {
	private static final Logger logger = Logger.getLogger(UpdateModelImpl.class.getName());
	private static final AppContext context = AppContext.instance();

	private final ItemIndex itemIndex = ItemIndex.instance();
	private final boolean firstUpdate;
	private final RupeeTransactionReader.Builder builder;
	private final Duration oldestAllowablePaymentTransactionAge;
	private final DbDao dao;
	private final ReportSender reportSender;

	private final Listeners pageDownloadedListeners = new Listeners();
	private final Listeners badSessionListeners = new Listeners();
	private final Listeners downloadErrorListeners = new Listeners();
	private final Listeners downloadCompleteListeners = new Listeners();

	private RupeeTransactionReader reader;
	private LocalDateTime started;
	private Duration timeTaken;
	private int transactionsCount, shopTransactionsCount, paymentTransactionsCount, bonusFeeTransactionsCount, pagesCount;
	private LocalDateTime earliestParsedTransactionDate, latestParsedBonusFeeDate;
	private RupeeTransaction highestBalance;
	private Map<Class<? extends RupeeTransaction>, MutableInt> bonusFeeTotals;
	private boolean downloadStopped = false;
	private Throwable thrown;
	private Integer rupeeBalance;

	/**
	 * @param builder the builder object for constructing new
	 * {@link RupeeTransactionReader} instances.
	 * @param oldestPaymentTransactionAge ignore all payment transactions that
	 * are older than this age or null to parse all payment transactions
	 * regardless of age
	 */
	public UpdateModelImpl(RupeeTransactionReader.Builder builder, Duration oldestAllowablePaymentTransactionAge) {
		this.builder = builder;
		this.oldestAllowablePaymentTransactionAge = oldestAllowablePaymentTransactionAge;

		firstUpdate = (builder.stopDate() == null);
		dao = context.get(DbDao.class);
		reportSender = context.get(ReportSender.class);
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
	public Duration getEstimatedTime() {
		Integer stopAtPage = getStopAtPage();
		return (stopAtPage == null) ? null : EMCShopkeeper.estimateUpdateTime(stopAtPage);
	}

	@Override
	public Integer getStopAtPage() {
		return builder.stopPage();
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
	public LocalDateTime getOldestParsedTransactionDate() {
		return earliestParsedTransactionDate;
	}

	@Override
	public Thread startDownload() {
		started = LocalDateTime.now();
		bonusFeeTotals = new HashMap<Class<? extends RupeeTransaction>, MutableInt>();

		DownloadThread thread = new DownloadThread();
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	@Override
	public synchronized void stopDownload() {
		downloadStopped = true;
		timeTaken = Duration.between(started, LocalDateTime.now());
	}

	@Override
	public void setSession(EmcSession session) {
		context.set(session);
	}

	@Override
	public Throwable getDownloadError() {
		return thrown;
	}

	@Override
	public LocalDateTime getStarted() {
		return started;
	}

	@Override
	public Duration getTimeTaken() {
		return timeTaken;
	}

	@Override
	public Integer getRupeeBalance() {
		return rupeeBalance;
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

			if (latestParsedBonusFeeDate != null) {
				dao.updateBonusesFeesLatestTransactionDate(latestParsedBonusFeeDate);
			}

			if (!bonusFeeTotals.isEmpty()) {
				dao.updateBonusFeeTotals(bonusFeeTotals);
			}

			if (highestBalance != null) {
				dao.updateBonusesFeesHighestBalance(highestBalance);
			}

			//log the update operation
			dao.insertUpdateLog(started, getRupeeBalance(), shopTransactionsCount, paymentTransactionsCount, bonusFeeTransactionsCount, timeTaken);

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

	@Override
	public void reportError() {
		if (thrown == null) {
			return;
		}
		reportSender.report(null, thrown);
	}

	private class DownloadThread extends Thread {
		public DownloadThread() {
			setName(getClass().getSimpleName());
		}

		@Override
		public void run() {
			try {
				reader = builder.build();
			} catch (InvalidCredentialsException e) {
				badSessionListeners.fire();
				return;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			LocalDateTime earliestAllowedPaymentTransaction = (oldestAllowablePaymentTransactionAge == null) ? null : started.minus(oldestAllowablePaymentTransactionAge);

			try {
				highestBalance = null;
				RupeeTransaction transaction;
				int curPage = reader.getCurrentPageNumber();
				while ((transaction = reader.next()) != null) {
					LocalDateTime transactionTs = TimeUtils.toLocalDateTime(transaction.getTs());
					rupeeBalance = reader.getRupeeBalance();
					int page = reader.getCurrentPageNumber();
					synchronized (UpdateModelImpl.this) {
						if (downloadStopped) {
							break;
						}

						if (page != curPage) {
							pagesCount++;
							pageDownloadedListeners.fire();
							curPage = page;
						}

						//keep track of the oldest transaction date
						earliestParsedTransactionDate = transactionTs;

						//keep track of the transaction with the highest balance
						if (highestBalance == null || transaction.getBalance() > highestBalance.getBalance()) {
							highestBalance = transaction;
						}

						if (transaction instanceof ShopTransaction) {
							ShopTransaction shopTransaction = (ShopTransaction) transaction;
							String itemName = itemIndex.getDisplayName(shopTransaction.getItem());
							dao.insertTransaction(new ShopTransactionDb(shopTransaction, itemName), true);
							shopTransactionsCount++;
							transactionsCount++;
						} else if (transaction instanceof PaymentTransaction) {
							if (earliestAllowedPaymentTransaction != null && transactionTs.isBefore(earliestAllowedPaymentTransaction)) {
								//ignore old payment transactions
								continue;
							}

							PaymentTransaction paymentTransaction = (PaymentTransaction) transaction;
							dao.insertPaymentTransaction(new PaymentTransactionDb(paymentTransaction));
							paymentTransactionsCount++;
							transactionsCount++;
						} else if (dao.isBonusFeeTransaction(transaction)) {
							if (latestParsedBonusFeeDate == null) {
								latestParsedBonusFeeDate = transactionTs;
							}

							Class<? extends RupeeTransaction> clazz = transaction.getClass();
							MutableInt count = bonusFeeTotals.get(clazz);
							if (count == null) {
								count = new MutableInt();
								bonusFeeTotals.put(clazz, count);
							}
							count.add(transaction.getAmount());

							bonusFeeTransactionsCount++;
							transactionsCount++;
						}
					}
				}

				//update completed successfully
				synchronized (UpdateModelImpl.this) {
					if (downloadStopped) {
						return;
					}
					pagesCount++;
					pageDownloadedListeners.fire();
					timeTaken = Duration.between(started, LocalDateTime.now());
				}

				downloadCompleteListeners.fire();
			} catch (Throwable t) {
				//an error occurred during the update
				synchronized (UpdateModelImpl.this) {
					stopDownload();

					if (!firstUpdate || transactionsCount == 0) {
						dao.rollback();
						throw new RuntimeException(t);
					}

					thrown = t;
					logger.log(Level.SEVERE, "Error downloading transactions.", t);
				}

				downloadErrorListeners.fire();
			} finally {
				IOUtils.closeQuietly(reader);
			}
		}
	}
}
