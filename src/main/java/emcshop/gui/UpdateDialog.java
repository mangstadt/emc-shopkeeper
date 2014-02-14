package emcshop.gui;

import static emcshop.scraper.TransactionPuller.filter;
import static emcshop.util.GuiUtils.toolTipText;

import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
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
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.ReportSender;
import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.scraper.BadSessionException;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.EmcSession;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.RupeeTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.scraper.TransactionPuller;
import emcshop.util.GuiUtils;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class UpdateDialog extends JDialog {
	private static final Logger logger = Logger.getLogger(UpdateDialog.class.getName());

	private final DbDao dao;
	private TransactionPuller puller;

	private JButton cancel, stop;
	private JLabel shopTransactionsLabel, paymentTransactionsLabel, bonusFeeTransactionsLabel;
	private JLabel pages;
	private JLabel timerLabel;
	private JCheckBox display;

	private BadSessionException badLogin;
	private boolean updateComitted = false, cancelOrStopClicked = false;
	private long started, timeTaken;
	private int shopTransactionCount, paymentTransactionCount, bonusFeeTransactionCount, transactionCount;
	private int pageCount;
	private Date earliestParsedTransactionDate;

	private UpdateDialog(MainFrame owner, final DbDao dao, final EmcSession session, final TransactionPuller.Config pullerConfig, final Long estimatedTime) {
		super(owner, "Updating Transactions", true);
		this.dao = dao;

		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);
		setResizable(false);

		final boolean firstUpdate = (pullerConfig.getStopAtDate() == null);
		createWidgets(firstUpdate);
		layoutWidgets(firstUpdate);

		pack();
		setSize(getWidth() + 20, getHeight());
		setLocationRelativeTo(owner);

		Thread pullerThread = new Thread() {
			@Override
			public void run() {
				final Integer stopAtPage = pullerConfig.getStopAtPage();
				final NumberFormat nf = NumberFormat.getInstance();
				final NumberFormat timeNf = new DecimalFormat("00");
				long previousTime = 0;

				Thread timerThread = new TimerThread(estimatedTime);
				timerThread.setDaemon(true);
				timerThread.start();

				started = System.currentTimeMillis();

				try {
					puller = new TransactionPuller(session, pullerConfig);
				} catch (BadSessionException e) {
					badLogin = e;
					dispose();
					return;
				} catch (IOException e) {
					dispose();
					throw new RuntimeException(e);
				}

				try {
					List<RupeeTransaction> transactions;
					while ((transactions = puller.nextPage()) != null) {
						synchronized (UpdateDialog.this) {
							if (cancelOrStopClicked) {
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
							shopTransactionCount += shopTransactions.size();
							transactionCount += shopTransactions.size();

							List<PaymentTransaction> paymentTransactions = filter(transactions, PaymentTransaction.class);
							dao.insertPaymentTransactions(paymentTransactions);
							paymentTransactionCount += paymentTransactions.size();
							transactionCount += paymentTransactions.size();

							List<BonusFeeTransaction> bonusFeeTransactions = filter(transactions, BonusFeeTransaction.class);
							dao.updateBonusesFees(bonusFeeTransactions);
							bonusFeeTransactionCount += bonusFeeTransactions.size();
							transactionCount += bonusFeeTransactions.size();

							pageCount++;
						}

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

					//update completed successfully
					try {
						synchronized (UpdateDialog.this) {
							if (cancelOrStopClicked) {
								return;
							}
							timeTaken = System.currentTimeMillis() - started;
							completeUpdate();
						}
					} finally {
						dispose();
					}
				} catch (Throwable t) {
					//an error occurred during the update

					cancel.setEnabled(false);
					if (stop != null) {
						stop.setEnabled(false);
					}

					timerThread.interrupt();
					timeTaken = System.currentTimeMillis() - started;

					try {
						synchronized (UpdateDialog.this) {
							if (!firstUpdate || transactionCount == 0) {
								dao.rollback();
								throw new RuntimeException(t);
							}

							logger.log(Level.SEVERE, "Error downloading transactions.", t);

							boolean saveTransactions = UpdateErrorDialog.show(UpdateDialog.this, pageCount, transactionCount, earliestParsedTransactionDate, t);
							if (saveTransactions) {
								completeUpdate();
							} else {
								dao.rollback();
							}
						}
					} finally {
						dispose(); //Java doesn't seem to like it when dispose() calls are within synchronized blocks
					}
				}
			}
		};
		pullerThread.setDaemon(true);
		pullerThread.start();
	}

	private void completeUpdate() {
		try {
			if (earliestParsedTransactionDate != null) {
				dao.updateBonusesFeesSince(earliestParsedTransactionDate);
			}

			Integer rupeeBalance = puller.getRupeeBalance();
			if (rupeeBalance != null) {
				dao.updateRupeeBalance(rupeeBalance);
			}

			dao.commit();
			updateComitted = true;
		} catch (SQLException e) {
			dao.rollback();
			throw new RuntimeException(e);
		}
	}

	private void createWidgets(boolean firstUpdate) {
		cancel = new JButton("Cancel");
		if (firstUpdate) {
			cancel.setToolTipText(toolTipText("Stops the update process and <b>discards</b> all transactions that were parsed."));
		}
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				synchronized (UpdateDialog.this) {
					cancelOrStopClicked = true;
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
					boolean noTransactions = false;
					synchronized (UpdateDialog.this) {
						cancelOrStopClicked = true;
						timeTaken = System.currentTimeMillis() - started;
						if (transactionCount == 0) {
							noTransactions = true;
						}
					}

					if (!noTransactions) {
						completeUpdate();
					}
					dispose();
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

	private class TimerThread extends Thread {
		private final String estimatedTimeDisplay;

		public TimerThread(Long estimatedTime) {
			estimatedTimeDisplay = (estimatedTime == null) ? null : DurationFormatUtils.formatDuration(estimatedTime, "HH:mm:ss", true);
		}

		@Override
		public void run() {
			long start = System.currentTimeMillis();
			NumberFormat nf = new DecimalFormat("00");
			while (isDisplayable()) {
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

	/**
	 * Shows the update dialog.
	 * @param owner
	 * @param dao
	 * @param puller
	 * @param estimatedTime
	 * @return statistics of the update or null if the update was canceled
	 */
	public static Result show(MainFrame owner, DbDao dao, EmcSession session, TransactionPuller.Config pullerConfig, Long estimatedTime) throws BadSessionException {
		UpdateDialog dialog = new UpdateDialog(owner, dao, session, pullerConfig, estimatedTime);
		dialog.setVisible(true);

		if (dialog.badLogin != null) {
			throw dialog.badLogin;
		}

		if (!dialog.updateComitted) {
			return null;
		}

		Result result = new Result();
		result.setShopTransactions(dialog.shopTransactionCount);
		result.setPaymentTransactions(dialog.paymentTransactionCount);
		result.setBonusFeeTransactions(dialog.bonusFeeTransactionCount);
		result.setPageCount(dialog.pageCount);
		result.setRupeeBalance(dialog.puller.getRupeeBalance());
		result.setShowResults(dialog.display.isSelected());
		result.setStarted(new Date(dialog.started));
		result.setTimeTaken(dialog.timeTaken);
		return result;
	}

	private static class UpdateErrorDialog extends JDialog {
		private boolean saveTransactions = false;

		private UpdateErrorDialog(Window owner, int pages, int transactions, Date oldestTransaction, final Throwable thrown) {
			super(owner, "Error", ModalityType.DOCUMENT_MODAL);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			GuiUtils.closeOnEscapeKeyPress(this);

			JTextArea displayText = new JTextArea("An unexpected error occurred while downloading your transactions.");
			displayText.setEditable(false);
			displayText.setBackground(getBackground());
			displayText.setLineWrap(true);
			displayText.setWrapStyleWord(true);

			JLabel errorIcon = new JLabel(ImageManager.getErrorIcon());

			JTextArea stackTrace = new JTextArea(ExceptionUtils.getStackTrace(thrown));
			stackTrace.setEditable(false);
			stackTrace.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

			final JButton report = new JButton("Send Error Report");
			report.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ReportSender.instance().report(thrown);
					report.setEnabled(false);
					report.setText("Reported");
					JOptionPane.showMessageDialog(UpdateErrorDialog.this, "Error report sent.  Thanks!");
				}
			});

			JButton save = new JButton("Save Transactions");
			save.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					saveTransactions = true;
					dispose();
				}
			});

			JButton discard = new JButton("Discard transactions");
			discard.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					dispose();
				}
			});

			setLayout(new MigLayout());
			add(errorIcon, "split 2");
			add(displayText, "w 100:100%:100%, gapleft 10, wrap");
			JScrollPane scroll = new JScrollPane(stackTrace);
			scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			add(scroll, "grow, w 100%, h 100%, align center, wrap");
			add(report, "align right, wrap");

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
			//@formatter:off
			add(new JLabel(
			"<html>" +
				"<b>Do you want to save the transactions that have been parsed or try starting over from scratch?</b><br><br>" +
				"<table border=0>" +
					"<tr><td>Date of oldest transaction:</td><td>" + df.format(oldestTransaction) + "</td></tr>" +
					"<tr><td>Total transactions parsed:</td><td>" + transactions + "</td></tr>" +
					"<tr><td>Total pages parsed:</td><td>" + pages + "</td></tr>" +
				"</table>" +
			"</html>"), "align center, wrap");
			//@formatter:on

			add(save, "align center, split 2");
			add(discard);

			setSize(500, 500);
			setLocationRelativeTo(owner);
		}

		public static boolean show(Window owner, int pages, int transactions, Date oldestTransaction, Throwable thrown) {
			UpdateErrorDialog dialog = new UpdateErrorDialog(owner, pages, transactions, oldestTransaction, thrown);
			dialog.setVisible(true);
			return dialog.saveTransactions;
		}
	}

	public static class Result {
		private Date started;
		private Integer rupeeBalance;
		private long timeTaken;
		private int shopTransactions, paymentTransactions, bonusFeeTransactions;
		private int pageCount;
		private boolean showResults;

		public Date getStarted() {
			return started;
		}

		public void setStarted(Date started) {
			this.started = started;
		}

		public Integer getRupeeBalance() {
			return rupeeBalance;
		}

		public void setRupeeBalance(Integer rupeeBalance) {
			this.rupeeBalance = rupeeBalance;
		}

		public long getTimeTaken() {
			return timeTaken;
		}

		public void setTimeTaken(long timeTaken) {
			this.timeTaken = timeTaken;
		}

		public int getShopTransactions() {
			return shopTransactions;
		}

		public void setShopTransactions(int shopTransactions) {
			this.shopTransactions = shopTransactions;
		}

		public int getPaymentTransactions() {
			return paymentTransactions;
		}

		public void setPaymentTransactions(int paymentTransactions) {
			this.paymentTransactions = paymentTransactions;
		}

		public int getBonusFeeTransactions() {
			return bonusFeeTransactions;
		}

		public void setBonusFeeTransactions(int bonusFeeTransactions) {
			this.bonusFeeTransactions = bonusFeeTransactions;
		}

		public int getPageCount() {
			return pageCount;
		}

		public void setPageCount(int pageCount) {
			this.pageCount = pageCount;
		}

		public boolean isShowResults() {
			return showResults;
		}

		public void setShowResults(boolean showResults) {
			this.showResults = showResults;
		}
	}
}