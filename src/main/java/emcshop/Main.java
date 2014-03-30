package emcshop;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.ToolTipManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import emcshop.cli.CliController;
import emcshop.cli.EmcShopArguments;
import emcshop.db.DbDao;
import emcshop.db.DbListener;
import emcshop.db.DirbyDbDao;
import emcshop.db.DirbyEmbeddedDbDao;
import emcshop.gui.AboutDialog;
import emcshop.gui.ItemSuggestField;
import emcshop.gui.MainFrame;
import emcshop.gui.ProfileImageLoader;
import emcshop.gui.SplashFrame;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.JarSignersHardLinker;
import emcshop.gui.lib.MacHandler;
import emcshop.gui.lib.MacSupport;
import emcshop.model.IProfileSelectorModel;
import emcshop.model.ProfileSelectorModelImpl;
import emcshop.presenter.ProfileSelectorPresenter;
import emcshop.presenter.UnhandledErrorPresenter;
import emcshop.util.GuiUtils;
import emcshop.util.Settings;
import emcshop.util.ZipUtils.ZipListener;
import emcshop.view.IProfileSelectorView;
import emcshop.view.ProfileSelectorViewImpl;

public class Main {
	private static final Logger logger = Logger.getLogger(Main.class.getName());
	private static final String NEWLINE = System.getProperty("line.separator");
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
		InputStream in = Main.class.getResourceAsStream("/info.properties");
		Properties props = new Properties();
		try {
			props.load(in);
		} catch (IOException e) {
			throw new RuntimeException(e);
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
		} catch (IllegalArgumentException e) {
			printHelp();
			out.println();
			out.println("Error: The following arguments are invalid: " + e.getMessage());
			System.exit(1);
		}

		//print help
		if (arguments.help()) {
			printHelp();
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
			profile = profileDir.getName();
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

	private static void printHelp() {
		//@formatter:off
		out.println(
		"EMC Shopkeeper v" + VERSION + NEWLINE +
		"by Michael Angstadt (shavingfoam)" + NEWLINE +
		URL + NEWLINE +
		NEWLINE +
		"General arguments" + NEWLINE +
		"These arguments can be used for the GUI and CLI." + NEWLINE +
		"================================================" + NEWLINE +
		"--profile=PROFILE" + NEWLINE +
		"  The profile to use (defaults to \"" + defaultProfileName + "\")." + NEWLINE +
		NEWLINE +
		"--profile-dir=DIR" + NEWLINE +
		"  The path to the directory that contains all the profiles" + NEWLINE +
		"  (defaults to \"" + defaultProfileRootDir.getAbsolutePath() + "\")." + NEWLINE +
		NEWLINE +
		"--db=PATH" + NEWLINE +
		"  Overrides the database location (stored in the profile by default)." + NEWLINE +
		NEWLINE +
		"--log-level=FINEST|FINER|FINE|CONFIG|INFO|WARNING|SEVERE" + NEWLINE +
		"  The log level to use (defaults to INFO)." + NEWLINE +
		NEWLINE +
		"CLI arguments" + NEWLINE +
		"Using one of these arguments will launch EMC Shopkeeper in CLI mode." + NEWLINE +
		"================================================" + NEWLINE +
		"--update" + NEWLINE +
		"  Updates the database with the latest transactions." + NEWLINE +
		"--start-page=PAGE" + NEWLINE +
		"  Specifies the transaction history page number to start at during" + NEWLINE +
		"  the first update (defaults to " + defaultStartPage + ")." + NEWLINE +
		"--stop-page=PAGE" + NEWLINE +
		"  Specifies the transaction history page number to stop at during" + NEWLINE +
		"  the first update (defaults to the last page)." + NEWLINE +
		NEWLINE +
		"--query=QUERY" + NEWLINE +
		"  Shows the net gains/losses of each item.  Examples:" + NEWLINE +
		"  All data:           --query" + NEWLINE +
		"  Today's data:       --query=\"today\"" + NEWLINE +
		"  Three days of data: --query=\"2013-03-07 to 2013-03-09\"" + NEWLINE +
		"  Data up to today:   --query=\"2013-03-07 to today\"" + NEWLINE +
		"--format=TABLE|CSV|BBCODE" + NEWLINE +
		"  Specifies how to render the queried transaction data (defaults to " + defaultFormat + ")." + NEWLINE +
		NEWLINE +
		"--version" + NEWLINE +
		"  Prints the version of this program." + NEWLINE +
		NEWLINE +
		"--help" + NEWLINE +
		"  Prints this help message."
		);
		//@formatter:on
	}

	private static void launchCli(File dbDir, Settings settings, EmcShopArguments args) throws Throwable {
		final DbDao dao = new DirbyEmbeddedDbDao(dbDir);

		int startingDbVersion = dao.selectDbVersion();
		Integer currentRupeeBalance = prepareForUpdateLogConversion(startingDbVersion, dao, settings);

		ReportSender reportSender = ReportSender.instance();
		reportSender.setDatabaseVersion(startingDbVersion);
		dao.updateToLatestVersion(null);
		reportSender.setDatabaseVersion(dao.selectDbVersion());

		finishUpdateLogConversion(currentRupeeBalance, startingDbVersion, dao, settings);

		CliController cli = new CliController(dao, settings);

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

	private static void launchGui(File profileDir, File dbDir, Settings settings, LogManager logManager) throws SQLException, IOException {
		initializeMac();

		//show splash screen
		final SplashFrame splash = new SplashFrame();
		splash.setVisible(true);

		//create the backup manager
		File dbBackupDir = new File(profileDir, "db-backups");
		BackupManager backupManager = new BackupManager(dbDir, dbBackupDir, settings.getBackupsEnabled(), settings.getBackupFrequency(), settings.getMaxBackups());

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

		//set uncaught exception handler
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable thrown) {
				UnhandledErrorPresenter.show(null, "An error occurred.", thrown);
			}
		});

		//init the cache
		File cacheDir = new File(profileDir, "cache");
		initCacheDir(cacheDir);

		//start the profile image loader
		ProfileImageLoader profileImageLoader = new ProfileImageLoader(cacheDir, settings);

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

		//connect to database
		splash.setMessage("Starting database...");
		DbDao dao;
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

		int startingDbVersion = dao.selectDbVersion();

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

			//re-connect to database
			splash.setMessage("Restarting database...");
			dao = new DirbyEmbeddedDbDao(dbDir, listener);
		}

		Integer currentRupeeBalance = prepareForUpdateLogConversion(startingDbVersion, dao, settings);

		//update database schema
		ReportSender reportSender = ReportSender.instance();
		reportSender.setDatabaseVersion(startingDbVersion);
		dao.updateToLatestVersion(listener);
		reportSender.setDatabaseVersion(dao.selectDbVersion());

		finishUpdateLogConversion(currentRupeeBalance, startingDbVersion, dao, settings);

		//tweak tooltip settings
		ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
		if (GuiUtils.linux) {
			//disable tooltips on linux because of an Open JDK bug
			//see: http://josm.openstreetmap.de/ticket/8921
			toolTipManager.setEnabled(false);
		} else {
			toolTipManager.setInitialDelay(0);
			toolTipManager.setDismissDelay(30000);
		}

		//pre-load the labels for the item suggest fields
		splash.setMessage("Loading item icons...");
		ItemSuggestField.init(dao);

		mainFrame = new MainFrame(settings, dao, logManager, profileImageLoader, profileDir.getName(), backupManager);
		mainFrame.setVisible(true);
		splash.dispose();
	}

	/**
	 * Runs Mac OS X customizations if user is on a Mac. This method must run
	 * before anything else graphics-related.
	 */
	private static void initializeMac() {
		if (macInitialized || !MacSupport.isMac()) {
			return;
		}

		//run Mac OS X customizations if user is on a Mac
		//this code must run before *anything* else graphics-related
		Image appIcon = ImageManager.getAppIcon().getImage();
		MacSupport.init("EMC Shopkeeper", false, appIcon, new MacHandler() {
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
	 * Inits the "update_log" table. This must be called *after* the database is
	 * updated.
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