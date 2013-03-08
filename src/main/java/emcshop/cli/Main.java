package emcshop.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

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

	private static final Set<String> validArgs = new HashSet<String>();
	static {
		validArgs.add("help");
		validArgs.add("db");
		validArgs.add("stop-at-page");
		validArgs.add("start-at-page");
		validArgs.add("threads");
		validArgs.add("settings");
		validArgs.add("latest");
		validArgs.add("update");
		validArgs.add("query");
		//TODO add "--version" argument
		//TODO '--profile=NAME' argument
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
			"--update\n" +
			"  Updates the database with the latest transactions.\n" +
			"--query=QUERY\n" +
			"  Shows the net gains/losses of each item.\n" +
			"  All data:           --query\n" +
			"  Today's data:       --query=\"today\"\n" +
			"  Three days of data: --query=\"2013-03-07 to 2013-03-09\"\n" +
			"  Data up to today:   --query=\"2013-03-07 to today\"\n" +
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
			out.println("Nothing to do.  Use \"--update\" to update the database or \"--query\" to query it.");
			System.exit(1);
		}

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
		}

		if (update) {
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

	private static DbDao initDao(File folder) throws SQLException {
		return new DirbyEmbeddedDbDao(new File(folder, "data"));
	}
}