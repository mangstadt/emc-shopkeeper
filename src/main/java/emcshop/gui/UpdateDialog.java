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

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;

import net.miginfocom.swing.MigLayout;
import emcshop.ShopTransaction;
import emcshop.TransactionPuller;
import emcshop.TransactionPullerListener;
import emcshop.db.DbDao;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class UpdateDialog extends JDialog implements WindowListener {
	private JButton cancel;
	private JLabel transactions;
	private JLabel timeElapsed;

	private Thread timeThread;
	private TransactionPuller puller;
	private Thread pullerThread;

	private long started;
	private int transactionCount = 0;

	public UpdateDialog(final MainFrame owner, final TransactionPuller puller, final DbDao dao) {
		super(owner, "Updating Transactions", true);
		this.puller = puller;
		setLocationRelativeTo(owner);

		createWidgets();
		layoutWidgets();
		setResizable(false);

		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);

		setSize(200, 150);
		addWindowListener(this);

		timeThread = new Thread() {
			@Override
			public void run() {
				long start = System.currentTimeMillis();
				NumberFormat nf = new DecimalFormat("00");
				while (isVisible()) {
					long components[] = TimeUtils.parseTimeComponents((System.currentTimeMillis() - start));
					timeElapsed.setText(nf.format(components[2]) + ":" + nf.format(components[1]));

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		};

		pullerThread = new Thread() {
			String errorDisplayMessage;
			Throwable error;

			@Override
			public void run() {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
					//nothing
				}

				started = System.currentTimeMillis();
				try {
					puller.start(new TransactionPullerListener() {
						@Override
						public synchronized void onPageScraped(int page, List<ShopTransaction> transactions) {
							try {
								for (ShopTransaction transaction : transactions) {
									dao.insertTransaction(transaction);
									transactionCount++;
									UpdateDialog.this.transactions.setText(transactionCount + "");
								}
							} catch (SQLException e) {
								error = e;
								errorDisplayMessage = "An error occurred while inserting transactions into the database.";
								puller.cancel();
							}
						}

						@Override
						public void onCancel() {
							dao.rollback();
						}

						@Override
						public void onError(Throwable thrown) {
							error = thrown;
							errorDisplayMessage = "An error occurred while getting the transactions.";
							dao.rollback();
						}

						@Override
						public void onSuccess() {
							try {
								dao.commit();

								UpdateDialog.this.dispose();
								long time = System.currentTimeMillis() - started;
								owner.updateSuccessful(new Date(started), time, transactionCount);
							} catch (SQLException e) {
								dao.rollback();
								error = e;
								errorDisplayMessage = "An error occurred completing the update.";
							}
						}
					});
				} catch (IOException e) {
					error = e;
					errorDisplayMessage = "An error occurred starting the transaction update.";
				}

				UpdateDialog.this.dispose();

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
		timeElapsed = new JLabel();
	}

	private void layoutWidgets() {
		setLayout(new MigLayout("w 100%!"));

		JPanel p = new JPanel(new FlowLayout());
		p.add(new JLabel(new ImageIcon(getClass().getResource("loading.gif"))));
		p.add(new JLabel("<html><b>Updating...</b></html>"));
		add(p, "span 2, align center, wrap");

		add(new JLabel("Transactions found:"));
		add(transactions, "wrap");

		add(new JLabel("Time elapsed:"));
		add(timeElapsed, "wrap");

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
		timeThread.start();
		pullerThread.start();
	}
}