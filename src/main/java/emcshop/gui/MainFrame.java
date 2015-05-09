package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.GuiUtils.toolTipText;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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

import emcshop.AppContext;
import emcshop.EMCShopkeeper;
import emcshop.LogManager;
import emcshop.Settings;
import emcshop.db.DbDao;
import emcshop.gui.images.Images;
import emcshop.gui.lib.InfiniteProgressPanel;
import emcshop.gui.lib.JarSignersHardLinker;
import emcshop.model.BackupModelImpl;
import emcshop.model.ChatLogViewerModelImpl;
import emcshop.model.FirstUpdateModelImpl;
import emcshop.model.IBackupModel;
import emcshop.model.IChatLogViewerModel;
import emcshop.model.IFirstUpdateModel;
import emcshop.model.IUpdateModel;
import emcshop.model.UpdateModelImpl;
import emcshop.presenter.BackupPresenter;
import emcshop.presenter.ChatLogViewerPresenter;
import emcshop.presenter.FirstUpdatePresenter;
import emcshop.presenter.LoginPresenter;
import emcshop.presenter.UpdatePresenter;
import emcshop.scraper.EmcSession;
import emcshop.scraper.TransactionPullerFactory;
import emcshop.util.GuiUtils;
import emcshop.util.RupeeFormatter;
import emcshop.util.TimeUtils;
import emcshop.view.BackupViewImpl;
import emcshop.view.ChatLogViewerViewImpl;
import emcshop.view.FirstUpdateViewImpl;
import emcshop.view.IBackupView;
import emcshop.view.IChatLogViewerView;
import emcshop.view.IFirstUpdateView;
import emcshop.view.IUpdateView;
import emcshop.view.LoginShower;
import emcshop.view.UpdateViewImpl;

@SuppressWarnings("serial")
public class MainFrame extends JFrame {
	private static final Logger logger = Logger.getLogger(MainFrame.class.getName());
	private static final AppContext context = AppContext.instance();

	private final InfiniteProgressPanel progressPanel;
	private final KeyEventDispatcher ignoreKeyEvents;

	private JButton update;
	private JLabel lastUpdateDate;
	private JLabel rupeeBalance;
	private JPanel updateAvailablePanel;

	private JTabbedPane tabs;
	private TransactionsTab transactionsTab;
	private PaymentsTab paymentsTab;
	private InventoryTab inventoryTab;
	private BonusFeeTab bonusFeeTab;
	private ChartsTab graphsTab;
	private JMenuItem clearSessionMenuItem;
	private MenuButton menu;

	private final DbDao dao;
	private final Settings settings;
	private final LogManager logManager;
	private final String profile;

	public MainFrame(String profile) throws SQLException {
		this.profile = profile;
		dao = context.get(DbDao.class);
		settings = context.get(Settings.class);
		logManager = context.get(LogManager.class);

		setTitle("EMC Shopkeeper v" + EMCShopkeeper.VERSION);
		setIconImage(Images.APP_ICON.getImage());
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		progressPanel = new InfiniteProgressPanel();
		setGlassPane(progressPanel);
		ignoreKeyEvents = new KeyEventDispatcher() {
			@Override
			public boolean dispatchKeyEvent(KeyEvent event) {
				return true;
			}
		};

		createMenu();
		createWidgets();
		layoutWidgets();

		WindowState state = settings.getWindowState();
		if (state == null) {
			state = new WindowState(Collections.<String, Object> emptyMap(), null, new Dimension(800, 600), null);
		}
		state.applyTo(this);
		transactionsTab.afterPopulate();

		updatePaymentsCount();

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent arg0) {
				exit();
			}
		});

		checkForNewVersion();
	}

	/**
	 * Shows a loading screen.
	 * @param caption the caption to display
	 */
	public void startProgress(String caption) {
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ignoreKeyEvents);
		busyCursor(this, true);
		progressPanel.setText(caption);
		progressPanel.start();
	}

	/**
	 * Hides the loading screen.
	 */
	public void stopProgress() {
		progressPanel.stop();
		busyCursor(this, false);
		KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(ignoreKeyEvents);
	}

	private void createMenu() {
		//@formatter:off
		menu = new MenuButton();
		menu.setOffset(0, 32);

		JMenu tools = menu.addMenu("Tools")
		.icon(Images.TOOLS)
		.add();
		{
			menu.addMenuItem("Log...")
			.parent(tools)
			.add(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					ShowLogDialog.show(MainFrame.this);
				}
			});
			
			menu.addMenuItem("Chat Log Viewer...")
			.icon(Images.CHAT_LARGE)
			.parent(tools)
			.add(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					IChatLogViewerView view = new ChatLogViewerViewImpl(MainFrame.this);
					IChatLogViewerModel model = new ChatLogViewerModelImpl();
					new ChatLogViewerPresenter(view, model);
				}
			});
			
			clearSessionMenuItem = menu.addMenuItem("Clear Saved Session")
			.parent(tools)
			.add(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					context.remove(EmcSession.class);
					clearSessionMenuItem.setEnabled(false);
					JOptionPane.showMessageDialog(MainFrame.this, "Session has been cleared.", "Session cleared", JOptionPane.INFORMATION_MESSAGE);
				}
			});
			clearSessionMenuItem.setEnabled(context.get(EmcSession.class) != null);
			
			menu.addMenuItem("Wipe Database...")
			.icon(Images.WIPE_DATABASE)
			.parent(tools)
			.add(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					onWipeDatabase();
				}
			});
		}

		JMenu settingsMenu = menu.addMenu("Settings")
		.icon(Images.SETTINGS)
		.add();
		{
			menu.addMenuItem("Database Backup...")
			.icon(Images.BACKUP_DATABASE)
			.parent(settingsMenu)
			.add(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					IBackupView view = new BackupViewImpl(MainFrame.this);
					IBackupModel model = new BackupModelImpl();
					BackupPresenter presenter = new BackupPresenter(view, model);
					if (presenter.getExit()) {
						exit();
					}
				}
			});

			JMenu logLevel = menu.addMenu("Log Level")
			.parent(settingsMenu)
			.add();
			{
				Map<String, Level> levels = new LinkedHashMap<String, Level>();
				levels.put("Detailed", Level.FINEST);
				levels.put("Normal", Level.INFO);
				levels.put("Off", Level.OFF);
				ButtonGroup group = new ButtonGroup();
				for (Map.Entry<String, Level> entry : levels.entrySet()) {
					String name = entry.getKey();
					final Level level = entry.getValue();

					JRadioButtonMenuItem levelItem = menu.addRadioButtonMenuItem(name, group)
					.parent(logLevel)
					.add(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent event) {
							logger.finest("Changing log level to " + level.getName() + ".");
							logManager.setLevel(level);
							settings.setLogLevel(level);
							settings.save();
						}
					});

					if (logManager.getLevel().equals(level)) {
						levelItem.setSelected(true);
					}
				}
			}

			JCheckBoxMenuItem stacks = menu.addCheckboxMenuItem("Show Quantities in Stacks")
			.icon(Images.STACK)
			.parent(settingsMenu)
			.add(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					JCheckBoxMenuItem source = (JCheckBoxMenuItem) event.getSource();
					boolean stacks = source.isSelected();
					settings.setShowQuantitiesInStacks(stacks);
					settings.save();

					inventoryTab.setShowQuantitiesInStacks(stacks);
					transactionsTab.setShowQuantitiesInStacks(stacks);
				}
			});
			stacks.setSelected(settings.isShowQuantitiesInStacks());

			if (profile.equals(EMCShopkeeper.defaultProfileName)) {
				JCheckBoxMenuItem showProfiles = menu.addCheckboxMenuItem("Show Profiles on Startup")
				.icon(Images.SHOW_PROFILES)
				.parent(settingsMenu)
				.add(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent event) {
						JCheckBoxMenuItem source = (JCheckBoxMenuItem) event.getSource();
						settings.setShowProfilesOnStartup(source.isSelected());
						settings.save();
					}
				});
				showProfiles.setSelected(settings.isShowProfilesOnStartup());
			}
		}

		menu.addSeparator();

		menu.addMenuItem("Changelog")
		.icon(Images.CHANGELOG)
		.add(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				ChangelogDialog.show(MainFrame.this);
			}
		});

		menu.addMenuItem("About")
		.icon(Images.HELP)
		.add(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				AboutDialog.show(MainFrame.this);
			}
		});

		menu.addMenuItem("Exit")
		.add(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				exit();
			}
		});
		//@formatter:on
	}

	private void createWidgets() {
		update = new JButton("Update Transactions", Images.UPDATE);
		update.setToolTipText(toolTipText("<font size=4><b>Update Transactions</b></font><br><br>Downloads your latest transactions from the EMC website."));
		update.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				LoginShower loginShower = new LoginShower();

				if (context.get(EmcSession.class) == null) {
					//user hasn't logged in
					LoginPresenter p = loginShower.show(MainFrame.this);
					if (p.isCanceled()) {
						return;
					}
				}

				clearSessionMenuItem.setEnabled(true);

				Date latestTransactionDate;
				try {
					latestTransactionDate = dao.getLatestTransactionDate();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}

				TransactionPullerFactory pullerFactory = new TransactionPullerFactory();
				if (latestTransactionDate == null) {
					//it's the first update

					IFirstUpdateView view = new FirstUpdateViewImpl(MainFrame.this);
					view.setMaxPaymentTransactionAge(7);
					view.setStopAtPage(5000);
					IFirstUpdateModel model = new FirstUpdateModelImpl();
					FirstUpdatePresenter presenter = new FirstUpdatePresenter(view, model);
					if (presenter.isCanceled()) {
						return;
					}

					Integer stopAtPage = presenter.getStopAtPage();
					pullerFactory.setStopAtPage(stopAtPage);

					Integer oldestPaymentTransactionDays = presenter.getMaxPaymentTransactionAge();
					pullerFactory.setMaxPaymentTransactionAge(oldestPaymentTransactionDays);
				} else {
					pullerFactory.setStopAtDate(latestTransactionDate);
				}

				//show the update dialog
				IUpdateView view = new UpdateViewImpl(MainFrame.this, loginShower);
				IUpdateModel model = new UpdateModelImpl(pullerFactory, context.get(EmcSession.class));
				UpdatePresenter presenter = new UpdatePresenter(view, model);

				if (!presenter.isCanceled()) {
					try {
						updateSuccessful(presenter.getStarted(), presenter.getRupeeBalance(), presenter.getTimeTaken(), presenter.getShopTransactions(), presenter.getPaymentTransactions(), presenter.getBonusFeeTransactions(), presenter.getPageCount(), presenter.getShowResults());
					} catch (SQLException e) {
						throw new RuntimeException(e);
					}
				}
			}
		});

		updateAvailablePanel = new JPanel(new MigLayout("insets 0"));

		lastUpdateDate = new JLabel();
		updateLastUpdateDate();

		rupeeBalance = new JLabel();
		updateRupeeBalance();

		tabs = new JTabbedPane();
		tabs.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Component selected = tabs.getSelectedComponent();

				if (selected == paymentsTab && paymentsTab.isStale()) {
					paymentsTab.reset();
				}
			}
		});

		transactionsTab = new TransactionsTab(this);
		paymentsTab = new PaymentsTab(this);
		inventoryTab = new InventoryTab(this);
		bonusFeeTab = new BonusFeeTab(dao);
		graphsTab = new ChartsTab(this, dao);
	}

	private void layoutWidgets() {
		setLayout(new MigLayout("insets 5 10 10 10, fill"));

		add(menu);

		JPanel updatePanel = new JPanel(new MigLayout("insets 0"));
		updatePanel.add(update);
		updatePanel.add(updateAvailablePanel, "span 1 2, wrap");
		updatePanel.add(new JLabel("Last updated:"), "split 2");
		updatePanel.add(lastUpdateDate);

		add(updatePanel);

		add(new JLabel(Images.HEADER), "w 100%, align center");

		JPanel right = new JPanel(new MigLayout("insets 0"));

		if (!profile.equals(EMCShopkeeper.defaultProfileName)) {
			right.add(new JLabel("Profile: " + profile), "align right, wrap");
		}
		right.add(new JLabel("<html><h2>Rupees:</h2></html>"), "split 2, gapright 10, align right");
		right.add(rupeeBalance);
		add(right, "wrap");

		int index = 0;
		tabs.addTab("Transactions", transactionsTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Transactions</b></font><br><br>Displays your shop's transactions."));

		tabs.addTab("Payments", paymentsTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Payments</b></font><br><br>Displays payment transactions that are awaiting your review.  Payment transactions that are shop-related (such as buying an item in bulk) can be added to your shop transaction history.\n\nA payment transaction occurs when a player gives rupees to another player using the <code>\"/r pay\"</code> command."));

		tabs.addTab("Inventory", inventoryTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Shop Inventory</b></font><br><br>Allows you to define how much of each item your shop has in stock.  Your inventory is updated automatically every time you run an update."));

		tabs.addTab("Bonuses/Fees", bonusFeeTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Bonuses/Fees</b></font><br><br>Keeps a tally of the rupee bonuses and fees your account has received.  This tally is updated every time you update your transactions."));

		tabs.addTab("Charts", graphsTab);
		tabs.setToolTipTextAt(index++, toolTipText("<font size=4><b>Charts</b></font><br><br>Generates graphs of your shop transaction data."));

		add(tabs, "span 4, h 100%, w 100%");
	}

	private void onWipeDatabase() {
		boolean reset = ResetDatabaseDialog.show(MainFrame.this);
		if (!reset) {
			return;
		}

		startProgress("Resetting Database...");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					dao.wipe();
					context.remove(EmcSession.class);
					settings.save();
					clearSessionMenuItem.setEnabled(false);
					lastUpdateDate.setText("-");
					updateRupeeBalance();
					transactionsTab.clear();
					updatePaymentsCount();
					paymentsTab.reset();
					inventoryTab.refresh();
					bonusFeeTab.refresh();
					graphsTab.clear();
				} catch (Throwable e) {
					throw new RuntimeException(e);
				} finally {
					stopProgress();
				}
			}
		};
		t.start();
	}

	public void updatePaymentsCount() {
		int count;
		try {
			count = dao.countPendingPaymentTransactions();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		StringBuilder sb = new StringBuilder("Payments");
		if (count > 0) {
			sb.append(" (").append(count).append(")");
		}

		for (int i = 0; i < tabs.getTabCount(); i++) {
			if (paymentsTab == tabs.getComponentAt(i)) {
				tabs.setTitleAt(i, sb.toString());
				break;
			}
		}
	}

	public void updateInventoryTab() {
		inventoryTab.refresh();
	}

	/**
	 * Called after an update has completed.
	 * @throws SQLException
	 */
	private void updateSuccessful(Date started, Integer rupeeTotal, long time, int shopTransactionCount, int paymentTransactionCount, int bonusFeeTransactionCount, int pageCount, boolean showResults) throws SQLException {
		int totalTransactions = shopTransactionCount + paymentTransactionCount + bonusFeeTransactionCount;
		String message;
		if (totalTransactions == 0) {
			message = "No new transactions found.";
		} else {
			//is this the first update?
			boolean firstUpdate = (dao.getSecondLatestUpdateDate() == null);

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
			sb.append(nf.format(totalTransactions)).append(" transactions parsed in ");
			if (components[3] > 0) {
				sb.append(components[3]).append(" hours, ");
			}
			if (components[3] > 0 || components[2] > 0) {
				sb.append(components[2]).append(" minutes and ");
			}
			sb.append(components[1]).append(" seconds.");
			message = sb.toString();

			updateLastUpdateDate(started);
			updateRupeeBalance(rupeeTotal);
		}

		JOptionPane.showMessageDialog(this, message, "Update complete", JOptionPane.INFORMATION_MESSAGE);
	}

	private void updateLastUpdateDate() {
		Date date;
		try {
			date = dao.getLatestUpdateDate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		updateLastUpdateDate(date);
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
		Integer balance;
		try {
			balance = dao.selectRupeeBalance();
			if (balance == null) {
				balance = 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		updateRupeeBalance(balance);
	}

	private void updateRupeeBalance(int balance) {
		RupeeFormatter rf = new RupeeFormatter();
		String balanceStr = rf.format(balance);
		rupeeBalance.setText("<html><h2><b>" + balanceStr);
	}

	public void exit() {
		WindowState state = WindowState.of(this);
		if (!Boolean.TRUE.equals(state.getComponentValues().get("dateRange.range"))) {
			Map<String, Object> map = new HashMap<String, Object>(state.getComponentValues());
			map.remove("dateRange.from");
			map.remove("dateRange.to");
			state = new WindowState(map, state.getLocation(), state.getSize(), state.getState());
		}
		settings.setWindowState(state);
		settings.save();

		System.exit(0);
	}

	private void checkForNewVersion() {
		if (JarSignersHardLinker.isRunningOnWebstart()) {
			return;
		}

		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					logger.finest("Checking for updates.");
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

					long diff = latestRelease.getTime() - EMCShopkeeper.BUILT.getTime();
					if (diff < 1000 * 60 * 10) { //give a buffer of 10 minutes because there will be a few minutes difference between the build timestamp and the commit timestamp
						//already running the latest version
						logger.finest("Running latest version.");
						return;
					}

					updateAvailablePanel.add(new JLabel("<html><center><b>New Version Available!</b></center></html>"), "gapleft 10, align center, wrap");

					if (GuiUtils.canOpenWebPages()) {
						JButton downloadUpdate = new JButton("Download");
						downloadUpdate.setIcon(Images.DOWNLOAD);
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
						updateAvailablePanel.add(downloadUpdate, "gapleft 10, align center");
					}

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
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}
}