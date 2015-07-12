package emcshop.model;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
import emcshop.util.GuiUtils;

public class UpdateModelImpl implements IUpdateModel {
	private static final Logger logger = Logger.getLogger(UpdateModelImpl.class.getName());
	private static final AppContext context = AppContext.instance();

	private final ItemIndex itemIndex = ItemIndex.instance();
	private final boolean firstUpdate;
	private final RupeeTransactionReader.Builder builder;
	private final Integer oldestAllowablePaymentTransactionAge;
	private final DbDao dao;
	private final ReportSender reportSender;

	private final List<ActionListener> pageDownloadedListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> badSessionListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> downloadErrorListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> downloadCompleteListeners = new ArrayList<ActionListener>();

	private RupeeTransactionReader reader;
	private long started, timeTaken;
	private int transactionsCount, shopTransactionsCount, paymentTransactionsCount, bonusFeeTransactionsCount, pagesCount;
	private Date earliestParsedTransactionDate, latestParsedBonusFeeDate;
	private Map<Class<? extends RupeeTransaction>, MutableInt> bonusFeeTotals;
	private boolean downloadStopped = false;
	private Throwable thrown;
	private Integer rupeeBalance;

	/**
	 * @param builder the builder object for constructing new
	 * {@link RupeeTransactionReader} instances.
	 * @param oldestPaymentTransactionAge ignore all payment transactions that
	 * are older than this age (in milliseconds) or null to parse all payment
	 * transactions regardless of age
	 */
	public UpdateModelImpl(RupeeTransactionReader.Builder builder, Integer oldestAllowablePaymentTransactionAge) {
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
	public Long getEstimatedTime() {
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
	public Date getOldestParsedTransactionDate() {
		return earliestParsedTransactionDate;
	}

	@Override
	public Thread startDownload() {
		started = System.currentTimeMillis();
		bonusFeeTotals = new HashMap<Class<? extends RupeeTransaction>, MutableInt>();

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
		context.set(session);
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

			//log the update operation
			dao.insertUpdateLog(new Date(started), getRupeeBalance(), shopTransactionsCount, paymentTransactionsCount, bonusFeeTransactionsCount, timeTaken);

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
				GuiUtils.fireEvents(badSessionListeners);
				return;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			Long earliestAllowedPaymentTransaction = (oldestAllowablePaymentTransactionAge == null) ? null : started - oldestAllowablePaymentTransactionAge;

			try {
				RupeeTransaction transaction;
				int curPage = reader.getCurrentPageNumber();
				while ((transaction = reader.next()) != null) {
					rupeeBalance = reader.getRupeeBalance();
					int page = reader.getCurrentPageNumber();
					synchronized (UpdateModelImpl.this) {
						if (downloadStopped) {
							break;
						}

						if (page != curPage) {
							pagesCount++;
							GuiUtils.fireEvents(pageDownloadedListeners);
							curPage = page;
						}

						//keep track of the oldest transaction date
						earliestParsedTransactionDate = transaction.getTs();

						if (transaction instanceof ShopTransaction) {
							ShopTransaction shopTransaction = (ShopTransaction) transaction;
							String itemName = itemIndex.getDisplayName(shopTransaction.getItem());
							dao.insertTransaction(new ShopTransactionDb(shopTransaction, itemName), true);
							shopTransactionsCount++;
							transactionsCount++;
						} else if (transaction instanceof PaymentTransaction) {
							if (earliestAllowedPaymentTransaction != null && transaction.getTs().getTime() < earliestAllowedPaymentTransaction) {
								//ignore old payment transactions
								continue;
							}

							PaymentTransaction paymentTransaction = (PaymentTransaction) transaction;
							dao.insertPaymentTransaction(new PaymentTransactionDb(paymentTransaction));
							paymentTransactionsCount++;
							transactionsCount++;
						} else if (dao.isBonusFeeTransaction(transaction)) {
							if (latestParsedBonusFeeDate == null) {
								latestParsedBonusFeeDate = transaction.getTs();
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
					GuiUtils.fireEvents(pageDownloadedListeners);
					timeTaken = System.currentTimeMillis() - started;
				}

				GuiUtils.fireEvents(downloadCompleteListeners);
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

				GuiUtils.fireEvents(downloadErrorListeners);
			} finally {
				IOUtils.closeQuietly(reader);
			}
		}
	}
}
