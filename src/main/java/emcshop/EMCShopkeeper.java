package emcshop;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import joptsimple.OptionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import emcshop.cli.CliController;
import emcshop.cli.EmcShopArguments;
import emcshop.db.DbDao;
import emcshop.db.DbListener;
import emcshop.db.DirbyDbDao;
import emcshop.db.DirbyEmbeddedDbDao;
import emcshop.gui.AboutDialog;
import emcshop.gui.ItemSuggestField;
import emcshop.gui.MainFrame;
import emcshop.gui.OnlinePlayersMonitor;
import emcshop.gui.ProfileLoader;
import emcshop.gui.SplashFrame;
import emcshop.gui.images.Images;
import emcshop.gui.lib.JarSignersHardLinker;
import emcshop.gui.lib.MacHandler;
import emcshop.gui.lib.MacSupport;
import emcshop.model.DatabaseStartupErrorModelImpl;
import emcshop.model.IDatabaseStartupErrorModel;
import emcshop.model.IProfileSelectorModel;
import emcshop.model.ProfileSelectorModelImpl;
import emcshop.presenter.DatabaseStartupErrorPresenter;
import emcshop.presenter.ProfileSelectorPresenter;
import emcshop.presenter.UnhandledErrorPresenter;
import emcshop.scraper.EmcSession;
import emcshop.scraper.OnlinePlayersScraper;
import emcshop.util.GuiUtils;
import emcshop.util.HttpClientFactory;
import emcshop.util.ZipUtils.ZipListener;
import emcshop.view.DatabaseStartupErrorViewImpl;
import emcshop.view.IDatabaseStartupErrorView;
import emcshop.view.IProfileSelectorView;
import emcshop.view.ProfileSelectorViewImpl;

public class EMCShopkeeper {
	private static final Logger logger = Logger.getLogger(EMCShopkeeper.class.getName());
	private static final PrintStream out = System.out;

	/**
	 * The version of the app.
	 */
	public static final String VERSION;

	/**
	 * The project webpage.
	 */
	public static final String URL;

	/**
	 * The date the application was built.
	 */
	public static final Date BUILT;

	/**
	 * The version of the cache;
	 */
	public static final String CACHE_VERSION = "1";

	static {
		InputStream in = EMCShopkeeper.class.getResourceAsStream("/info.properties");
		Properties props = new Properties();
		try {
			props.load(in);
		} catch (Throwable t) {
			throw new ExceptionInInitializerError(t);
		} finally {
			IOUtils.closeQuietly(in);
		}

		VERSION = props.getProperty("version");
		URL = props.getProperty("url");

		Date built;
		try {
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
			built = df.parse(props.getProperty("built"));
		} catch (ParseException e) {
			//this could happen during development if the properties file is not filtered by Maven
			logger.log(Level.SEVERE, "Could not parse built date.", e);
			built = new Date();
		}
		BUILT = built;
	}

	private static final File defaultProfileRootDir = new File(FileUtils.getUserDirectory(), ".emc-shopkeeper");
	public static final String defaultProfileName = "default";
	private static final Integer defaultStartPage = 1;
	private static final String defaultFormat = "TABLE";
	private static boolean macInitialized = false;

	private static MainFrame mainFrame;

	public static void main(String[] args) throws Throwable {
		//stop the random SecurityExceptions from being thrown
		JarSignersHardLinker.go();

		EmcShopArguments arguments = null;
		try {
			arguments = new EmcShopArguments(args);
		} catch (OptionException e) {
			out.println(e.getMessage());
			System.exit(1);
		}

		//print help
		if (arguments.help()) {
			String help = arguments.printHelp(defaultProfileName, defaultProfileRootDir.getAbsolutePath(), defaultStartPage, defaultFormat);
			out.println(help);
			return;
		}

		//print version
		if (arguments.version()) {
			out.println(VERSION);
			return;
		}

		//get profile root dir
		String profileRootDirStr = arguments.profileDir();
		File profileRootDir = (profileRootDirStr == null) ? defaultProfileRootDir : new File(profileRootDirStr);

		//get profile
		boolean profileSpecified = true;
		String profile = arguments.profile();
		if (profile == null) {
			profileSpecified = false;
			profile = defaultProfileName;
		}

		//create profile dir
		File profileDir = new File(profileRootDir, profile);
		if (profileDir.exists()) {
			if (!profileDir.isDirectory()) {
				out.println("Error: Profile directory is not a directory!  Path: " + profileDir.getAbsolutePath());
				System.exit(1);
			}
		} else {
			logger.info("Creating new profile: " + profileDir.getAbsolutePath());
			if (!profileDir.mkdirs()) {
				out.println("Error: Could not create profile folder!  Path: " + profileDir.getAbsolutePath());
				System.exit(1);
			}
		}

		//load settings
		Settings settings = new Settings(new File(profileDir, "settings.properties"));

		//show the "choose profile" dialog
		boolean cliMode = arguments.query() != null || arguments.update();
		if (!cliMode && !profileSpecified && settings.isShowProfilesOnStartup()) {
			initializeMac();

			IProfileSelectorView view = new ProfileSelectorViewImpl(null);
			IProfileSelectorModel model = new ProfileSelectorModelImpl(profileRootDir);
			ProfileSelectorPresenter presenter = new ProfileSelectorPresenter(view, model);

			String selectedProfile = presenter.getSelectedProfile();
			if (selectedProfile == null) {
				//user canceled the dialog, so quit the application
				return;
			}

			//reset the profile dir
			profileDir = new File(profileRootDir, selectedProfile);
			settings = new Settings(new File(profileDir, "settings.properties"));
		}

		//get database dir
		String dbDirStr = arguments.db();
		File dbDir = (dbDirStr == null) ? new File(profileDir, "db") : new File(dbDirStr);

		//get log level
		Level logLevel = null;
		try {
			logLevel = arguments.logLevel();
			if (logLevel == null) {
				logLevel = settings.getLogLevel();
			}
		} catch (IllegalArgumentException e) {
			out.println("Error: Invalid log level.");
			System.exit(1);
		}

		LogManager logManager = new LogManager(logLevel, new File(profileDir, "app.log"));

		if (!arguments.update() && arguments.query() == null) {
			launchGui(profileDir, dbDir, settings, logManager);
		} else {
			launchCli(dbDir, settings, arguments);
		}
	}

	private static void launchCli(File dbDir, Settings settings, EmcShopArguments args) throws Throwable {
		final DbDao dao = new DirbyEmbeddedDbDao(dbDir);

		int startingDbVersion = dao.selectDbVersion();
		Integer currentRupeeBalance = prepareForUpdateLogConversion(startingDbVersion, dao, settings);

		dao.updateToLatestVersion(null);

		finishUpdateLogConversion(currentRupeeBalance, startingDbVersion, dao, settings);

		CliController cli = new CliController(dao);

		if (args.update()) {
			//get stop at page
			Integer stopAtPage = args.stopPage();
			if (stopAtPage != null && stopAtPage < 1) {
				out.println("Error: \"stop-page\" must be greater than 0.");
				System.exit(1);
			}

			//get start at page
			Integer startAtPage = args.startPage();
			if (startAtPage == null) {
				startAtPage = defaultStartPage;
			} else if (startAtPage < 1) {
				out.println("Error: \"start-page\" must be greater than 0.");
				System.exit(1);
			}

			cli.update(startAtPage, stopAtPage);
		}

		String query = args.query();
		if (query != null) {
			String format = args.format();
			if (format == null) {
				format = defaultFormat;
			}
			cli.query(query, format);
		}
	}

	private static void launchGui(File profileDir, File dbDir, final Settings settings, LogManager logManager) throws Throwable {
		initializeMac();

		final AppContext context = AppContext.instance();
		context.add(settings);
		context.add(logManager);

		//add the report sender to the app context here incase there is an error during startup
		ReportSender reportSender = new ReportSender();
		context.add(reportSender);

		//set uncaught exception handler
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, final Throwable thrown) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						UnhandledErrorPresenter.show(null, "An error occurred.", thrown);
					}
				});
			}
		});

		//show splash screen
		final SplashFrame splash = new SplashFrame();
		splash.setVisible(true);

		//create the backup manager
		File dbBackupDir = new File(profileDir, "db-backups");
		BackupManager backupManager = new BackupManager(dbDir, dbBackupDir, settings.getBackupsEnabled(), settings.getBackupFrequency(), settings.getMaxBackups());
		context.add(backupManager);

		//delete old backups
		backupManager.cleanup();

		//backup the database if a backup is due
		boolean backedup = false;
		if (backupManager.shouldBackup()) {
			final long databaseSize = backupManager.getSizeOfDatabase();
			backupManager.backup(new ZipListener() {
				private long zipped = 0;

				@Override
				public void onZippedFile(File file) {
					zipped += file.length();
					int percent = (int) ((double) zipped / databaseSize * 100.0);
					splash.setMessage("Backing up database... (" + percent + "%)");
				}
			});

			backedup = true;
		}

		//initialize the cache
		File cacheDir = new File(profileDir, "cache");
		initCacheDir(cacheDir);

		//start the profile image loader
		ProfileLoader profileLoader = new ProfileLoader(cacheDir);
		profileLoader.setHttpClientFactory(new HttpClientFactory() {
			@Override
			public HttpClient create() {
				EmcSession session = context.get(EmcSession.class);
				return (session == null) ? new DefaultHttpClient() : session.createHttpClient();
			}
		});
		profileLoader.start();
		context.add(profileLoader);

		DbListener listener = new DbListener() {
			@Override
			public void onCreate() {
				splash.setMessage("Creating database...");
			}

			@Override
			public void onMigrate(int oldVersion, int newVersion) {
				splash.setMessage("Updating database...");
			}
		};

		DbDao dao = null;
		while (true) {
			try {
				//connect to database
				splash.setMessage("Starting database...");
				try {
					dao = new DirbyEmbeddedDbDao(dbDir, listener);
				} catch (SQLException e) {
					if ("XJ040".equals(e.getSQLState())) {
						splash.dispose();
						JOptionPane.showMessageDialog(null, "EMC Shopkeeper is already running.", "Already running", JOptionPane.ERROR_MESSAGE);
						return;
					}
					throw e;
				}

				//check to see an old version of the app is being run
				int startingDbVersion = dao.selectDbVersion();
				if (startingDbVersion > dao.getAppDbVersion()) {
					splash.dispose();
					JOptionPane.showMessageDialog(null, "The version of your EMC Shopkeeper database is newer than the EMC Shopkeeper application.  Please download the latest version of EMC Shopkeeper and run that.", "Outdated version", JOptionPane.ERROR_MESSAGE);
					return;
				}

				//initialize the report sender with the current database version
				reportSender.setDatabaseVersion(startingDbVersion);

				//backup the database if there is a database schema change
				if (!backedup && DirbyDbDao.schemaVersion > startingDbVersion) {
					dao.close();

					final long size = backupManager.getSizeOfDatabase();
					backupManager.backup(new ZipListener() {
						private long zipped = 0;

						@Override
						public void onZippedFile(File file) {
							zipped += file.length();
							int percent = (int) ((double) zipped / size * 100.0);
							splash.setMessage("Backing up database... (" + percent + "%)");
						}
					});

					backedup = true;

					//re-connect to the database
					splash.setMessage("Restarting database...");
					dao = new DirbyEmbeddedDbDao(dbDir, listener);
				}

				Integer currentRupeeBalance = prepareForUpdateLogConversion(startingDbVersion, dao, settings);

				//update database schema
				dao.updateToLatestVersion(listener);
				reportSender.setDatabaseVersion(dao.selectDbVersion());

				finishUpdateLogConversion(currentRupeeBalance, startingDbVersion, dao, settings);

				break;
			} catch (Throwable t) {
				IDatabaseStartupErrorView view = new DatabaseStartupErrorViewImpl(splash);
				IDatabaseStartupErrorModel model = new DatabaseStartupErrorModelImpl(t);
				DatabaseStartupErrorPresenter presenter = new DatabaseStartupErrorPresenter(view, model);
				if (presenter.getQuit()) {
					splash.dispose();
					return;
				}
			}
		}
		context.add(dao);

		OnlinePlayersMonitor onlinePlayersMonitor = new OnlinePlayersMonitor(new OnlinePlayersScraper(new DefaultHttpClient()), 1000 * 60 * 5);
		onlinePlayersMonitor.start();
		context.add(onlinePlayersMonitor);

		//tweak tooltip settings
		ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
		if (GuiUtils.linux) {
			//disable tooltips on Linux because random exceptions are thrown when tooltips are rendered
			//this may be due to an Open JDK bug: http://josm.openstreetmap.de/ticket/8921
			toolTipManager.setEnabled(false);
		} else {
			toolTipManager.setInitialDelay(0);
			toolTipManager.setDismissDelay(30000);
		}

		//pre-load the labels for the item suggest fields
		splash.setMessage("Loading item icons...");
		List<String> itemNames = dao.getItemNames();
		reportUnknownItems(itemNames, settings, reportSender);
		ItemSuggestField.init(itemNames);

		mainFrame = new MainFrame(profileDir.getName());
		mainFrame.setVisible(true);
		splash.dispose();
	}

	private static void reportUnknownItems(List<String> itemNames, Settings settings, ReportSender reportSender) {
		if (!settings.isReportUnknownItems()) {
			return;
		}

		List<String> alreadyReported = settings.getReportedUnknownItems();
		List<String> itemsToReport = new ArrayList<String>();
		ItemIndex itemIndex = ItemIndex.instance();
		for (String itemName : itemNames) {
			if (itemIndex.isUnknownItem(itemName) && !alreadyReported.contains(itemName)) {
				itemsToReport.add(itemName);
			}
		}
		if (itemsToReport.isEmpty()) {
			return;
		}

		reportSender.report("Unknown items: " + itemsToReport, null);

		alreadyReported.addAll(itemsToReport);
		settings.setReportedUnknownItems(alreadyReported);
		settings.save();
	}

	/**
	 * Runs Mac OS X customizations if user is on a Mac. This method must run
	 * before anything else graphics-related.
	 */
	private static void initializeMac() {
		if (macInitialized || !MacSupport.isMac()) {
			return;
		}

		MacSupport.init("EMC Shopkeeper", false, Images.APP_ICON.getImage(), new MacHandler() {
			@Override
			public void handleQuit(Object applicationEvent) {
				mainFrame.exit();
			}

			@Override
			public void handleAbout(Object applicationEvent) {
				AboutDialog.show(mainFrame);
			}
		});

		macInitialized = true;
	}

	/**
	 * Gets the player's current rupee balance so that it can be transfered to
	 * the "update_log" table. This must be called *before* the database is
	 * updated.
	 * @param startingDbVersion the current database version
	 * @param dao
	 * @param settings
	 * @return the player's current rupee balance
	 * @throws SQLException
	 */
	private static Integer prepareForUpdateLogConversion(int startingDbVersion, DbDao dao, Settings settings) throws SQLException {
		if (startingDbVersion > 17) {
			return null;
		}

		//get the player's current rupee balance
		Integer rupeeBalance = null;
		if (startingDbVersion == 17) {
			rupeeBalance = dao.selectRupeeBalanceMeta();
		} else {
			rupeeBalance = settings.getRupeeBalance();
			//do not save the settings file yet
		}
		return rupeeBalance;
	}

	/**
	 * Initializes the "update_log" table. This must be called *after* the
	 * database is updated.
	 * @param currentRupeeBalance the player's current rupee balance
	 * @param startingDbVersion the database version before the database was
	 * updated
	 * @param dao
	 * @param settings
	 * @throws SQLException
	 */
	private static void finishUpdateLogConversion(Integer currentRupeeBalance, int startingDbVersion, DbDao dao, Settings settings) throws SQLException {
		if (startingDbVersion > 17) {
			return;
		}

		Date previousUpdate = settings.getPreviousUpdate();
		if (previousUpdate != null) {
			dao.insertUpdateLog(previousUpdate, 0, 0, 0, 0, 0);
		}

		Date lastUpdated = settings.getLastUpdated();
		if (lastUpdated != null) {
			dao.insertUpdateLog(lastUpdated, currentRupeeBalance, 0, 0, 0, 0);
		}

		dao.commit();
		settings.save(); //remove unused properties
	}

	/**
	 * Estimates the time it will take to download a given number of transaction
	 * pages.
	 * @param stopPage the page to stop at
	 * @return the estimated processing time (in milliseconds)
	 */
	public static long estimateUpdateTime(Integer stopPage) {
		int totalMs = 10000;
		int last = 10000;
		for (int i = 100; i < stopPage; i += 100) {
			int cur = last + 1550;
			totalMs += cur;
			last = cur;
		}
		return totalMs;
	}

	/**
	 * Initializes the cache directory.
	 * @param cacheDir the path to the cache directory
	 * @throws IOException
	 */
	private static void initCacheDir(File cacheDir) throws IOException {
		File versionFile = new File(cacheDir, "_cache-version");
		boolean clearCache = false;
		if (!cacheDir.isDirectory()) {
			//create the cache dir if it doesn't exist
			clearCache = true;
		} else {
			//clear the cache dir on update
			String version = versionFile.exists() ? FileUtils.readFileToString(versionFile) : null;
			clearCache = (version == null || !version.equals(CACHE_VERSION));
		}

		//clear the cache if it's out of date
		if (clearCache) {
			logger.info("Clearing the cache.");
			if (!cacheDir.isDirectory() || FileUtils.deleteQuietly(cacheDir)) {
				if (!cacheDir.mkdir()) {
					throw new IOException("Could not create cache directory: " + cacheDir.getAbsolutePath());
				}
			}
			FileUtils.writeStringToFile(versionFile, CACHE_VERSION);
		}
	}
}