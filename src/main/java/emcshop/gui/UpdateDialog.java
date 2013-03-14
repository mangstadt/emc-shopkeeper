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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;

import net.miginfocom.swing.MigLayout;
import emcshop.EmcSession;
import emcshop.ShopTransaction;
import emcshop.TransactionPuller;
import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.util.Settings;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class UpdateDialog extends JDialog implements WindowListener {
	private static final Logger logger = Logger.getLogger(UpdateDialog.class.getName());

	private JButton cancel;
	private JLabel transactions;
	private JLabel timerLabel;

	private TransactionPuller puller;
	private Thread pullerThread;

	private long started;

	public UpdateDialog(final MainFrame owner, final DbDao dao, final Settings settings) throws SQLException {
		super(owner, "Updating Transactions", true);
		setLocationRelativeTo(owner);

		createWidgets();
		layoutWidgets();
		setResizable(false);

		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);

		pack();
		setSize(getWidth() + 20, getHeight());
		addWindowListener(this);

		puller = new TransactionPuller(settings.getSession());
		pullerThread = new Thread() {
			String errorDisplayMessage;
			Throwable error;

			@Override
			public void run() {
				try {
					ShopTransaction latest = dao.getLatestTransaction();
					if (latest == null) {
						boolean yes = FirstUpdateDialog.show(UpdateDialog.this);
						if (!yes) {
							return;
						}
					} else {
						puller.setStopAtDate(latest.getTs());
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
									timerLabel.setText(nf.format(components[2]) + ":" + nf.format(components[1]));

									try {
										Thread.sleep(1000);
									} catch (InterruptedException e) {
										break;
									}
								}
							}
						};
						timerThread.start();

						started = System.currentTimeMillis();

						TransactionPuller.Result result = puller.start(new TransactionPuller.Listener() {
							int transactionCount = 0;

							@Override
							public synchronized void onPageScraped(int page, List<ShopTransaction> transactions) {
								try {
									dao.insertTransactions(transactions);
									transactionCount += transactions.size();
									UpdateDialog.this.transactions.setText(transactionCount + "");
								} catch (SQLException e) {
									error = e;
									errorDisplayMessage = "An error occurred while inserting transactions into the database.";
									puller.cancel();
								}
							}
						});

						switch (result.getState()) {
						case CANCELLED:
							dao.rollback();
							break;
						case NOT_LOGGED_IN:
							timerThread.interrupt();

							EmcSession session = LoginDialog.show(UpdateDialog.this);
							if (session != null) {
								settings.setSession(session);
								try {
									settings.save();
								} catch (IOException e) {
									logger.log(Level.SEVERE, "An error occurred saving the settings.", e);
								}

								puller = new TransactionPuller(session);
								if (latest != null) {
									puller.setStopAtDate(latest.getTs());
								}

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
								dao.commit();
								dispose();
								owner.updateSuccessful(new Date(started), result.getTimeTaken(), result.getTransactionCount());
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
	}

	private void createWidgets() {
		cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				cancel.setText("Canceling...");
				cancel.setEnabled(false);
				puller.cancel();
			}
		});

		transactions = new JLabel("0");
		timerLabel = new JLabel("...");
	}

	private void layoutWidgets() {
		setLayout(new MigLayout());

		JPanel p = new JPanel(new FlowLayout());
		p.add(new JLabel(ImageManager.getLoading()));
		p.add(new JLabel("<html><b>Updating...</b></html>"));
		add(p, "span 2, align center, wrap");

		add(new JLabel("Transactions found:"));
		add(transactions, "wrap");

		add(new JLabel("Time elapsed:"));
		add(timerLabel, "wrap");

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