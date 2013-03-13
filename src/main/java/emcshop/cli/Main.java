package emcshop.cli;

import java.io.File;
import java.io.PrintStream;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import emcshop.EmcSession;
import emcshop.ShopTransaction;
import emcshop.TransactionPuller;
import emcshop.db.DbDao;
import emcshop.db.DirbyEmbeddedDbDao;
import emcshop.db.ItemGroup;
import emcshop.util.Settings;

public class Main {
	private static final Logger logger = Logger.getLogger(Main.class.getName());

	private static final PrintStream out = System.out;
	private static Throwable pullerError = null;

	private static final Set<String> validArgs;
	static {
		Set<String> set = new HashSet<String>();
		set.add("help");
		set.add("db");
		set.add("stop-at-page");
		set.add("start-at-page");
		set.add("threads");
		set.add("settings");
		set.add("latest");
		set.add("update");
		set.add("query");
		set.add("profile");
		set.add("profile-dir");
		//TODO add "--version" argument
		validArgs = Collections.unmodifiableSet(set);
	}

	public static void main(String[] args) throws Throwable {
		LogManager.getLogManager().reset();
		File defaultProfileRootDir = new File(System.getProperty("user.home"), ".emc-shopkeeper");
		String defaultProfile = "default";
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
			"--update\n" +
			"  Updates the database with the latest transactions.\n" +
			"--query=QUERY\n" +
			"  Shows the net gains/losses of each item.  Examples:\n" +
			"  All data:           --query\n" +
			"  Today's data:       --query=\"today\"\n" +
			"  Three days of data: --query=\"2013-03-07 to 2013-03-09\"\n" +
			"  Data up to today:   --query=\"2013-03-07 to today\"\n" +
			"--profile=PROFILE\n" +
			"  The profile to use (defaults to \"" + defaultProfile + "\").\n" +
			"--profile-dir=DIR\n" +
			"  The path to the directory that contains all the profiles\n" +
			"  (defaults to \"" + defaultProfileRootDir.getAbsolutePath() + "\").\n" +
			"--db=PATH\n" +
			"  Overrides the database location (stored in the profile by default).\n" +
			"--settings=PATH\n" +
			"  Overrides the settings file location (stored in the profile by default).\n" +
			"--threads=NUM\n" +
			"  Specifies the number of transaction history pages that will be parsed at\n" +
			"  once during an update (defaults to " + defaultThreads + ").\n" +
			"--start-at-page=PAGE\n" +
			"  Specifies the transaction history page number to start at during an update\n" +
			"  (defaults to " + defaultStartPage + ").\n" +
			"--stop-at-page=PAGE\n" +
			"  Specifies the transaction history page number to stop at during an update\n" +
			"  (defaults to the last page).\n" +
			"--help\n" +
			"  Prints this help message.\n"
			);
			//@formatter:on
			return;
		}

		Collection<String> invalidArgs = arguments.invalidArgs(validArgs);
		if (!invalidArgs.isEmpty()) {
			out.println("Error: The following arguments are invalid:");
			for (String invalidArg : invalidArgs) {
				out.println("  " + invalidArg);
			}
			System.exit(1);
		}

		String profileRootDirStr = arguments.value(null, "profile-dir");
		File profileRootDir = (profileRootDirStr == null) ? defaultProfileRootDir : new File(profileRootDirStr);

		String profile = arguments.value(null, "profile");
		if (profile == null) {
			profile = defaultProfile;
		}
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

		String settingsFileStr = arguments.value(null, "settings");
		File settingsFile = (settingsFileStr == null) ? new File(profileDir, "settings.properties") : new File(settingsFileStr);
		Settings settings = new Settings(settingsFile);

		String dbDirStr = arguments.value(null, "db");
		File dbDir = (dbDirStr == null) ? new File(profileDir, "db") : new File(dbDirStr);

		Integer stopAtPage = arguments.valueInt(null, "stop-at-page");
		if (stopAtPage != null && stopAtPage < 1) {
			out.println("Error: \"stop-at-page\" must be greater than 0.");
			System.exit(1);
		}

		int startAtPage = arguments.valueInt(null, "start-at-page", defaultStartPage);
		if (startAtPage < 1) {
			out.println("Error: \"start-at-page\" must be greater than 0.");
			System.exit(1);
		}

		int threadCount = arguments.valueInt(null, "threads", defaultThreads);
		if (threadCount < 1) {
			out.println("Error: \"threads\" must be greater than 0.");
			System.exit(1);
		}

		boolean latest = arguments.exists(null, "latest");

		boolean update = arguments.exists(null, "update");

		String query;
		if (arguments.exists(null, "query")) {
			query = arguments.value(null, "query");
			if (query == null) {
				query = "";
			}
		} else {
			query = null;
		}

		if (!latest && !update && query == null) {
			out.println("Nothing to do.  Use \"--update\" to update the database or \"--query\" to query it.\nSee \"--help\" for a list of commands.");
			System.exit(1);
		}

		final DbDao dao = new DirbyEmbeddedDbDao(dbDir);
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
		}

		if (update) {
			if (latestTransactionFromDb == null) {
				//@formatter:off
				out.println(
				"================================================================================\n" +
				"NOTE: This is the first time you are running an update.  To ensure accurate\n" +
				"results, it is recommended that you set MOVE PERMS to FALSE on your res for this\n" +
				"first update.\n" +
				"                                /res set move false\n" +
				"\n" +
				"This could take up to 30 MINUTES depending on your transaction history size.\n" +
				"--------------------------------------------------------------------------------");
				//@formatter:on
				String ready = System.console().readLine("Are you ready to start? (y/n) ");
				if (!"y".equalsIgnoreCase(ready)) {
					out.println("Goodbye.");
					return;
				}
			}
			EmcSession session = settings.getSession();
			boolean repeat;
			do {
				repeat = false;
				if (session == null) {
					out.println("Please enter your EMC login credentials.");
					do {
						//note: "System.console()" doesn't work from Eclipse
						String username = System.console().readLine("Username: ");
						String password = new String(System.console().readPassword("Password: "));
						out.println("Logging in...");
						session = EmcSession.login(username, password);
						if (session == null) {
							out.println("Login failed.  Please try again.");
						}
					} while (session == null);
					settings.setSession(session);
					settings.save();
				}

				final TransactionPuller puller = new TransactionPuller(session);
				puller.setThreadCount(threadCount);
				if (latestTransactionFromDb != null) {
					puller.setStopAtDate(latestTransactionFromDb.getTs());
				}
				if (stopAtPage != null) {
					puller.setStopAtPage(stopAtPage);
				}
				puller.setStartAtPage(startAtPage);

				TransactionPuller.Result result = puller.start(new TransactionPuller.Listener() {
					int transactionCount = 0;
					int pageCount = 0;
					NumberFormat nf = NumberFormat.getInstance();

					@Override
					public synchronized void onPageScraped(int page, List<ShopTransaction> transactions) {
						try {
							dao.insertTransactions(transactions);
							pageCount++;
							transactionCount += transactions.size();
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
				case NOT_LOGGED_IN:
					out.println("Your login session has expired.");
					session = null;
					repeat = true;
					break;
				case FAILED:
					dao.rollback();
					throw result.getThrown();
				case COMPLETED:
					dao.commit();
					out.println("\n" + result.getPageCount() + " pages processed and " + result.getTransactionCount() + " transactions saved in " + (result.getTimeTaken() / 1000) + " seconds.");
					logger.info(result.getPageCount() + " pages processed and " + result.getTransactionCount() + " transactions saved in " + (result.getTimeTaken() / 1000) + " seconds.");
					break;
				}
			} while (repeat);
		}

		if (query != null) {
			Map<String, ItemGroup> itemGroups;
			if (query.isEmpty()) {
				itemGroups = dao.getItemGroups();
			} else {
				Date range[] = parseDateRange(query);
				itemGroups = dao.getItemGroups(range[0], range[1]);
			}

			out.println("Item                |Sold                |Bought              |Net");
			out.println("--------------------------------------------------------------------------------");
			NumberFormat nf = NumberFormat.getNumberInstance();
			long totalAmount = 0;
			for (Map.Entry<String, ItemGroup> entry : itemGroups.entrySet()) {
				//TODO ANSI colors
				ItemGroup itemGroup = entry.getValue();

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
					s = nf.format(itemGroup.getSoldQuantity()) + " / +" + nf.format(itemGroup.getSoldAmount()) + "r";
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
					s = "+" + nf.format(itemGroup.getBoughtQuantity()) + " / " + nf.format(itemGroup.getBoughtAmount()) + "r";
				}
				out.print(s);
				spaces = 20 - s.length();
				if (spaces > 0) {
					out.print(StringUtils.repeat(' ', spaces));
				}
				out.print('|');

				s = "";
				if (itemGroup.getNetQuantity() > 0) {
					s += "+";
				}
				s += nf.format(itemGroup.getNetQuantity()) + " / ";
				if (itemGroup.getNetAmount() > 0) {
					s += "+";
				}
				s += nf.format(itemGroup.getNetAmount()) + "r";
				out.print(s);

				out.println();

				totalAmount += itemGroup.getNetAmount();
			}

			out.print(StringUtils.repeat(' ', 62));
			out.print('|');
			if (totalAmount > 0) {
				out.print('+');
			}
			out.println(nf.format(totalAmount) + "r");
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
}