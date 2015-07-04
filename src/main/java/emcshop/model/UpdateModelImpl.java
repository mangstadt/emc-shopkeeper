package emcshop.model;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import com.github.mangstadt.emc.net.InvalidCredentialsException;
import com.github.mangstadt.emc.rupees.RupeeTransactionReader;
import com.github.mangstadt.emc.rupees.dto.DailySigninBonus;
import com.github.mangstadt.emc.rupees.dto.EggifyFee;
import com.github.mangstadt.emc.rupees.dto.HorseSummonFee;
import com.github.mangstadt.emc.rupees.dto.LockTransaction;
import com.github.mangstadt.emc.rupees.dto.MailFee;
import com.github.mangstadt.emc.rupees.dto.PaymentTransaction;
import com.github.mangstadt.emc.rupees.dto.RupeeTransaction;
import com.github.mangstadt.emc.rupees.dto.ShopTransaction;
import com.github.mangstadt.emc.rupees.dto.VaultFee;
import com.github.mangstadt.emc.rupees.dto.VoteBonus;
import com.google.common.collect.ImmutableSet;

import emcshop.AppContext;
import emcshop.EMCShopkeeper;
import emcshop.ReportSender;
import emcshop.db.DbDao;
import emcshop.scraper.EmcSession;
import emcshop.util.GuiUtils;

public class UpdateModelImpl implements IUpdateModel {
	private static final Logger logger = Logger.getLogger(UpdateModelImpl.class.getName());
	private static final AppContext context = AppContext.instance();

	private final Set<Class<? extends RupeeTransaction>> bonusFeeTransactionTypes;
	{
		ImmutableSet.Builder<Class<? extends RupeeTransaction>> builder = ImmutableSet.builder();
		builder.add(DailySigninBonus.class);
		builder.add(EggifyFee.class);
		builder.add(HorseSummonFee.class);
		builder.add(LockTransaction.class);
		builder.add(MailFee.class);
		builder.add(VaultFee.class);
		builder.add(VoteBonus.class);
		bonusFeeTransactionTypes = builder.build();
	}

	private final boolean firstUpdate;
	private final RupeeTransactionReader.Builder builder;
	private final Integer oldestPaymentTransaction;
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
	private boolean downloadStopped = false;
	private Throwable thrown;
	private Integer rupeeBalance;

	public UpdateModelImpl(RupeeTransactionReader.Builder builder, Integer oldestPaymentTransaction) {
		this.builder = builder;
		this.oldestPaymentTransaction = oldestPaymentTransaction;

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

			try {
				RupeeTransaction transaction;
				int curPage = reader.getCurrentPageNumber();
				while ((transaction = reader.next()) != null) {
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
							dao.insertTransaction(shopTransaction, true);
							shopTransactionsCount++;
							transactionsCount++;
						} else if (transaction instanceof PaymentTransaction) {
							//TODO ignore old payment transactions?
							PaymentTransaction paymentTransaction = (PaymentTransaction) transaction;
							dao.insertPaymentTransactions(Arrays.asList(paymentTransaction));
							paymentTransactionsCount++;
							transactionsCount++;
						} else if (bonusFeeTransactionTypes.contains(transaction.getClass())) {
							if (latestParsedBonusFeeDate == null) {
								latestParsedBonusFeeDate = transaction.getTs();
							}
							dao.updateBonusFees(Arrays.asList(transaction));
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
