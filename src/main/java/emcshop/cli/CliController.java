package emcshop.cli;

import static emcshop.scraper.TransactionPuller.filter;
import static emcshop.util.NumberFormatter.formatQuantity;
import static emcshop.util.NumberFormatter.formatRupees;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.Main;
import emcshop.QueryExporter;
import emcshop.db.DbDao;
import emcshop.db.ItemGroup;
import emcshop.scraper.BadSessionException;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.EmcSession;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.RupeeTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.scraper.TransactionPuller;
import emcshop.util.Settings;

public class CliController {
	private static final Logger logger = Logger.getLogger(CliController.class.getName());
	private static final String NEWLINE = System.getProperty("line.separator");
	private static final PrintStream out = System.out;

	private final DbDao dao;
	private final Settings settings;

	public CliController(DbDao dao, Settings settings) {
		this.dao = dao;
		this.settings = settings;
	}

	public void update(Integer startAtPage, Integer stopAtPage) throws Throwable {
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
				long time = Main.estimateUpdateTime(stopAtPage);
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

		TransactionPuller.Config.Builder cb = new TransactionPuller.Config.Builder();
		if (firstUpdate) {
			cb.maxPaymentTransactionAge(7);
			if (stopAtPage != null) {
				cb.stopAtPage(stopAtPage);
			}
			cb.startAtPage(startAtPage);
		} else {
			cb.stopAtDate(latestTransactionDateFromDb);
		}
		TransactionPuller.Config config = cb.build();

		TransactionPuller puller = null;
		EmcSession session = settings.getSession();
		String sessionUsername = (session == null) ? null : session.getUsername();
		boolean repeat;
		long started;
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

			started = System.currentTimeMillis();
			try {
				puller = new TransactionPuller(session, config);
			} catch (BadSessionException e) {
				out.println("Your login session has expired.");
				session = null;
				repeat = true;
			}
		} while (repeat);

		final NumberFormat nf = NumberFormat.getInstance();
		int pageCount = 0, transactionCount = 0;
		Date earliestParsedTransactionDate = null;
		List<RupeeTransaction> transactions;
		while ((transactions = puller.nextPage()) != null) {
			if (!transactions.isEmpty()) {
				RupeeTransaction last = transactions.get(transactions.size() - 1); //transactions are ordered date descending
				Date lastTs = last.getTs();
				if (earliestParsedTransactionDate == null || lastTs.before(earliestParsedTransactionDate)) {
					earliestParsedTransactionDate = lastTs;
				}
			}

			List<ShopTransaction> shopTransactions = filter(transactions, ShopTransaction.class);
			for (ShopTransaction shopTransaction : shopTransactions) {
				dao.insertTransaction(shopTransaction, true);
			}

			List<PaymentTransaction> paymentTransactions = filter(transactions, PaymentTransaction.class);
			dao.insertPaymentTransactions(paymentTransactions);

			List<BonusFeeTransaction> bonusFeeTransactions = filter(transactions, BonusFeeTransaction.class);
			dao.updateBonusesFees(bonusFeeTransactions);

			pageCount++;
			transactionCount += shopTransactions.size() + paymentTransactions.size() + bonusFeeTransactions.size();
			out.print("\rPages: " + nf.format(pageCount) + " | Transactions: " + nf.format(transactionCount));
		}

		if (earliestParsedTransactionDate != null) {
			dao.updateBonusesFeesSince(earliestParsedTransactionDate);
		}
		dao.commit();

		settings.setPreviousUpdate(settings.getLastUpdated());
		settings.setLastUpdated(new Date(started));
		settings.setRupeeBalance(puller.getRupeeBalance());
		settings.save();

		long timeTaken = System.currentTimeMillis() - started;
		out.println("\n" + pageCount + " pages processed and " + transactionCount + " transactions saved in " + (timeTaken / 1000) + " seconds.");
		logger.info(pageCount + " pages processed and " + transactionCount + " transactions saved in " + (timeTaken / 1000) + " seconds.");
	}

	public void query(String query, String format) throws Throwable {
		Map<String, ItemGroup> itemGroups;
		Date from, to;
		if (query.isEmpty()) {
			itemGroups = dao.getItemGroups(null, null);
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
