package emcshop;

import static emcshop.util.NumberFormatter.formatQuantity;
import static emcshop.util.NumberFormatter.formatRupees;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.SplashScreen;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.ToolTipManager;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.cli.Arguments;
import emcshop.db.DbDao;
import emcshop.db.DbListener;
import emcshop.db.DirbyEmbeddedDbDao;
import emcshop.db.ItemGroup;
import emcshop.gui.AboutDialog;
import emcshop.gui.ErrorDialog;
import emcshop.gui.MainFrame;
import emcshop.gui.ProfileImageLoader;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.MacHandler;
import emcshop.gui.lib.MacSupport;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.EmcSession;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.RupeeTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.scraper.TransactionPuller;
import emcshop.scraper.TransactionPullerFactory;
import emcshop.util.Settings;

public class Main {
	private static final Logger logger = Logger.getLogger(Main.class.getName());
	private static final String NEWLINE = System.getProperty("line.separator");

	private static final PrintStream out = System.out;
	private static Throwable pullerError = null;

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

	private static final Set<String> validArgs;
	static {
		Set<String> set = new HashSet<String>();
		set.add("profile");
		set.add("profile-dir");
		set.add("db");
		set.add("settings");
		set.add("log-level");

		set.add("update");
		set.add("stop-page");
		set.add("start-page");
		set.add("query");
		set.add("format");
		set.add("version");
		set.add("help");
		validArgs = Collections.unmodifiableSet(set);
	}

	private static final File defaultProfileRootDir = new File(FileUtils.getUserDirectory(), ".emc-shopkeeper");
	private static final String defaultProfile = "default";
	private static final int defaultStartPage = 1;
	private static final String defaultFormat = "TABLE";

	private static File profileRootDir, profileDir, dbDir;
	private static String profile, query, format;
	private static Settings settings;
	private static LogManager logManager;
	private static Integer stopAtPage;
	private static int startAtPage;
	private static boolean update;
	private static Level logLevel;

	private static MainFrame frame;

	private static Date earliestParsedTransactionDate;

	public static void main(String[] args) throws Throwable {
		Arguments arguments = new Arguments(args);

		//print help
		if (arguments.exists(null, "help")) {
			printHelp();
			return;
		}

		//check for invalid arguments
		Collection<String> invalidArgs = arguments.invalidArgs(validArgs);
		if (!invalidArgs.isEmpty()) {
			printHelp();
			out.println();

			out.println("Error: The following arguments are invalid:");
			for (String invalidArg : invalidArgs) {
				out.println("  " + invalidArg);
			}
			System.exit(1);
		}

		//print version
		if (arguments.exists(null, "version")) {
			out.println(VERSION);
			return;
		}

		//get profile root dir
		String profileRootDirStr = arguments.value(null, "profile-dir");
		profileRootDir = (profileRootDirStr == null) ? defaultProfileRootDir : new File(profileRootDirStr);

		//get profile
		boolean profileSpecified = true;
		profile = arguments.value(null, "profile");
		if (profile == null) {
			profileSpecified = false;
			profile = defaultProfile;
		}

		//create profile dir
		profileDir = new File(profileRootDir, profile);
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
		String settingsFileStr = arguments.value(null, "settings");
		File settingsFile = (settingsFileStr == null) ? new File(profileDir, "settings.properties") : new File(settingsFileStr);
		settings = new Settings(settingsFile);

		//show the "choose profile" dialog
		boolean cliMode = arguments.exists(null, "query") || arguments.exists(null, "update");
		if (!cliMode && !profileSpecified && settings.isShowProfilesOnStartup()) {
			ProfileDialog dialog = new ProfileDialog();
			dialog.setVisible(true);
			if (dialog.selectedProfileDir == null) {
				//user canceled the dialog
				//quit the application
				return;
			}

			//reset the profile dir
			profileDir = dialog.selectedProfileDir;
			profile = profileDir.getName();
			settings = new Settings(new File(profileDir, "settings.properties"));
		}

		//get database dir
		String dbDirStr = arguments.value(null, "db");
		dbDir = (dbDirStr == null) ? new File(profileDir, "db") : new File(dbDirStr);

		//get update flag
		update = arguments.exists(null, "update");
		if (update) {
			//get stop at page
			stopAtPage = arguments.valueInt(null, "stop-page");
			if (stopAtPage != null && stopAtPage < 1) {
				out.println("Error: \"stop-page\" must be greater than 0.");
				System.exit(1);
			}

			//get start at page
			startAtPage = arguments.valueInt(null, "start-page", defaultStartPage);
			if (startAtPage < 1) {
				out.println("Error: \"start-page\" must be greater than 0.");
				System.exit(1);
			}
		}

		//get query
		if (arguments.exists(null, "query")) {
			query = arguments.value(null, "query");
			if (query == null) {
				query = "";
			}
			format = arguments.value(null, "format");
			if (format == null) {
				format = defaultFormat;
			}
		} else {
			query = null;
		}

		//get log level
		String logLevelStr = arguments.value(null, "log-level");
		if (logLevelStr == null) {
			logLevel = settings.getLogLevel();
		} else {
			try {
				logLevel = Level.parse(logLevelStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				out.println("Error: Invalid log level \"" + logLevelStr + "\".");
				System.exit(1);
			}
		}

		logManager = new LogManager(logLevel, new File(profileDir, "app.log"));

		if (!update && query == null) {
			launchGui();
		} else {
			launchCli();
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
		"  The profile to use (defaults to \"" + defaultProfile + "\")." + NEWLINE +
		NEWLINE +
		"--profile-dir=DIR" + NEWLINE +
		"  The path to the directory that contains all the profiles" + NEWLINE +
		"  (defaults to \"" + defaultProfileRootDir.getAbsolutePath() + "\")." + NEWLINE +
		NEWLINE +
		"--db=PATH" + NEWLINE +
		"  Overrides the database location (stored in the profile by default)." + NEWLINE +
		NEWLINE +
		"--settings=PATH" + NEWLINE +
		"  Overrides the settings file location (stored in the profile by default)." + NEWLINE +
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

	private static void launchCli() throws Throwable {
		final DbDao dao = new DirbyEmbeddedDbDao(dbDir);
		ReportSender.instance().setDatabaseVersion(dao.selectDbVersion());
		dao.updateToLatestVersion(null);
		ReportSender.instance().setDatabaseVersion(dao.selectDbVersion());

		if (update) {
			Date latestTransactionDateFromDb = dao.getLatestTransactionDate();
			boolean firstUpdate = (latestTransactionDateFromDb == null);

			if (firstUpdate) {
				//@formatter:off
				out.println(
				"================================================================================" + NEWLINE +
				"NOTE: This is the first time you are running an update.  To ensure accurate" + NEWLINE +
				"results, it is recommended that you set MOVE PERMS to FALSE on your res for this" + NEWLINE +
				"first update." + NEWLINE +
				"                                /res set move false" + NEWLINE);
				//@formatter:on

				if (stopAtPage == null) {
					//@formatter:off
					out.println(
					"Your entire transaction history will be parsed." + NEWLINE +
					"This could take up to 60 MINUTES depending on its size.");
					//@formatter:on
				} else {
					long time = estimateUpdateTime(stopAtPage);
					int pages = stopAtPage - startAtPage + 1;

					//@formatter:off
					out.println(
					pages + " pages will be parsed." + NEWLINE +
					"Estimated time: " + DurationFormatUtils.formatDuration(time, "HH:mm:ss", true));
					//@formatter:on
				}

				out.println("--------------------------------------------------------------------------------");
				String ready = System.console().readLine("Are you ready to start? (y/n) ");
				if (!"y".equalsIgnoreCase(ready)) {
					out.println("Goodbye.");
					return;
				}
			}

			TransactionPullerFactory pullerFactory = new TransactionPullerFactory();
			if (firstUpdate) {
				pullerFactory.setMaxPaymentTransactionAge(7);
				if (stopAtPage != null) {
					pullerFactory.setStopAtPage(stopAtPage);
				}
				pullerFactory.setStartAtPage(startAtPage);
			} else {
				pullerFactory.setStopAtDate(latestTransactionDateFromDb);
			}

			EmcSession session = settings.getSession();
			String sessionUsername = (session == null) ? null : session.getUsername();
			boolean repeat;
			do {
				repeat = false;
				if (session == null) {
					out.println("Please enter your EMC login credentials.");
					do {
						//note: "System.console()" doesn't work from Eclipse
						String username;
						if (sessionUsername == null) {
							username = System.console().readLine("Username: ");
						} else {
							username = System.console().readLine("Username [" + sessionUsername + "]: ");
							if (username.isEmpty()) {
								username = sessionUsername;
							}
						}
						String password = new String(System.console().readPassword("Password: "));
						out.println("Logging in...");
						session = EmcSession.login(username, password, settings.isPersistSession());
						if (session == null) {
							out.println("Login failed.  Please try again.");
						}
					} while (session == null);
					settings.setSession(session);
					settings.save();
				}

				final TransactionPuller puller = pullerFactory.newInstance();
				Date started = new Date();
				TransactionPuller.Result result = puller.start(session, new TransactionPuller.Listener() {
					private int transactionCount = 0;
					private int pageCount = 0;
					private final NumberFormat nf = NumberFormat.getInstance();

					@Override
					public synchronized void onPageScraped(int page, List<RupeeTransaction> transactions) {
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
							out.print("\rPages: " + nf.format(pageCount) + " | Transactions: " + nf.format(transactionCount));
						} catch (SQLException e) {
							pullerError = e;
							puller.cancel();
						}
					}
				});

				switch (result.getState()) {
				case CANCELLED:
					dao.rollback();
					if (pullerError != null) {
						throw pullerError;
					}
					break;
				case BAD_SESSION:
					out.println("Your login session has expired.");
					session = null;
					repeat = true;
					break;
				case FAILED:
					dao.rollback();
					throw result.getThrown();
				case COMPLETED:
					if (earliestParsedTransactionDate != null) {
						dao.updateBonusesFeesSince(earliestParsedTransactionDate);
					}
					dao.commit();

					settings.setPreviousUpdate(settings.getLastUpdated());
					settings.setLastUpdated(started);
					settings.setRupeeBalance(result.getRupeeBalance());
					settings.save();

					out.println("\n" + result.getPageCount() + " pages processed and " + result.getTransactionCount() + " transactions saved in " + (result.getTimeTaken() / 1000) + " seconds.");
					logger.info(result.getPageCount() + " pages processed and " + result.getTransactionCount() + " transactions saved in " + (result.getTimeTaken() / 1000) + " seconds.");
					break;
				}
			} while (repeat);
		}

		if (query != null) {
			Map<String, ItemGroup> itemGroups;
			Date from, to;
			if (query.isEmpty()) {
				itemGroups = dao.getItemGroups();
				from = to = null;
			} else {
				Date range[] = parseDateRange(query);
				from = range[0];
				to = range[1];
				itemGroups = dao.getItemGroups(from, to);
			}

			//sort items
			List<ItemGroup> sortedItemGroups = new ArrayList<ItemGroup>(itemGroups.values());
			Collections.sort(sortedItemGroups, new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup left, ItemGroup right) {
					return left.getItem().compareTo(right.getItem());
				}
			});

			if ("CSV".equalsIgnoreCase(format)) {
				//calculate net total
				int netTotal = 0;
				for (Map.Entry<String, ItemGroup> entry : itemGroups.entrySet()) {
					ItemGroup itemGroup = entry.getValue();
					netTotal += itemGroup.getNetAmount();
				}

				//generate CSV
				out.println(QueryExporter.generateItemsCsv(sortedItemGroups, netTotal, from, to));
			} else if ("BBCODE".equalsIgnoreCase(format)) {
				//calculate net total
				int netTotal = 0;
				for (Map.Entry<String, ItemGroup> entry : itemGroups.entrySet()) {
					ItemGroup itemGroup = entry.getValue();
					netTotal += itemGroup.getNetAmount();
				}

				//generate BBCode
				out.println(QueryExporter.generateItemsBBCode(sortedItemGroups, netTotal, from, to));
			} else {
				out.println("Item                |Sold                |Bought              |Net");
				out.println("--------------------------------------------------------------------------------");
				int totalAmount = 0;
				for (ItemGroup itemGroup : sortedItemGroups) {
					//TODO ANSI colors
					//TODO String.format("%-20s | %-20s | %-20s | %-20s\n", group.getItem(), sold, bought, net);

					out.print(itemGroup.getItem());
					int spaces = 20 - itemGroup.getItem().length();
					if (spaces > 0) {
						out.print(StringUtils.repeat(' ', spaces));
					}
					out.print('|');

					String s;

					if (itemGroup.getSoldQuantity() == 0) {
						s = " - ";
					} else {
						s = formatQuantity(itemGroup.getSoldQuantity(), false) + " / " + formatRupees(itemGroup.getSoldAmount(), false);
					}
					out.print(s);
					spaces = 20 - s.length();
					if (spaces > 0) {
						out.print(StringUtils.repeat(' ', spaces));
					}
					out.print('|');

					if (itemGroup.getBoughtQuantity() == 0) {
						s = " - ";
					} else {
						s = formatQuantity(itemGroup.getBoughtQuantity(), false) + " / " + formatRupees(itemGroup.getBoughtAmount(), false);
					}
					out.print(s);
					spaces = 20 - s.length();
					if (spaces > 0) {
						out.print(StringUtils.repeat(' ', spaces));
					}
					out.print('|');

					out.print(formatQuantity(itemGroup.getNetQuantity(), false));
					out.print(" / ");
					out.print(formatRupees(itemGroup.getNetAmount(), false));

					out.println();

					totalAmount += itemGroup.getNetAmount();
				}

				out.print(StringUtils.repeat(' ', 62));
				out.print('|');
				out.println(formatRupees(totalAmount));
			}
		}
	}

	protected static Date[] parseDateRange(String dateRangeStr) throws ParseException {
		dateRangeStr = dateRangeStr.trim().toLowerCase();

		Date from, to;
		if ("today".equals(dateRangeStr)) {
			Calendar c = Calendar.getInstance();
			c.set(Calendar.MILLISECOND, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.HOUR_OF_DAY, 0);
			from = c.getTime();

			to = new Date();
		} else {
			String split[] = dateRangeStr.split("\\s+to\\s+");
			from = parseDate(split[0], false);

			if (split.length == 1 || "today".equals(split[1])) {
				to = new Date();
			} else {
				to = parseDate(split[1], true);
			}
		}

		return new Date[] { from, to };
	}

	private static Date parseDate(String s, boolean to) throws ParseException {
		int colonCount = StringUtils.countMatches(s, ":");
		if (colonCount == 0) {
			Date date = new SimpleDateFormat("yyyy-MM-dd").parse(s);
			if (to) {
				Calendar c = Calendar.getInstance();
				c.setTime(date);
				c.add(Calendar.DATE, 1);
				date = c.getTime();
			}
			return date;
		} else if (colonCount == 1) {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(s);
		} else {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
		}
	}

	private static void launchGui() throws SQLException, IOException {
		final SplashScreenWrapper splash = new SplashScreenWrapper();
		splash.setMessage("Starting database...");

		DbListener listener = new DbListener() {
			@Override
			public void onCreate() {
				splash.setMessage("Creating database...");
			}

			@Override
			public void onBackup(int oldVersion, int newVersion) {
				splash.setMessage("Preparing for database migration...");
			}

			@Override
			public void onMigrate(int oldVersion, int newVersion) {
				splash.setMessage("Migrating database...");
			}
		};

		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable thrown) {
				ErrorDialog.show(null, "An error occurred.", thrown);
			}
		});

		//init the cache
		File cacheDir = new File(profileDir, "cache");
		initCacheDir(cacheDir);

		//start the profile image loader
		ProfileImageLoader profileImageLoader = new ProfileImageLoader(cacheDir, settings);

		//connect to database
		DbDao dao;
		try {
			dao = new DirbyEmbeddedDbDao(dbDir, listener);
		} catch (SQLException e) {
			if ("XJ040".equals(e.getSQLState())) {
				JOptionPane.showMessageDialog(null, "EMC Shopkeeper is already running.", "Already running", JOptionPane.ERROR_MESSAGE);
				return;
			}
			throw e;
		}

		//update database schema
		ReportSender.instance().setDatabaseVersion(dao.selectDbVersion());
		dao.updateToLatestVersion(listener);
		ReportSender.instance().setDatabaseVersion(dao.selectDbVersion());

		splash.close();

		//run Mac OS X customizations if user is on a Mac
		//this code must run before *anything* else graphics-related
		Image appIcon = ImageManager.getAppIcon().getImage();
		MacSupport.initIfMac("EMC Shopkeeper", false, appIcon, new MacHandler() {
			@Override
			public void handleQuit(Object applicationEvent) {
				frame.windowClosed(null);
			}

			@Override
			public void handleAbout(Object applicationEvent) {
				AboutDialog.show(frame);
			}
		});

		//tweak tooltip settings
		ToolTipManager.sharedInstance().setInitialDelay(0);
		ToolTipManager.sharedInstance().setDismissDelay(30000);

		frame = new MainFrame(settings, dao, logManager, profileImageLoader, profileDir.getName());
		frame.setVisible(true);
	}

	/**
	 * Estimates the time it will take to process all the transactions.
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

	private static class SplashScreenWrapper {
		private final SplashScreen splash;
		private final Graphics2D gfx;

		public SplashScreenWrapper() {
			splash = SplashScreen.getSplashScreen();
			if (splash == null) {
				logger.warning("Splash screen not configured to display.");
				gfx = null;
			} else {
				gfx = splash.createGraphics();
				if (gfx == null) {
					logger.warning("Could not get Graphics2D object from splash screen.");
				}
			}
		}

		public void setMessage(String message) {
			if (gfx == null) {
				return;
			}

			gfx.setComposite(AlphaComposite.Clear);
			gfx.fillRect(40, 60, 200, 40);
			gfx.setPaintMode();
			gfx.setColor(Color.BLACK);
			gfx.drawString(message, 40, 80);
			splash.update();
		}

		public void close() {
			if (splash == null) {
				return;
			}

			splash.close();
		}
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

	@SuppressWarnings("serial")
	private static class ProfileDialog extends JDialog {
		private final JComboBox profiles;
		private final JButton ok, quit;
		private File selectedProfileDir = null;

		public ProfileDialog() {
			setTitle("Choose Profile");
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			setResizable(false);
			setModal(true);

			//get list of existing profiles
			Vector<String> profileNames = new Vector<String>();
			File files[] = profileRootDir.listFiles();
			for (File file : files) {
				if (!file.isDirectory()) {
					continue;
				}

				profileNames.add(file.getName());
			}

			profiles = new JComboBox(profileNames);
			profiles.setEditable(true);
			if (profileNames.contains(defaultProfile)) {
				profiles.setSelectedItem(defaultProfile);
			}
			profiles.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ok.doClick();
				}
			});

			ok = new JButton("OK");
			ok.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					String profile = (String) profiles.getSelectedItem();
					if (profile.isEmpty()) {
						JOptionPane.showMessageDialog(ProfileDialog.this, "Profile name cannot be blank.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}

					File profileDir = new File(profileRootDir, profile);
					if (!profileDir.exists() && !profileDir.mkdir()) {
						//if it couldn't create the directory
						JOptionPane.showMessageDialog(ProfileDialog.this, "Invalid profile name.  Please choose another.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}

					selectedProfileDir = profileDir;
					dispose();
				}
			});

			quit = new JButton("Quit");
			quit.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					dispose();
				}
			});

			///////////////////////

			setLayout(new MigLayout());

			add(new JLabel(ImageManager.getImageIcon("header.png")), "align center, wrap");
			add(new JLabel("<html><div width=250><center>Select a profile:</center></div>"), "align center, wrap");
			add(profiles, "w 200, align center, wrap");
			add(ok, "split 2, align center");
			add(quit);

			pack();
			setLocationRelativeTo(null); //center on screen
		}
	}
}