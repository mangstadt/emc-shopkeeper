package emcshop.gui;

import static emcshop.util.GuiUtils.toolTipText;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JRootPane;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.scraper.BadSessionException;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.EmcSession;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.RupeeTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.scraper.TransactionPuller;
import emcshop.util.Settings;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class UpdateDialog extends JDialog {
	private static final Logger logger = Logger.getLogger(UpdateDialog.class.getName());

	private final MainFrame owner;
	private final DbDao dao;

	private JButton cancel, stop;
	private volatile boolean cancelClicked = false;
	private JLabel shopTransactionsLabel, paymentTransactionsLabel, bonusFeeTransactionsLabel;
	private JLabel pages;
	private JLabel timerLabel;
	private JCheckBox display;

	private TransactionPuller puller;
	private Thread pullerThread;

	private long started;
	private TransactionPullerListener listener;

	private UpdateDialog(final MainFrame owner, final DbDao dao, final Settings settings, final TransactionPuller puller, final Long estimatedTime) {
		super(owner, "Updating Transactions", true);
		this.owner = owner;
		this.dao = dao;
		this.puller = puller;
		listener = new TransactionPullerListener(puller.getStopAtPage());

		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);
		setResizable(false);

		boolean firstUpdate = (puller.getStopAtDate() == null);
		createWidgets(firstUpdate);
		layoutWidgets(firstUpdate);

		pack();
		setSize(getWidth() + 20, getHeight());
		setLocationRelativeTo(owner);

		pullerThread = new Thread() {
			@Override
			public void run() {
				TransactionPuller.Result result = null;
				boolean repeat;
				do {
					repeat = false;

					Thread timerThread = new TimerThread(estimatedTime);
					timerThread.setDaemon(true);
					timerThread.start();

					started = System.currentTimeMillis();

					try {
						result = puller.start(settings.getSession(), listener);
					} catch (BadSessionException e) {
						String username = null;
						EmcSession oldSession = settings.getSession();
						if (oldSession != null) {
							username = oldSession.getUsername();
						}
						LoginDialog.Result loginResult = LoginDialog.show(UpdateDialog.this, settings.isPersistSession(), username);
						EmcSession session = loginResult.getSession();
						if (session != null) {
							settings.setSession(session);
							settings.setPersistSession(loginResult.isRememberMe());
							settings.save();

							if (settings.isPersistSession()) {
								owner.setClearSessionMenuItemEnabled(true);
							}

							//restart the puller with the new session token
							repeat = true;
						}
					} catch (Throwable t) {
						//an error occurred
						dao.rollback();
						dispose();
						throw new RuntimeException(t);
					} finally {
						timerThread.interrupt();
					}
				} while (repeat);

				//puller was canceled
				if (result == null) {
					if (!cancelClicked) {
						dao.rollback();
					}

					dispose();
					return;
				}

				try {
					if (listener.earliestParsedTransactionDate != null) {
						dao.updateBonusesFeesSince(listener.earliestParsedTransactionDate);
					}
					dao.commit();
					dispose();

					owner.updateSuccessful(new Date(started), result.getRupeeBalance(), result.getTimeTaken(), listener.shopTransactionCount, listener.paymentTransactionCount, listener.bonusFeeTransactionCount, result.getPageCount(), display.isSelected());
				} catch (SQLException e) {
					dao.rollback();
					throw new RuntimeException(e);
				}
			}
		};
		pullerThread.setDaemon(true);
		pullerThread.start();
	}

	private void createWidgets(boolean firstUpdate) {
		cancel = new JButton("Cancel");
		if (firstUpdate) {
			cancel.setToolTipText(toolTipText("Stops the update process and <b>discards</b> all transactions that were parsed."));
		}
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				synchronized (puller) {
					//synchronized so the code in the listener is not executed at the same time as this code
					cancelClicked = true;
					puller.cancel();
					dao.rollback();
				}
				dispose();
			}
		});

		if (firstUpdate) {
			stop = new JButton("Stop");
			stop.setToolTipText(toolTipText("Stops the update process and <b>saves</b> all transactions that were parsed."));
			stop.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					synchronized (puller) {
						//synchronized so the code in the listener is not executed at the same time as this code
						cancelClicked = true;
						puller.cancel();

						if (listener.getParsedTransactionsCount() == 0) {
							dispose();
							return;
						}

						try {
							if (listener.earliestParsedTransactionDate != null) {
								dao.updateBonusesFeesSince(listener.earliestParsedTransactionDate);
							}
							dao.commit();
						} catch (SQLException e) {
							ErrorDialog.show(null, "Error committing transactions.", e);
							dispose();
							return;
						}
					}

					dispose();
					long timeTaken = System.currentTimeMillis() - started;
					owner.updateSuccessful(new Date(started), puller.getRupeeBalance(), timeTaken, listener.shopTransactionCount, listener.paymentTransactionCount, listener.bonusFeeTransactionCount, listener.pageCount, display.isSelected());
				}
			});
		}

		pages = new JLabel("0");
		shopTransactionsLabel = new JLabel("0");
		paymentTransactionsLabel = new JLabel("0");
		bonusFeeTransactionsLabel = new JLabel("0");
		timerLabel = new JLabel("...");
		display = new JCheckBox("Display transactions when finished");
		display.setSelected(true);
	}

	private void layoutWidgets(boolean firstUpdate) {
		setLayout(new MigLayout());

		add(new JLabel(ImageManager.getLoading()), "span 2, split 2, align center");
		add(new JLabel("<html><b>Updating...</b></html>"), "wrap");

		add(new JLabel("Pages:"));
		add(pages, "wrap");

		add(new JLabel("Transactions:"), "span 2, wrap");
		add(new JLabel("Shop:"), "gapleft 30");
		add(shopTransactionsLabel, "wrap");
		add(new JLabel("Payment:"), "gapleft 30");
		add(paymentTransactionsLabel, "wrap");
		add(new JLabel("Bonus/Fee:"), "gapleft 30");
		add(bonusFeeTransactionsLabel, "wrap");

		add(new JLabel("Time:"));
		add(timerLabel, "wrap");

		add(display, "span 2, align center, wrap");

		if (firstUpdate) {
			add(cancel, "span 2, split 2, align center");
			add(stop);
		} else {
			add(cancel, "span 2, align center");
		}
	}

	private class TransactionPullerListener extends TransactionPuller.Listener {
		private final NumberFormat nf = NumberFormat.getInstance();
		private final NumberFormat timeNf = new DecimalFormat("00");
		private final Integer stopAtPage;
		private int shopTransactionCount = 0, paymentTransactionCount = 0, bonusFeeTransactionCount = 0;
		private int pageCount = 0;
		private long previousTime = 0;
		private Date earliestParsedTransactionDate;

		public TransactionPullerListener(Integer stopAtPage) {
			this.stopAtPage = stopAtPage;
		}

		@Override
		public void onPageScraped(int page, List<RupeeTransaction> transactions) throws Throwable {
			synchronized (puller) { //the method itself cannot have a "synchronized" flag because there is a synchronized block in the cancel button's click handler
				if (puller.isCanceled()) {
					return;
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
				dao.insertTransactions(shopTransactions, true);
				shopTransactionCount += shopTransactions.size();

				List<PaymentTransaction> paymentTransactions = filter(transactions, PaymentTransaction.class);
				dao.insertPaymentTransactions(paymentTransactions);
				paymentTransactionCount += paymentTransactions.size();

				List<BonusFeeTransaction> bonusFeeTransactions = filter(transactions, BonusFeeTransaction.class);
				dao.updateBonusesFees(bonusFeeTransactions);
				bonusFeeTransactionCount += bonusFeeTransactions.size();

				pageCount++;

				String pagesText = nf.format(pageCount);
				if (stopAtPage != null) {
					pagesText += " / " + stopAtPage;
				}
				UpdateDialog.this.pages.setText(pagesText);

				shopTransactionsLabel.setText(nf.format(shopTransactionCount));
				paymentTransactionsLabel.setText(nf.format(paymentTransactionCount));
				bonusFeeTransactionsLabel.setText(nf.format(bonusFeeTransactionCount));

				if (pageCount % 100 == 0 && logger.isLoggable(Level.FINEST)) {
					long fromStart = System.currentTimeMillis() - started;
					long fromPrevious = System.currentTimeMillis() - previousTime;
					long fromStartComponents[] = TimeUtils.parseTimeComponents(fromStart);
					long fromPreviousComponents[] = TimeUtils.parseTimeComponents(fromPrevious);
					//@formatter:off
						logger.finest(
							"DOWNLOAD STATS | " + 
							"Pages: " + pageCount + " | " +
							"From start: " + timeNf.format(fromStartComponents[3]) + ":" + timeNf.format(fromStartComponents[2]) + ":" + timeNf.format(fromStartComponents[1]) + " | " +
							"From previous: " + timeNf.format(fromPreviousComponents[3]) + ":" + timeNf.format(fromPreviousComponents[2]) + ":" + timeNf.format(fromPreviousComponents[1])
						);
						//@formatter:on
					previousTime = System.currentTimeMillis();
				}
			}
		}

		public int getParsedTransactionsCount() {
			return shopTransactionCount + paymentTransactionCount + bonusFeeTransactionCount;
		}
	}

	private class TimerThread extends Thread {
		private final String estimatedTimeDisplay;

		public TimerThread(Long estimatedTime) {
			estimatedTimeDisplay = (estimatedTime == null) ? null : DurationFormatUtils.formatDuration(estimatedTime, "HH:mm:ss", true);
		}

		@Override
		public void run() {
			long start = System.currentTimeMillis();
			NumberFormat nf = new DecimalFormat("00");
			while (isVisible()) {
				long elapsed = System.currentTimeMillis() - start;
				long components[] = TimeUtils.parseTimeComponents(elapsed);
				String timerText = nf.format(components[3]) + ":" + nf.format(components[2]) + ":" + nf.format(components[1]);
				if (estimatedTimeDisplay != null) {
					timerText += " / " + estimatedTimeDisplay;
				}
				timerLabel.setText(timerText);

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}

	public static void show(MainFrame owner, DbDao dao, Settings settings, TransactionPuller puller, Long estimatedTime) {
		UpdateDialog dialog = new UpdateDialog(owner, dao, settings, puller, estimatedTime);
		dialog.setVisible(true);
	}
}