package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.GuiUtils.toolTipText;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;
import emcshop.EmcSession;
import emcshop.LogManager;
import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.MacSupport;
import emcshop.util.NumberFormatter;
import emcshop.util.Settings;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class MainFrame extends JFrame implements WindowListener {
	//TODO add update check--include a release date with the app and check the timestamp on the file in github
	private static final Logger logger = Logger.getLogger(MainFrame.class.getName());

	private JButton update;
	private JLabel lastUpdateDate;
	private JLabel rupeeBalance;

	private JTabbedPane tabs;
	private TransactionsTab transactionsTab;
	private PaymentsTab paymentsTab;
	private InventoryTab inventoryTab;

	JMenuItem clearSession;

	private final DbDao dao;
	private final Settings settings;
	private final LogManager logManager;
	private final ProfileImageLoader profileImageLoader;

	public MainFrame(Settings settings, DbDao dao, LogManager logManager, ProfileImageLoader profileImageLoader) throws SQLException {
		this.dao = dao;
		this.settings = settings;
		this.logManager = logManager;
		this.profileImageLoader = profileImageLoader;

		setTitle("EMC Shopkeeper");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		createMenu();
		createWidgets();
		layoutWidgets();
		setSize(settings.getWindowWidth(), settings.getWindowHeight());

		Image appIcon = ImageManager.getAppIcon().getImage();
		setIconImage(appIcon);

		updatePaymentsCount();

		addWindowListener(this);
	}

	private void createMenu() {
		//http://docs.oracle.com/javase/tutorial/uiswing/components/menu.html

		boolean mac = MacSupport.isMac();

		JMenuBar menuBar = new JMenuBar();

		if (!mac) {
			JMenu file = new JMenu("File");

			JMenuItem exit = new JMenuItem("Exit");
			exit.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					windowClosed(null);
				}
			});
			file.add(exit);

			menuBar.add(file);
		}

		{
			JMenu tools = new JMenu("Tools");

			JMenuItem showLog = new JMenuItem("Show log...");
			showLog.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ShowLogDialog.show(MainFrame.this, logManager);
				}
			});
			tools.add(showLog);

			JMenu logLevel = new JMenu("Log level");
			{
				Map<String, Level> levels = new LinkedHashMap<String, Level>();
				levels.put("Detailed", Level.FINEST);
				levels.put("Normal", Level.INFO);
				levels.put("Off", Level.OFF);
				ButtonGroup group = new ButtonGroup();
				for (Map.Entry<String, Level> entry : levels.entrySet()) {
					String name = entry.getKey();
					final Level level = entry.getValue();
					JMenuItem levelItem = new JRadioButtonMenuItem(name);
					levelItem.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent arg0) {
							logger.finest("Changing log level to " + level.getName() + ".");
							logManager.setLevel(level);
							settings.setLogLevel(level);
							try {
								settings.save();
							} catch (IOException e) {
								logger.log(Level.SEVERE, "Problem saving settings file.", e);
							}
						}
					});
					if (logManager.getLevel().equals(level)) {
						levelItem.setSelected(true);
					}
					group.add(levelItem);
					logLevel.add(levelItem);
				}
			}
			tools.add(logLevel);

			clearSession = new JMenuItem("Clear Saved Session");
			clearSession.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					settings.setSession(null);
					try {
						settings.save();
					} catch (IOException e) {
						logger.log(Level.SEVERE, "Problem saving settings file.", e);
					}
					clearSession.setEnabled(false);
					JOptionPane.showMessageDialog(MainFrame.this, "Session has been cleared.", "Session cleared", JOptionPane.INFORMATION_MESSAGE);
				}
			});
			clearSession.setEnabled(settings.getSession() != null);
			tools.add(clearSession);

			tools.addSeparator();

			JMenuItem resetDb = new JMenuItem("Reset database...");
			resetDb.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					boolean reset = ResetDatabaseDialog.show(MainFrame.this);
					if (reset) {
						final LoadingDialog loading = new LoadingDialog(MainFrame.this, "Resetting database", "Resetting database...");
						Thread t = new Thread() {
							@Override
							public void run() {
								busyCursor(MainFrame.this, true);
								try {
									dao.wipe();
									settings.setLastUpdated(null);
									settings.setRupeeBalance(null);
									settings.setSession(null);
									try {
										settings.save();
									} catch (IOException e) {
										logger.log(Level.SEVERE, "Problem saving settings file.", e);
									}
									lastUpdateDate.setText("-");
									updateRupeeBalance();
									transactionsTab.clear();
									updatePaymentsCount();
									paymentsTab.reset();
									inventoryTab.refresh();
									loading.dispose();
								} catch (Throwable e) {
									loading.dispose();
									ErrorDialog.show(MainFrame.this, "Problem resetting database.", e);
								} finally {
									busyCursor(MainFrame.this, false);
								}
							}
						};
						t.start();
						loading.setVisible(true);
					}
				}
			});
			tools.add(resetDb);

			menuBar.add(tools);
		}

		{
			JMenu help = new JMenu("Help");

			JMenuItem changelog = new JMenuItem("Changelog");
			changelog.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ChangelogDialog.show(MainFrame.this);
				}
			});
			help.add(changelog);

			if (!mac) {
				JMenuItem about = new JMenuItem("About");
				about.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						AboutDialog.show(MainFrame.this);
					}
				});
				help.add(about);
			}

			menuBar.add(help);
		}

		setJMenuBar(menuBar);
	}

	private void createWidgets() {
		try {
			ItemSuggestField.init(dao);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		update = new JButton("Update Transactions", ImageManager.getUpdate());
		update.setToolTipText(toolTipText("<font size=4><b>Update Transactions</b></font><br><br>Downloads your latest transactions from the EMC website."));
		update.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				//log the user in if he's not logged in
				EmcSession session = settings.getSession();
				if (session == null) {
					LoginDialog.Result result = LoginDialog.show(MainFrame.this, settings.isPersistSession());
					session = result.getSession();
					if (session == null) {
						return;
					}
					settings.setPersistSession(result.isRememberMe());
					settings.setSession(session);
					try {
						settings.save();
					} catch (IOException e) {
						logger.log(Level.SEVERE, "Problem saving settings file.", e);
					}

					if (settings.isPersistSession()) {
						clearSession.setEnabled(true);
					}
				}

				try {
					UpdateDialog w = new UpdateDialog(MainFrame.this, dao, settings);
					w.setVisible(true);
				} catch (SQLException e) {
					ErrorDialog.show(MainFrame.this, "An error occurred connecting to the database.", e);
				}
			}
		});

		lastUpdateDate = new JLabel();
		Date date = settings.getLastUpdated();
		updateLastUpdateDate(date);

		rupeeBalance = new JLabel();
		updateRupeeBalance();

		tabs = new JTabbedPane();
		tabs.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (tabs.getSelectedComponent() == paymentsTab && paymentsTab.isStale()) {
					paymentsTab.reset();
				}
			}
		});

		transactionsTab = new TransactionsTab(this, dao, profileImageLoader, settings);
		paymentsTab = new PaymentsTab(this, dao, profileImageLoader);
		inventoryTab = new InventoryTab(this, dao);
	}

	private void layoutWidgets() {
		setLayout(new MigLayout("insets 5 10 10 10, fill"));

		JPanel updatePanel = new JPanel(new MigLayout());
		updatePanel.add(update, "wrap");
		updatePanel.add(new JLabel("Last updated:"), "split 2");
		updatePanel.add(lastUpdateDate);
		add(updatePanel);

		add(new JLabel(ImageManager.getImageIcon("header.png")), "w 100%, align center");

		add(new JLabel("<html><h2>Rupees:</h2></html>"), "split 2, gapright 10, align right");
		add(rupeeBalance, "wrap");

		int index = 0;
		tabs.addTab("Transactions", transactionsTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Transactions Tab</b></font><br><br>Displays your shop transactions over a specified date range.  Transactions can be grouped by item or player."));
		tabs.addTab("Payments", paymentsTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Payments Tab</b></font><br><br>Displays payment transactions that are awaiting your review.  Payment transactions that are shop-related (such as buying an item in bulk) can be added to your shop transaction history.\n\nA payment transaction occurs when a player gives rupees to another player using the <code>\"/r pay\"</code> command."));
		tabs.addTab("Inventory", inventoryTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Inventory Tab</b></font><br><br>Allows you to define how much of each item you have in stock.  Your inventory is updated every time you download new transactions from EMC, so this tab will show you the items in your shop that are low in stock."));

		add(tabs, "span 3, h 100%, w 100%");
	}

	public void updatePaymentsCount() {
		try {
			String title = "Payments";
			int count = dao.countPendingPaymentTransactions();
			if (count > 0) {
				title += " (" + count + ")";
			}
			tabs.setTitleAt(1, title);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void updateInventoryTab() {
		inventoryTab.refresh();
	}

	/**
	 * Called after an update has completed.
	 */
	public void updateSuccessful(Date started, Integer rupeeTotal, long time, int transactionCount, int pageCount, boolean showResults) {
		String message;
		if (transactionCount == 0) {
			message = "No new transactions found.";
		} else {
			boolean firstUpdate = (settings.getLastUpdated() == null && settings.getPreviousUpdate() == null);

			//only set the previous update date if this update returned transactions
			//this line of code must run before the Transactions tab is updated
			settings.setPreviousUpdate(settings.getLastUpdated());

			transactionsTab.updateComplete(showResults, firstUpdate);
			if (showResults) {
				tabs.setSelectedComponent(transactionsTab);
			}

			updatePaymentsCount();
			if (tabs.getSelectedComponent() == paymentsTab) {
				paymentsTab.reset();
			} else {
				paymentsTab.setStale(true);
			}

			inventoryTab.refresh();

			long components[] = TimeUtils.parseTimeComponents(time);
			NumberFormat nf = NumberFormat.getInstance();
			StringBuilder sb = new StringBuilder();
			sb.append("Update complete.\n");
			sb.append(nf.format(pageCount)).append(" pages parsed and ");
			sb.append(nf.format(transactionCount)).append(" transactions added in ");
			if (components[3] > 0) {
				sb.append(components[3]).append(" hours, ");
			}
			if (components[3] > 0 || components[2] > 0) {
				sb.append(components[2]).append(" minutes and ");
			}
			sb.append(components[1]).append(" seconds.");
			message = sb.toString();
		}

		JOptionPane.showMessageDialog(this, message, "Update complete", JOptionPane.INFORMATION_MESSAGE);

		settings.setLastUpdated(started);
		settings.setRupeeBalance(rupeeTotal);
		try {
			settings.save();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem writing to settings file.", e);
		}

		updateLastUpdateDate(started);
		updateRupeeBalance();
	}

	private void updateLastUpdateDate(Date date) {
		String text;
		if (date == null) {
			text = "-";
		} else {
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
			text = df.format(date);
		}

		lastUpdateDate.setText(text);
	}

	private void updateRupeeBalance() {
		Integer balance = settings.getRupeeBalance();
		if (balance == null) {
			balance = 0;
		}

		String balanceStr = NumberFormatter.formatRupees(balance, false);
		rupeeBalance.setText("<html><h2><b>" + balanceStr + "</b></h2></html>");
	}

	///////////////////////////////////

	@Override
	public void windowActivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		settings.setWindowWidth(getWidth());
		settings.setWindowHeight(getHeight());
		try {
			settings.save();
		} catch (IOException e) {
			logger.log(Level.WARNING, "Problem persisting settings file.", e);
		}

		System.exit(0);
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
		//do nothing
	}
}