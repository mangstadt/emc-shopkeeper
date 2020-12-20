package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.GuiUtils.toolTipText;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.github.mangstadt.emc.rupees.RupeeTransactionReader;

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
import emcshop.util.GitHubCommitsApi;
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
import net.miginfocom.swing.MigLayout;

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

	public MainFrame(String profile) {
		this.profile = profile;
		dao = context.get(DbDao.class);
		settings = context.get(Settings.class);
		logManager = context.get(LogManager.class);

		setTitle("EMC Shopkeeper v" + EMCShopkeeper.VERSION);
		setIconImage(Images.APP_ICON.getImage());
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		progressPanel = new InfiniteProgressPanel();
		setGlassPane(progressPanel);
		ignoreKeyEvents = event -> true;

		createMenu();
		createWidgets();
		layoutWidgets();

		WindowState state = settings.getWindowState();
		if (state == null) {
			state = new WindowState(Collections.emptyMap(), null, new Dimension(800, 600), null);
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

		JMenu tools = menu.addMenu("Tools")
		.icon(Images.TOOLS)
		.add();
		{
			menu.addMenuItem("Open Profile Folder...")
			.parent(tools)
			.add(event -> {
				Path folder = logManager.getFile().getParent();
				if (folder == null) {
					folder = logManager.getFile().resolveSibling(".");
				}
				
				try {
					GuiUtils.openFile(folder);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			
			menu.addMenuItem("Show Application Log...")
			.parent(tools)
			.add(event -> ShowLogDialog.show(this));
			
			menu.addMenuItem("Chat Log Viewer...")
			.icon(Images.CHAT_LARGE)
			.parent(tools)
			.add(event -> {
				IChatLogViewerView view = new ChatLogViewerViewImpl(this);
				IChatLogViewerModel model = new ChatLogViewerModelImpl();
				new ChatLogViewerPresenter(view, model);
			});
			
			clearSessionMenuItem = menu.addMenuItem("Clear Saved Session")
			.parent(tools)
			.add(event -> {
				context.remove(EmcSession.class);
				clearSessionMenuItem.setEnabled(false);
				
				DialogBuilder.info()
					.parent(this)
					.title("Session cleared")
					.text("Session has been cleared.")
				.show();
			});
			clearSessionMenuItem.setEnabled(context.get(EmcSession.class) != null);
			
			menu.addMenuItem("Wipe Database...")
			.icon(Images.WIPE_DATABASE)
			.parent(tools)
			.add(event -> onWipeDatabase());
		}

		JMenu settingsMenu = menu.addMenu("Settings")
		.icon(Images.SETTINGS)
		.add();
		{
			menu.addMenuItem("Database Backup...")
			.icon(Images.BACKUP_DATABASE)
			.parent(settingsMenu)
			.add(event -> {
				IBackupView view = new BackupViewImpl(this);
				IBackupModel model = new BackupModelImpl();
				BackupPresenter presenter = new BackupPresenter(view, model);
				if (presenter.getExit()) {
					exit();
				}
			});

			JMenu logLevel = menu.addMenu("Log Level")
			.parent(settingsMenu)
			.add();
			{
				Map<String, Level> levels = new LinkedHashMap<>();
				levels.put("Detailed", Level.FINEST);
				levels.put("Normal", Level.INFO);
				levels.put("Off", Level.OFF);
				ButtonGroup group = new ButtonGroup();
				for (Map.Entry<String, Level> entry : levels.entrySet()) {
					String name = entry.getKey();
					final Level level = entry.getValue();

					JRadioButtonMenuItem levelItem = menu.addRadioButtonMenuItem(name, group)
					.parent(logLevel)
					.add(event -> {
						logger.finest("Changing log level to " + level.getName() + ".");
						logManager.setLevel(level);
						settings.setLogLevel(level);
						settings.save();
					});

					if (logManager.getLevel().equals(level)) {
						levelItem.setSelected(true);
					}
				}
			}

			JCheckBoxMenuItem stacks = menu.addCheckboxMenuItem("Show Quantities in Stacks")
			.icon(Images.STACK)
			.parent(settingsMenu)
			.add(event -> {
				JCheckBoxMenuItem source = (JCheckBoxMenuItem) event.getSource();
				boolean showStacks = source.isSelected();
				settings.setShowQuantitiesInStacks(showStacks);
				settings.save();

				inventoryTab.setShowQuantitiesInStacks(showStacks);
				transactionsTab.setShowQuantitiesInStacks(showStacks);
			});
			stacks.setSelected(settings.isShowQuantitiesInStacks());

			if (profile.equals(EMCShopkeeper.defaultProfileName)) {
				JCheckBoxMenuItem showProfiles = menu.addCheckboxMenuItem("Show Profiles on Startup")
				.icon(Images.SHOW_PROFILES)
				.parent(settingsMenu)
				.add(event -> {
					JCheckBoxMenuItem source = (JCheckBoxMenuItem) event.getSource();
					settings.setShowProfilesOnStartup(source.isSelected());
					settings.save();
				});
				showProfiles.setSelected(settings.isShowProfilesOnStartup());
			}
			
			JCheckBoxMenuItem reportUnknownItems = menu.addCheckboxMenuItem("Report Unknown Items")
			.icon(Images.REPORT_UNKNOWN_ITEMS)
			.parent(settingsMenu)
			.add(event -> {
				JCheckBoxMenuItem source = (JCheckBoxMenuItem) event.getSource();
				boolean report = source.isSelected();
				settings.setReportUnknownItems(report);
				settings.save();
			});
			reportUnknownItems.setSelected(settings.isReportUnknownItems());

			menu.addMenuItem("Set Download Threads...")
			.parent(settingsMenu)
			.add(event -> {
				final String valueForRecommended = "recommended";
				String textboxValue = settings.getUseRecommendedDownloadThreads() ? valueForRecommended : (settings.getDownloadThreads() + "");
				int cores = Runtime.getRuntime().availableProcessors();

				while (true) {
					String answer = DialogBuilder.question() //@formatter:off
						.title("Download Threads")
						.text("THIS SETTING IS FOR POWER USERS ONLY! :)",
							"This screen allows you to define the number threads you want to use for downloading",
							"and parsing rupee transactions.",
							"",
							"Each thread is responsible for downloading a rupee history transaction page and then",
							"extracting the transactions out of the page. Another thread (not included in this count)",
							"is responsible for inserting the transactions into the EMC Shopkeeper database.",
							"",
							"Your computer has " + cores + " logical cores. It is recommended that this setting be set to the number",
							"of logical cores you computer has, or 4 if your computer has less than 4 cores.",
							"",
							//using the string "recommended" allows the setting to be transferable between computers
							"Type \"" + valueForRecommended + "\" to use the recommended setting, or the number of threads you",
							"specifically want to use.")
						.showInput(textboxValue); //@formatter:on

					boolean dialogWasCanceled = (answer == null);
					if (dialogWasCanceled) {
						return;
					}

					if (valueForRecommended.equalsIgnoreCase(answer)) {
						settings.setUseRecommendedDownloadThreads();
						break;
					} else {
						int newValue;
						try {
							newValue = Integer.parseInt(answer);
						} catch (NumberFormatException e) {
							//bad input, ask again
							continue;
						}

						if (newValue <= 0) {
							//bad input, ask again
							continue;
						}

						settings.setDownloadThreads(newValue);
						break;
					}
				}

				settings.save();
			});
		}

		menu.addSeparator();

		menu.addMenuItem("Changelog")
		.icon(Images.CHANGELOG)
		.add(event -> ChangelogDialog.show(this));

		menu.addMenuItem("About")
		.icon(Images.HELP)
		.add(event -> AboutDialog.show(this));

		menu.addMenuItem("Exit")
		.add(event -> exit());
		//@formatter:on
	}

	private void createWidgets() {
		update = new JButton("Update Transactions", Images.UPDATE);
		update.setToolTipText(toolTipText("<font size=4><b>Update Transactions</b></font><br><br>Downloads your latest transactions from the EMC website."));
		update.addActionListener(event -> onUpdate());

		updateAvailablePanel = new JPanel(new MigLayout("insets 0"));

		lastUpdateDate = new JLabel();
		updateLastUpdateDate();

		rupeeBalance = new JLabel();
		updateRupeeBalance();

		tabs = new JTabbedPane();
		tabs.addChangeListener(event -> {
			Component selected = tabs.getSelectedComponent();
			if (selected == paymentsTab && paymentsTab.isStale()) {
				paymentsTab.reset();
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

	private void onUpdate() {
		LocalDateTime latestTransactionDate;
		try {
			latestTransactionDate = dao.getLatestTransactionDate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		Duration maxPaymentTransactionAge = null;
		Integer stopAtPage = null;
		boolean isFirstUpdate = (latestTransactionDate == null);
		if (isFirstUpdate) {
			IFirstUpdateView view = new FirstUpdateViewImpl(this);
			view.setMaxPaymentTransactionAge(Duration.ofDays(7));
			view.setStopAtPage(5000);

			IFirstUpdateModel model = new FirstUpdateModelImpl();

			FirstUpdatePresenter presenter = new FirstUpdatePresenter(view, model);
			if (presenter.isCanceled()) {
				return;
			}

			stopAtPage = presenter.getStopAtPage();
			maxPaymentTransactionAge = presenter.getMaxPaymentTransactionAge();
		}

		LoginShower loginShower = new LoginShower();

		EmcSession session = context.get(EmcSession.class);
		if (session == null) {
			//user hasn't logged in
			LoginPresenter p = loginShower.show(this);
			if (p.isCanceled()) {
				return;
			}
			session = p.getSession();
		}
		
		clearSessionMenuItem.setEnabled(true);

		RupeeTransactionReader.Builder builder = new RupeeTransactionReader.Builder(session.getCookieStore());
		builder.threads(settings.getDownloadThreads());
		if (stopAtPage != null) {
			builder.stop(stopAtPage);
		}
		if (latestTransactionDate != null) {
			builder.stop(latestTransactionDate);
		}

		//show the update dialog
		IUpdateView view = new UpdateViewImpl(this, loginShower);
		IUpdateModel model = new UpdateModelImpl(builder, maxPaymentTransactionAge);
		UpdatePresenter presenter = new UpdatePresenter(view, model);

		if (!presenter.isCanceled()) {
			try {
				updateSuccessful(presenter.getStarted(), presenter.getRupeeBalance(), presenter.getTimeTaken(), presenter.getShopTransactions(), presenter.getPaymentTransactions(), presenter.getBonusFeeTransactions(), presenter.getPageCount(), presenter.getShowResults());
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void onWipeDatabase() {
		boolean reset = ResetDatabaseDialog.show(this);
		if (!reset) {
			return;
		}

		startProgress("Resetting Database...");
		Thread t = new Thread(() -> {
			try {
				dao.wipe();
				context.remove(EmcSession.class);
				settings.save();

				SwingUtilities.invokeAndWait(() -> {
					clearSessionMenuItem.setEnabled(false);
					lastUpdateDate.setText("-");
					updateRupeeBalance(0);
					transactionsTab.clear();
					updatePaymentsCount(0);
					paymentsTab.reset();
					inventoryTab.refresh();
					bonusFeeTab.refresh();
					graphsTab.clear();
				});
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				stopProgress();
			}
		});
		t.start();
	}

	public void updatePaymentsCount() {
		int count;
		try {
			count = dao.countPendingPaymentTransactions();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		updatePaymentsCount(count);
	}

	public void updatePaymentsCount(int count) {
		String title = "Payments";
		if (count > 0) {
			title += " (" + count + ")";
		}

		for (int i = 0; i < tabs.getTabCount(); i++) {
			if (paymentsTab == tabs.getComponentAt(i)) {
				tabs.setTitleAt(i, title);
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
	private void updateSuccessful(LocalDateTime started, Integer rupeeTotal, Duration time, int shopTransactionCount, int paymentTransactionCount, int bonusFeeTransactionCount, int pageCount, boolean showResults) throws SQLException {
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

			Map<ChronoUnit, Long> components = TimeUtils.parseTimeComponents(time);
			NumberFormat nf = NumberFormat.getInstance();
			StringBuilder sb = new StringBuilder();
			sb.append("Update complete.\n");
			sb.append(nf.format(pageCount)).append(" pages parsed and ");
			sb.append(nf.format(totalTransactions)).append(" transactions parsed in ");
			if (components.get(ChronoUnit.HOURS) > 0) {
				sb.append(components.get(ChronoUnit.HOURS)).append(" hours, ");
			}
			if (components.get(ChronoUnit.HOURS) > 0 || components.get(ChronoUnit.MINUTES) > 0) {
				sb.append(components.get(ChronoUnit.MINUTES)).append(" minutes and ");
			}
			sb.append(components.get(ChronoUnit.SECONDS)).append(" seconds.");
			message = sb.toString();

			updateLastUpdateDate(started);
			updateRupeeBalance(rupeeTotal);
		}

		DialogBuilder.info() //@formatter:off
			.parent(this)
			.title("Update complete")
			.text(message)
		.show(); //@formatter:on
	}

	private void updateLastUpdateDate() {
		LocalDateTime date;
		try {
			date = dao.getLatestUpdateDate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		updateLastUpdateDate(date);
	}

	private void updateLastUpdateDate(LocalDateTime date) {
		String text;
		if (date == null) {
			text = "-";
		} else {
			DateTimeFormatter df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
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
			Map<String, Object> map = new HashMap<>(state.getComponentValues());
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

		Thread t = new Thread(() -> {
			GitHubCommitsApi gitHub = new GitHubCommitsApi("mangstadt", "emc-shopkeeper");
			try {
				logger.finest("Checking for updates.");
				Instant latestRelease = gitHub.getDateOfLatestCommit("dist/emc-shopkeeper-full.jar");
				if (latestRelease == null) {
					//couldn't find the release date
					return;
				}

				/*
				 * Allow for a buffer of 10 minutes because there will be a few
				 * minutes difference between the build timestamp and the commit
				 * timestamp.
				 */
				Duration diff = Duration.between(EMCShopkeeper.BUILT, latestRelease);
				if (diff.toMinutes() < 10) {
					//already running the latest version
					logger.finest("Running latest version.");
					return;
				}

				SwingUtilities.invokeLater(() -> {
					updateAvailablePanel.add(new JLabel("<html><center><b>New Version Available!</b></center></html>"), "gapleft 10, align center, wrap");

					if (GuiUtils.canOpenWebPages()) {
						JButton downloadUpdate = new JButton("Download");
						downloadUpdate.setIcon(Images.DOWNLOAD);
						downloadUpdate.addActionListener(event -> {
							try {
								GuiUtils.openWebPage(URI.create("https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper-full.jar"));
							} catch (IOException e) {
								throw new RuntimeException("Error opening webpage.", e);
							}
						});
						updateAvailablePanel.add(downloadUpdate, "gapleft 10, align center");
					}

					validate();
				});
			} catch (Exception e) {
				logger.log(Level.WARNING, "Problem checking for updates.", e);
			}
		});
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}
}
