package emcshop.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import emcshop.ShopTransaction;
import emcshop.TransactionPuller;
import emcshop.db.DbDao;
import emcshop.db.DirbyEmbeddedDbDao;
import emcshop.util.Settings;

public class Main {
	private static final Logger logger = Logger.getLogger(Main.class.getName());

	private static final PrintStream out = System.out;
	private static Throwable pullerError = null;

	private static final Set<String> validArgs = new HashSet<String>();
	static {
		validArgs.add("help");
		validArgs.add("db");
		validArgs.add("stop-at-page");
		validArgs.add("start-at-page");
		validArgs.add("threads");
		validArgs.add("settings");
		validArgs.add("latest");
		//TODO add "--version" argument
		//TODO '--profile=NAME' argument
		//TODO add '--update' argument
		//TODO add '--query="2013-03-01 00:00:00 to today"' argument
	}

	public static void main(String[] args) throws Throwable {
		//create the config folder in the user's home directory
		File config = new File(System.getProperty("user.home"), ".emc-shopkeeper");
		if (!config.exists() && !config.mkdir()) {
			throw new IOException("Could not create config folder: " + config.getAbsolutePath());
		}

		File defaultDbFolder = new File(config, "db");
		File defaultSettingsFile = new File(config, "settings.properties");
		int defaultThreads = 4;
		int defaultStartPage = 1;

		Arguments arguments = new Arguments(args);

		if (arguments.exists(null, "help")) {
			//@formatter:off
			out.print(
			"EMC Shopkeeper\n" +
			"by shavingfoam\n" +
			"\n" +
			"--latest\n" +
			"  Prints out the latest transaction from the database.\n" +
			"--db=PATH\n" +
			"  Specifies the location of the database.\n" +
			"  Defaults to " + defaultDbFolder.getAbsolutePath() + "\n" +
			"--settings=PATH\n" +
			"  Specifies the location of the application settings file, which contains the\n" +
			"  cookie values.\n" +
			"  Defaults to " + defaultSettingsFile.getAbsolutePath() + "\n" +
			"--threads=NUM\n" +
			"  Specifies the number of pages to parse at once.\n" +
			"  Defaults to " + defaultThreads + "\n" +
			"--start-at-page=PAGE\n" +
			"  Specifies the page number to start at.\n" +
			"  Defaults to " + defaultStartPage + "\n" +
			"--stop-at-page=PAGE\n" +
			"  Specifies the page number to stop parsing at.\n" +
			"  Defaults to the last transaction page.\n" +
			"--help\n" +
			"  Prints this help message.\n"
			);
			//@formatter:on
			return;
		}

		Collection<String> invalidArgs = arguments.invalidArgs(validArgs);
		if (!invalidArgs.isEmpty()) {
			out.println("The following arguments are invalid:");
			for (String invalidArg : invalidArgs) {
				out.println("  " + invalidArg);
			}
			System.exit(1);
		}

		String settingsPath = arguments.value(null, "settings");
		File settingsFile = (settingsPath == null) ? defaultSettingsFile : new File(settingsPath);
		if (!settingsFile.exists()) {
			out.println("Error: No cookies set.  Log into empireminecraft.com, and then copy all cookies to the following properties file: " + settingsFile.getAbsolutePath());
			System.exit(1);
		}
		Settings settings = new Settings(settingsFile);

		String dbPath = arguments.value(null, "db");
		File dbFolder = (dbPath == null) ? defaultDbFolder : new File(dbPath);
		Integer stopAtPage = arguments.valueInt(null, "stop-at-page");
		if (stopAtPage != null && stopAtPage < 1) {
			out.println("Error: \"stop-at-page\" must be greater than 0.");
		}
		int startAtPage = arguments.valueInt(null, "start-at-page", defaultStartPage);
		if (startAtPage < 1) {
			out.println("Error: \"start-at-page\" must be greater than 0.");
		}
		int threadCount = arguments.valueInt(null, "threads", defaultThreads);
		boolean latest = arguments.exists(null, "latest");

		final DbDao dao = initDao(dbFolder);
		ShopTransaction latestTransactionFromDb = dao.getLatestTransaction();

		if (latest) {
			if (latestTransactionFromDb == null) {
				out.println("No transactions in database.");
			} else {
				NumberFormat nf = NumberFormat.getNumberInstance();
				int quantity = latestTransactionFromDb.getQuantity();
				if (quantity < 0) {
					out.println("Sold " + -quantity + " " + latestTransactionFromDb.getItem() + " to " + latestTransactionFromDb.getPlayer() + " for " + nf.format(latestTransactionFromDb.getAmount()) + "r.");
				} else {
					out.println("Bought " + quantity + " " + latestTransactionFromDb.getItem() + " from " + latestTransactionFromDb.getPlayer() + " for " + nf.format(-latestTransactionFromDb.getAmount()) + "r.");
				}
				out.println("Current balance: " + nf.format(latestTransactionFromDb.getBalance()) + "r");
			}
			return;
		}

		final TransactionPuller puller = new TransactionPuller(settings.getCookies());
		puller.setThreadCount(threadCount);
		if (latestTransactionFromDb != null) {
			puller.setStopAtDate(latestTransactionFromDb.getTs());
		}
		if (stopAtPage != null) {
			puller.setStopAtPage(stopAtPage);
		}
		puller.setStartAtPage(startAtPage);

		TransactionPuller.Result result = puller.start(new TransactionPuller.Listener() {
			@Override
			public void onPageScraped(int page, List<ShopTransaction> transactions) {
				try {
					for (ShopTransaction transaction : transactions) {
						dao.insertTransaction(transaction);
					}
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
		case FAILED:
			dao.rollback();
			throw result.getThrown();
		case COMPLETED:
			dao.commit();
			logger.info(result.getPageCount() + " pages processed and " + result.getTransactionCount() + " transactions saved in " + (result.getTimeTaken() / 1000) + " seconds.");
			break;
		}
	}

	private static DbDao initDao(File folder) throws SQLException {
		return new DirbyEmbeddedDbDao(new File(folder, "data"));
	}
}
