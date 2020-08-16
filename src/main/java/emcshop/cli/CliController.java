package emcshop.cli;

import java.io.PrintStream;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import emcshop.db.ShopTransactionDb;
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
import emcshop.util.OS;
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

	public void update(Integer startAtPage, Integer stopAtPage) throws Exception {
		LocalDateTime latestTransactionDateFromDb = dao.getLatestTransactionDate();
		boolean firstUpdate = (latestTransactionDateFromDb == null);
		Duration oldestAllowablePaymentTransactionAge = null;

		//set configuration settings for puller
		if (firstUpdate) {
			FirstUpdateViewCli view = new FirstUpdateViewCli();
			view.setStopAtPage(stopAtPage);
			view.setMaxPaymentTransactionAge(Duration.ofDays(7));
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
		loginShower.show();

		EmcSession session = AppContext.instance().get(EmcSession.class);
		RupeeTransactionReader.Builder builder = new RupeeTransactionReader.Builder(session.getCookieStore());
		if (firstUpdate) {
			builder.stop(stopAtPage);
			builder.start(startAtPage);
		} else {
			builder.stop(latestTransactionDateFromDb);
		}

		//start the update
		IUpdateView view = new UpdateViewCli(loginShower);
		IUpdateModel model = new UpdateModelCli(builder, oldestAllowablePaymentTransactionAge);
		UpdatePresenter presenter = new UpdatePresenter(view, model);

		int transactions = presenter.getShopTransactions() + presenter.getPaymentTransactions() + presenter.getBonusFeeTransactions();
		out.println("\n" + presenter.getPageCount() + " pages processed and " + transactions + " transactions saved in " + presenter.getTimeTaken().getSeconds() + " seconds.");
		logger.info(presenter.getPageCount() + " pages processed and " + transactions + " transactions saved in " + presenter.getTimeTaken().getSeconds() + " seconds.");
	}

	public void query(String query, String format) throws Exception {
		LocalDateTime from, to;
		if (query.isEmpty()) {
			from = to = null;
		} else {
			LocalDateTime range[] = parseDateRange(query, dao);
			from = range[0];
			to = range[1];
		}

		Collection<ItemGroup> itemGroups = dao.getItemGroups(from, to, ShopTransactionType.MY_SHOP);

		//sort items
		List<ItemGroup> sortedItemGroups = new ArrayList<>(itemGroups);
		sortedItemGroups.sort((left, right) -> left.getItem().compareToIgnoreCase(right.getItem()));

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
			ANSI ansi = OS.isWindows() ? new ANSINotSupported() : new ANSIImpl();
			ItemIndex index = ItemIndex.instance();

			DateTimeFormatter df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
			out.println("Start date: " + ((from == null) ? "not specified" : df.format(from)));
			out.println("End date: " + ((to == null) ? "not specified" : df.format(to)));
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
				String color = ansi.getColor(netQuantity);
				out.print(color + fixedLength(qf.format(itemGroup.getNetQuantity(), index.getStackSize(itemGroup.getItem())), 19) + ansi.getReset());
				out.print('|');

				int netAmount = itemGroup.getNetAmount();
				color = ansi.getColor(netAmount);
				out.print(color + fixedLength(rf.format(itemGroup.getNetAmount()), 19) + ansi.getReset());

				out.println();

				totalAmount += itemGroup.getNetAmount();
			}

			out.print(StringUtils.repeat(' ', 53));
			out.print("Total: ");
			String color = ansi.getBoldColor(totalAmount);
			out.println(color + rf.format(totalAmount) + ansi.getReset());
		}
	}

	public void export(String query) throws Exception {
		LocalDateTime from, to;
		if (query.isEmpty()) {
			from = to = null;
		} else {
			LocalDateTime range[] = parseDateRange(query, dao);
			from = range[0];
			to = range[1];
		}

		List<ShopTransactionDb> transactions = dao.getTransactionsByDate(from, to, ShopTransactionType.ALL);
		out.println(QueryExporter.generateExportCsv(transactions, from, to));
	}

	/**
	 * @author Michael Angstadt
	 * @see "http://ascii-table.com/ansi-escape-sequences.php"
	 */
	private static interface ANSI {
		default String getBoldColor(int rupeeAmount) {
			if (rupeeAmount > 0) {
				return "\u001B[32;1m"; //green
			}

			if (rupeeAmount < 0) {
				return "\u001B[31;1m"; //red
			}

			return "";
		}

		default String getColor(int rupeeAmount) {
			if (rupeeAmount > 0) {
				return "\u001B[32m"; //green
			}

			if (rupeeAmount < 0) {
				return "\u001B[31m"; //red
			}

			return "";
		}

		default String getReset() {
			return "\u001B[0m";
		}
	}

	private static class ANSIImpl implements ANSI {
		//empty
	}

	private static class ANSINotSupported implements ANSI {
		@Override
		public String getBoldColor(int rupeeAmount) {
			return "";
		}

		@Override
		public String getColor(int rupeeAmount) {
			return "";
		}

		@Override
		public String getReset() {
			return "";
		}
	}

	private static String fixedLength(String value, int maxWidth) {
		int spaces = maxWidth - value.length();
		if (spaces > 0) {
			return value + StringUtils.repeat(' ', spaces);
		}
		return value.substring(0, maxWidth);
	}

	static LocalDateTime[] parseDateRange(String dateRangeStr, DbDao dao) throws SQLException {
		dateRangeStr = dateRangeStr.trim().toLowerCase();

		LocalDateTime from, to;
		if ("today".equals(dateRangeStr)) {
			to = LocalDateTime.now();
			from = to.truncatedTo(ChronoUnit.DAYS);
		} else if ("since last update".equals(dateRangeStr)) {
			to = dao.getLatestUpdateDate();
			from = dao.getSecondLatestUpdateDate();
		} else {
			String split[] = dateRangeStr.split("\\s+to\\s+");
			from = parseFrom(split[0]);

			if (split.length == 1 || "today".equals(split[1])) {
				to = LocalDateTime.now();
			} else {
				to = parseTo(split[1]);
			}
		}

		return new LocalDateTime[] { from, to };
	}

	private static LocalDateTime parseFrom(String s) {
		return parseDate(s, false);
	}

	private static LocalDateTime parseTo(String s) {
		return parseDate(s, true);
	}

	private static LocalDateTime parseDate(String s, boolean to) {
		int colonCount = StringUtils.countMatches(s, ":");
		if (colonCount == 0) {
			LocalDateTime date = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s)).atStartOfDay();
			if (to) {
				date = date.plusDays(1);
			}
			return date;
		} else if (colonCount == 1) {
			return LocalDateTime.from(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").parse(s));
		} else {
			return LocalDateTime.from(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").parse(s));
		}
	}
}
