package emcshop.gui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
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
import javax.swing.JPanel;
import javax.swing.JRootPane;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.BonusFeeTransaction;
import emcshop.EmcSession;
import emcshop.PaymentTransaction;
import emcshop.RupeeTransaction;
import emcshop.ShopTransaction;
import emcshop.TransactionPuller;
import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.util.Settings;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class UpdateDialog extends JDialog implements WindowListener {
	private static final Logger logger = Logger.getLogger(UpdateDialog.class.getName());

	private final DbDao dao;

	private JButton cancel;
	private volatile boolean cancelClicked = false;
	private JLabel transactions;
	private JLabel pages;
	private JLabel timerLabel;
	private JCheckBox display;

	private TransactionPuller puller;
	private Thread pullerThread;

	private long started;
	private Date earliestParsedTransactionDate;

	public UpdateDialog(final MainFrame owner, final DbDao dao, final Settings settings) throws SQLException {
		super(owner, "Updating Transactions", true);

		this.dao = dao;

		createWidgets();
		layoutWidgets();
		setResizable(false);

		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);

		pack();
		setSize(getWidth() + 20, getHeight());
		setLocationRelativeTo(owner);
		addWindowListener(this);

		puller = new TransactionPuller(settings.getSession());
		pullerThread = new Thread() {
			String errorDisplayMessage;
			Throwable error;

			@Override
			public void run() {
				try {
					Date latestTransactionDate = dao.getLatestTransactionDate();
					final Integer stopAtPage;
					final String estimatedTimeDisplay;
					final Integer oldestPaymentTransactionDays;
					if (latestTransactionDate == null) {
						FirstUpdateDialog.Result result = FirstUpdateDialog.show(UpdateDialog.this);
						if (result.isCancelled()) {
							return;
						}

						stopAtPage = result.getStopAtPage();
						puller.setStopAtPage(stopAtPage);

						oldestPaymentTransactionDays = result.getOldestPaymentTransactionDays();
						puller.setMaxPaymentTransactionAge(oldestPaymentTransactionDays);

						if (result.getEstimatedTime() != null) {
							estimatedTimeDisplay = DurationFormatUtils.formatDuration(result.getEstimatedTime(), "HH:mm:ss", true);
						} else {
							estimatedTimeDisplay = null;
						}
					} else {
						puller.setStopAtDate(latestTransactionDate);
						stopAtPage = null;
						estimatedTimeDisplay = null;
						oldestPaymentTransactionDays = null;
					}

					boolean repeat;
					do {
						repeat = false;

						Thread timerThread = new Thread() {
							@Override
							public void run() {
								long start = System.currentTimeMillis();
								NumberFormat nf = new DecimalFormat("00");
								while (UpdateDialog.this.isVisible()) {
									long components[] = TimeUtils.parseTimeComponents((System.currentTimeMillis() - start));
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
						};
						timerThread.setDaemon(true);
						timerThread.start();

						started = System.currentTimeMillis();

						TransactionPuller.Result result = puller.start(new TransactionPuller.Listener() {
							private final NumberFormat nf = NumberFormat.getInstance();
							private final NumberFormat timeNf = new DecimalFormat("00");
							private int transactionCount = 0;
							private int pageCount = 0;
							private long previousTime = 0;

							@Override
							public void onPageScraped(int page, List<RupeeTransaction> transactions) {
								synchronized (puller) { //the method itself cannot have a "synchronized" flag because there is a synchronized block in the cancel button's click handler
									if (puller.isCanceled()) {
										return;
									}

									if (!transactions.isEmpty()) {
										RupeeTransaction last = transactions.get(transactions.size() - 1); //transactions are ordered date descending
										Date lastTs = last.getTs();
										if (earliestParsedTransactionDate == null || lastTs.before(earliestParsedTransactionDate)) {
											earliestParsedTransactionDate = lastTs;
										}
									}

									try {
										List<ShopTransaction> shopTransactions = filter(transactions, ShopTransaction.class);
										dao.insertTransactions(shopTransactions);

										List<PaymentTransaction> paymentTransactions = filter(transactions, PaymentTransaction.class);
										dao.insertPaymentTransactions(paymentTransactions);

										List<BonusFeeTransaction> bonusFeeTransactions = filter(transactions, BonusFeeTransaction.class);
										dao.updateBonusesFees(bonusFeeTransactions);

										pageCount++;
										transactionCount += shopTransactions.size() + paymentTransactions.size();

										String pagesText = nf.format(pageCount);
										if (stopAtPage != null) {
											pagesText += " / " + stopAtPage;
										}
										UpdateDialog.this.pages.setText(pagesText);

										UpdateDialog.this.transactions.setText(nf.format(transactionCount));

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
									} catch (SQLException e) {
										error = e;
										errorDisplayMessage = "An error occurred while inserting transactions into the database.";
										puller.cancel();
									}
								}
							}
						});

						switch (result.getState()) {
						case CANCELLED:
							if (!cancelClicked) {
								dao.rollback();
							}
							break;
						case NOT_LOGGED_IN:
							timerThread.interrupt();

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
									owner.clearSession.setEnabled(true);
								}

								puller.setSession(session);

								repeat = true;
							}
							break;
						case FAILED:
							dao.rollback();
							error = result.getThrown();
							errorDisplayMessage = "An error occurred while getting the transactions.";
							break;
						case COMPLETED:
							try {
								if (earliestParsedTransactionDate != null) {
									dao.updateBonusesFeesSince(earliestParsedTransactionDate);
								}
								dao.commit();
								dispose();
								owner.updateSuccessful(new Date(started), result.getRupeeBalance(), result.getTimeTaken(), result.getTransactionCount(), result.getPageCount(), display.isSelected());
							} catch (SQLException e) {
								dao.rollback();
								error = e;
								errorDisplayMessage = "An error occurred completing the update.";
							}
							break;
						}
					} while (repeat);
				} catch (IOException e) {
					error = e;
					errorDisplayMessage = "An error occurred starting the transaction update.";
				} catch (SQLException e) {
					error = e;
					errorDisplayMessage = "An error occurred connecting to the database.";
				} finally {
					dispose();
				}

				if (error != null) {
					ErrorDialog.show(null, errorDisplayMessage, error);
				}
			}
		};
		pullerThread.setDaemon(true);
	}

	private void createWidgets() {
		cancel = new JButton("Cancel");
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

		pages = new JLabel("0");
		transactions = new JLabel("0");
		timerLabel = new JLabel("...");
		display = new JCheckBox("Display transactions when finished");
		display.setSelected(true);
	}

	private void layoutWidgets() {
		setLayout(new MigLayout());

		JPanel p = new JPanel(new FlowLayout());
		p.add(new JLabel(ImageManager.getLoading()));
		p.add(new JLabel("<html><b>Updating...</b></html>"));
		add(p, "span 2, align center, wrap");

		add(new JLabel("Pages:"));
		add(pages, "wrap");

		add(new JLabel("Transactions:"));
		add(transactions, "wrap");

		add(new JLabel("Time:"));
		add(timerLabel, "wrap");

		add(display, "span 2, align center, wrap");

		add(cancel, "span 2, align center");
	}

	////////////////////////////////

	@Override
	public void windowActivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		pullerThread.start();
	}
}