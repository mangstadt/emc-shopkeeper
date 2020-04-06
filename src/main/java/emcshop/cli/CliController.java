package emcshop.cli;

import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.github.mangstadt.emc.rupees.RupeeTransactionReader;

import emcshop.AppContext;
import emcshop.ItemIndex;
import emcshop.QueryExporter;
import emcshop.cli.model.UpdateModelCli;
import emcshop.cli.view.FirstUpdateViewCli;
import emcshop.cli.view.LoginShower;
import emcshop.cli.view.UpdateViewCli;
import emcshop.db.DbDao;
import emcshop.db.ItemGroup;
import emcshop.db.ShopTransactionType;
import emcshop.model.FirstUpdateModelImpl;
import emcshop.model.IUpdateModel;
import emcshop.presenter.FirstUpdatePresenter;
import emcshop.presenter.UpdatePresenter;
import emcshop.scraper.EmcSession;
import emcshop.util.QuantityFormatter;
import emcshop.util.RupeeFormatter;
import emcshop.view.IUpdateView;

public class CliController {
	private static final Logger logger = Logger.getLogger(CliController.class.getName());
	private static final PrintStream out = System.out;

	private final DbDao dao;

	public CliController(DbDao dao) {
		this.dao = dao;
	}

	public void update(Integer startAtPage, Integer stopAtPage) throws Throwable {
		Date latestTransactionDateFromDb = dao.getLatestTransactionDate();
		boolean firstUpdate = (latestTransactionDateFromDb == null);
		Integer oldestAllowablePaymentTransactionAge = null;

		//set configuration settings for puller
		if (firstUpdate) {
			FirstUpdateViewCli view = new FirstUpdateViewCli();
			view.setStopAtPage(stopAtPage);
			view.setMaxPaymentTransactionAge(7);
			FirstUpdateModelImpl model = new FirstUpdateModelImpl();
			FirstUpdatePresenter presenter = new FirstUpdatePresenter(view, model);
			if (presenter.isCanceled()) {
				out.println("Goodbye.");
				return;
			}

			stopAtPage = presenter.getStopAtPage();
			oldestAllowablePaymentTransactionAge = view.getMaxPaymentTransactionAge();
		}

		//log user in
		LoginShower loginShower = new LoginShower();
		EmcSession session = AppContext.instance().get(EmcSession.class);
		if (session == null) {
			loginShower.show();
		}

		RupeeTransactionReader.Builder builder = new RupeeTransactionReader.Builder(session.getUsername(), session.getPassword());
		if (firstUpdate) {
			builder.stop(stopAtPage);
			builder.start(startAtPage);
		} else {
			builder.stop(latestTransactionDateFromDb);
		}

		if (oldestAllowablePaymentTransactionAge != null) {
			oldestAllowablePaymentTransactionAge *= 24 * 60 * 60 * 1000;
		}

		//start the update
		IUpdateView view = new UpdateViewCli(loginShower);
		IUpdateModel model = new UpdateModelCli(builder, oldestAllowablePaymentTransactionAge);
		UpdatePresenter presenter = new UpdatePresenter(view, model);

		int transactions = presenter.getShopTransactions() + presenter.getPaymentTransactions() + presenter.getBonusFeeTransactions();
		out.println("\n" + presenter.getPageCount() + " pages processed and " + transactions + " transactions saved in " + (presenter.getTimeTaken() / 1000) + " seconds.");
		logger.info(presenter.getPageCount() + " pages processed and " + transactions + " transactions saved in " + (presenter.getTimeTaken() / 1000) + " seconds.");
	}

	public void query(String query, String format) throws Throwable {
		Collection<ItemGroup> itemGroups;
		Date from, to;
		if (query.isEmpty()) {
			itemGroups = dao.getItemGroups(null, null, ShopTransactionType.MY_SHOP);
			from = to = null;
		} else {
			Date range[] = parseDateRange(query);
			from = range[0];
			to = range[1];
			itemGroups = dao.getItemGroups(from, to, ShopTransactionType.MY_SHOP);
		}

		//sort items
		List<ItemGroup> sortedItemGroups = new ArrayList<ItemGroup>(itemGroups);
		Collections.sort(sortedItemGroups, (left, right) -> left.getItem().compareToIgnoreCase(right.getItem()));

		if ("CSV".equalsIgnoreCase(format)) {
			//calculate net total
			int netTotal = 0;
			for (ItemGroup itemGroup : sortedItemGroups) {
				netTotal += itemGroup.getNetAmount();
			}

			//generate CSV
			out.println(QueryExporter.generateItemsCsv(sortedItemGroups, netTotal, from, to));
		} else if ("BBCODE".equalsIgnoreCase(format)) {
			//calculate net total
			int netTotal = 0;
			for (ItemGroup itemGroup : sortedItemGroups) {
				netTotal += itemGroup.getNetAmount();
			}

			//generate BBCode
			out.println(QueryExporter.generateItemsBBCode(sortedItemGroups, netTotal, from, to));
		} else {
			//ANSI escape codes: http://ascii-table.com/ansi-escape-sequences.php
			final String reset = "\u001B[0m";
			ItemIndex index = ItemIndex.instance();

			out.println("Item                                   |Net Quantity       |Net Amount          ");
			out.println("--------------------------------------------------------------------------------");
			int totalAmount = 0;
			QuantityFormatter qf = new QuantityFormatter();
			qf.setPlus(true);
			RupeeFormatter rf = new RupeeFormatter();
			rf.setPlus(true);
			for (ItemGroup itemGroup : sortedItemGroups) {
				out.print(fixedLength(itemGroup.getItem(), 39));
				out.print('|');

				int netQuantity = itemGroup.getNetQuantity();
				String color = getColor(netQuantity);
				out.print(color + fixedLength(qf.format(itemGroup.getNetQuantity(), index.getStackSize(itemGroup.getItem())), 19) + reset);
				out.print('|');

				int netAmount = itemGroup.getNetAmount();
				color = getColor(netAmount);
				out.print(color + fixedLength(rf.format(itemGroup.getNetAmount()), 19) + reset);

				out.println();

				totalAmount += itemGroup.getNetAmount();
			}

			out.print(StringUtils.repeat(' ', 53));
			out.print("Total: ");
			String color = getBoldColor(totalAmount);
			out.println(color + rf.format(totalAmount) + reset);
		}
	}

	private static String getColor(int number) {
		if (number > 0) {
			return "\u001B[32m"; //green
		}

		if (number < 0) {
			return "\u001B[31m"; //red
		}

		return "";
	}

	private static String getBoldColor(int number) {
		if (number > 0) {
			return "\u001B[32;1m"; //green
		}

		if (number < 0) {
			return "\u001B[31;1m"; //red
		}

		return "";
	}

	private static String fixedLength(String value, int maxWidth) {
		int spaces = maxWidth - value.length();
		if (spaces > 0) {
			return value + StringUtils.repeat(' ', spaces);
		}
		return value.substring(0, maxWidth);
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
