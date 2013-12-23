package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.GuiUtils.toolTipText;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
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

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import emcshop.EmcSession;
import emcshop.LogManager;
import emcshop.Main;
import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.MacSupport;
import emcshop.util.GuiUtils;
import emcshop.util.NumberFormatter;
import emcshop.util.Settings;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class MainFrame extends JFrame implements WindowListener {
	private static final Logger logger = Logger.getLogger(MainFrame.class.getName());

	private JButton update;
	private JLabel lastUpdateDate;
	private JLabel rupeeBalance;
	private JPanel updateAvailablePanel;

	private JTabbedPane tabs;
	private TransactionsTab transactionsTab;
	private PaymentsTab paymentsTab;
	private InventoryTab inventoryTab;
	private BonusFeeTab bonusFeeTab;

	JMenuItem clearSession;

	private final DbDao dao;
	private final Settings settings;
	private final LogManager logManager;
	private final ProfileImageLoader profileImageLoader;
	private final String profile;

	public MainFrame(Settings settings, DbDao dao, LogManager logManager, ProfileImageLoader profileImageLoader, String profile) throws SQLException {
		this.dao = dao;
		this.settings = settings;
		this.logManager = logManager;
		this.profileImageLoader = profileImageLoader;
		this.profile = profile;

		setTitle("EMC Shopkeeper v" + Main.VERSION);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		createMenu();
		createWidgets();
		layoutWidgets();
		setSize(settings.getWindowWidth(), settings.getWindowHeight());

		Image appIcon = ImageManager.getAppIcon().getImage();
		setIconImage(appIcon);

		updatePaymentsCount();

		addWindowListener(this);

		checkForNewVersion();
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
							settings.save();
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
					settings.save();
					clearSession.setEnabled(false);
					JOptionPane.showMessageDialog(MainFrame.this, "Session has been cleared.", "Session cleared", JOptionPane.INFORMATION_MESSAGE);
				}
			});
			clearSession.setEnabled(settings.getSession() != null);
			tools.add(clearSession);

			if (profile.equals("default")) {
				final JCheckBoxMenuItem showProfilesOnStartup = new JCheckBoxMenuItem("Show Profiles On Startup", settings.isShowProfilesOnStartup());
				showProfilesOnStartup.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						settings.setShowProfilesOnStartup(showProfilesOnStartup.isSelected());
						settings.save();
					}
				});
				tools.add(showProfilesOnStartup);
			}

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
									settings.save();
									lastUpdateDate.setText("-");
									updateRupeeBalance();
									transactionsTab.clear();
									updatePaymentsCount();
									paymentsTab.reset();
									inventoryTab.refresh();
									bonusFeeTab.refresh();
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
					settings.save();

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

		updateAvailablePanel = new JPanel(new MigLayout("insets 0"));

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
		bonusFeeTab = new BonusFeeTab(dao);
	}

	private void layoutWidgets() {
		setLayout(new MigLayout("insets 5 10 10 10, fill"));

		JPanel updatePanel = new JPanel(new MigLayout("insets 0"));
		updatePanel.add(update);
		updatePanel.add(updateAvailablePanel, "span 1 2, wrap");
		updatePanel.add(new JLabel("Last updated:"), "split 2");
		updatePanel.add(lastUpdateDate);

		add(updatePanel);

		add(new JLabel(ImageManager.getImageIcon("header.png")), "w 100%, align center");

		JPanel right = new JPanel(new MigLayout("insets 0"));

		if (!profile.equals("default")) {
			right.add(new JLabel("Profile: " + profile), "align right, wrap");
		}
		right.add(new JLabel("<html><h2>Rupees:</h2></html>"), "split 2, gapright 10, align right");
		right.add(rupeeBalance);
		add(right, "wrap");

		int index = 0;
		tabs.addTab("Transactions", transactionsTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Transactions Tab</b></font><br><br>Displays your shop transactions over a specified date range.  Transactions can be grouped by item or player."));
		tabs.addTab("Payments", paymentsTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Payments Tab</b></font><br><br>Displays payment transactions that are awaiting your review.  Payment transactions that are shop-related (such as buying an item in bulk) can be added to your shop transaction history.\n\nA payment transaction occurs when a player gives rupees to another player using the <code>\"/r pay\"</code> command."));
		tabs.addTab("Inventory", inventoryTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Inventory Tab</b></font><br><br>Allows you to define how much of each item you have in stock.  Your inventory is updated every time you download new transactions from EMC, so this tab will show you the items in your shop that are low in stock."));
		tabs.addTab("Bonuses/Fees", bonusFeeTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Bonuses/Fees Tab</b></font><br><br>Keeps a tally of the rupee bonuses and fees your account has received.  This tally is updated every time you update your transactions."));

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
			bonusFeeTab.refresh();

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
		settings.save();

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

	private void checkForNewVersion() {
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					logger.log(Level.FINEST, "Checking for updates.");
					String json;
					try {
						json = downloadCommitInfo();
					} catch (IOException e) {
						//problem downloading file
						return;
					}

					Date latestRelease = parseLatestReleaseDate(json);
					if (latestRelease == null) {
						//couldn't find the release date
						return;
					}

					long diff = latestRelease.getTime() - Main.BUILT.getTime();
					if (diff < 1000 * 60 * 10) { //give a buffer of 10 minutes because there will be a few minutes difference between the build timestamp and the commit timestamp
						//already running the latest version
						logger.log(Level.FINEST, "Running latest version.");
						return;
					}

					JButton downloadUpdate = new JButton("Download");
					downloadUpdate.setIcon(ImageManager.getImageIcon("download.png"));
					downloadUpdate.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent event) {
							try {
								GuiUtils.openWebPage(URI.create("https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper-full.jar"));
							} catch (IOException e) {
								throw new RuntimeException("Error opening webpage.", e);
							}
						}
					});

					updateAvailablePanel.add(new JLabel("<html><center><b>New Version Available!</b></center></html>"), "gapleft 10, align center, wrap");
					updateAvailablePanel.add(downloadUpdate, "gapleft 10, align center");
					validate();
				} catch (Throwable e) {
					logger.log(Level.WARNING, "Problem checking for updates.", e);
				}
			}

			private String downloadCommitInfo() throws IOException {
				String url = "https://api.github.com/repos/mangstadt/emc-shopkeeper/commits?path=" + URLEncoder.encode("dist/emc-shopkeeper-full.jar", "UTF-8");

				DefaultHttpClient client = new DefaultHttpClient();
				HttpGet request = new HttpGet(url);

				HttpResponse response = client.execute(request);
				HttpEntity entity = response.getEntity();
				String json = IOUtils.toString(entity.getContent());
				EntityUtils.consume(entity);
				return json;
			}

			private Date parseLatestReleaseDate(String json) {
				Pattern p = Pattern.compile("\"date\":\"(.*?)\"");
				Matcher m = p.matcher(json);
				if (!m.find()) {
					logger.log(Level.WARNING, "Could not find release date in Github commit log: " + json);
					return null;
				}

				DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
				df.setTimeZone(TimeZone.getTimeZone("GMT"));
				try {
					return df.parse(m.group(1));
				} catch (ParseException e) {
					logger.log(Level.WARNING, "Could not parse release date from Github commit log.", e);
					return null;
				}
			}
		};
		t.setDaemon(true);
		t.start();
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
		settings.save();

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