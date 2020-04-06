package emcshop;

import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ListMultimap;

import au.com.bytecode.opencsv.CSVWriter;
import emcshop.db.Inventory;
import emcshop.db.ItemGroup;
import emcshop.db.Player;
import emcshop.db.PlayerGroup;
import emcshop.db.ShopTransactionDb;
import emcshop.util.BBCodeBuilder;
import emcshop.util.QuantityFormatter;
import emcshop.util.RupeeFormatter;

/**
 * Exports query results to various formats.
 * @author Michael Angstadt
 */
public final class QueryExporter {
	/**
	 * Generates a CSV string.
	 * @param itemGroups the items
	 * @param netTotal the net total
	 * @param from the start date or null if there is no start date
	 * @param to the end date or null if there is no end date
	 * @return the CSV string
	 */
	public static String generateItemsCsv(Collection<ItemGroup> itemGroups, int netTotal, Date from, Date to) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);

		writer.writeNext(new String[] { (from == null) ? "no start date" : df.format(from), (to == null) ? "no end date" : df.format(to) });
		writer.writeNext(new String[] { "Item", "Sold Quantity", "Sold Amount", "Bought Quantity", "Bought Amount", "Net Quantity", "Net Amount" });
		for (ItemGroup group : itemGroups) {
			//@formatter:off
			writer.writeNext(new String[]{
				group.getItem(),
				group.getSoldQuantity() + "",
				group.getSoldAmount() + "",
				group.getBoughtQuantity() + "",
				group.getBoughtAmount() + "",
				group.getNetQuantity() + "",
				group.getNetAmount() + ""
			});
			//@formatter:on
		}
		writer.writeNext(new String[] { "EMC Shopkeeper v" + EMCShopkeeper.VERSION + " - " + EMCShopkeeper.URL, "", "", "", "", "", netTotal + "" });

		//writing to a string
		IOUtils.closeQuietly(writer);

		return sw.toString();
	}

	/**
	 * Generates a BBCode string.
	 * @param itemGroups the items
	 * @param netTotal the net total
	 * @param from the start date or null if there is no start date
	 * @param to the end date or null if there is no end date
	 * @return the BBCode string
	 */
	public static String generateItemsBBCode(Collection<ItemGroup> itemGroups, int netTotal, Date from, Date to) {
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		BBCodeBuilder bbCode = new BBCodeBuilder();

		bbCode.font("courier new");

		//date range
		bbCode.b();
		if (from == null && to == null) {
			bbCode.text("entire history");
		} else if (from == null) {
			bbCode.text("up to ").text(df.format(to));
		} else if (to == null) {
			bbCode.text(df.format(from)).text(" to today");
		} else if (from.equals(to)) {
			bbCode.text(df.format(from));
		} else {
			bbCode.text(df.format(from)).text(" to ").text(df.format(to));
		}
		bbCode.close().nl();

		//item table
		generateItemsTableBBCode(itemGroups, bbCode, true);

		//footer and total
		String footer = "EMC Shopkeeper v" + EMCShopkeeper.VERSION;
		bbCode.url(EMCShopkeeper.URL, footer);
		bbCode.text(" ");
		bbCode.text(StringUtils.repeat('_', 44 - footer.length()));
		bbCode.b(" Total").text(" | ");
		bbCode.b();
		RupeeFormatter rf = new RupeeFormatter();
		rf.setPlus(true);
		String netTotalStr = rf.format(netTotal);
		colorize(netTotal, netTotalStr, bbCode);
		bbCode.close(); //close "b"

		bbCode.close(); //close "font"

		return bbCode.toString();
	}

	private static void generateItemsTableBBCode(Collection<ItemGroup> itemGroups, BBCodeBuilder bbCode, boolean includeProjectLink) {
		ItemIndex index = ItemIndex.instance();
		bbCode.u("Item").text(" - - - - - - - - - - - - - - - - | ").u("Net Quantity").text(" | ").u("Net Amount").nl();
		int totalAmount = 0;
		QuantityFormatter qf = new QuantityFormatter();
		qf.setPlus(true);
		RupeeFormatter rf = new RupeeFormatter();
		rf.setPlus(true);
		for (ItemGroup group : itemGroups) {
			String item = group.getItem();
			totalAmount += group.getNetAmount();

			bbCodeColumn(item, 36, bbCode);
			bbCode.text(" | ");

			int netQuantity = group.getNetQuantity();
			String netQuantityStr = qf.format(netQuantity, index.getStackSize(item));
			colorize(netQuantity, netQuantityStr, bbCode);
			bbCodeColumn("", 12 - netQuantityStr.length(), bbCode);
			bbCode.text(" | ");

			int netAmount = group.getNetAmount();
			String netAmountStr = rf.format(netAmount);
			colorize(netAmount, netAmountStr, bbCode);

			bbCode.nl();
		}

		//footer and total
		int padding = 45;
		if (includeProjectLink) {
			String footer = "EMC Shopkeeper v" + EMCShopkeeper.VERSION;
			bbCode.url(EMCShopkeeper.URL, footer);
			bbCode.text(" ");
			padding -= footer.length() - 1;
		}

		bbCode.text(StringUtils.repeat('_', padding));
		bbCode.b(" Total").text(" | ");
		bbCode.b();
		String netTotalStr = rf.format(totalAmount);
		colorize(totalAmount, netTotalStr, bbCode);
		bbCode.close(); //close "b"
	}

	private static void colorize(int amount, String str, BBCodeBuilder builder) {
		if (amount > 0) {
			builder.color("green", str);
		} else if (amount < 0) {
			builder.color("red", str);
		} else {
			builder.text(str);
		}
	}

	public static String generatePlayersCsv(List<PlayerGroup> players, ListMultimap<PlayerGroup, ItemGroup> items, Date from, Date to) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);

		writer.writeNext(new String[] { (from == null) ? "" : df.format(from), (to == null) ? "" : df.format(to) });
		writer.writeNext(new String[] { "Player", "First Seen", "Last Seen", "Item", "Sold Quantity", "Sold Amount", "Bought Quantity", "Bought Amount", "Net Quantity", "Net Amount" });
		for (PlayerGroup player : players) {
			Player p = player.getPlayer();
			for (ItemGroup group : items.get(player)) {
				//@formatter:off
				writer.writeNext(new String[]{
					p.getName(),
					(p.getFirstSeen() == null) ? "" : df.format(p.getFirstSeen()),
					(p.getLastSeen() == null) ? "" : df.format(p.getLastSeen()),
					group.getItem(),
					group.getSoldQuantity() + "",
					group.getSoldAmount() + "",
					group.getBoughtQuantity() + "",
					group.getBoughtAmount() + "",
					group.getNetQuantity() + "",
					group.getNetAmount() + ""
				});
				//@formatter:on
			}
		}
		writer.writeNext(new String[] { "EMC Shopkeeper v" + EMCShopkeeper.VERSION + " - " + EMCShopkeeper.URL });

		//writing to a string
		IOUtils.closeQuietly(writer);

		return sw.toString();
	}

	public static String generatePlayersBBCode(List<PlayerGroup> playerGroups, ListMultimap<PlayerGroup, ItemGroup> itemGroups, Date from, Date to) {
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		BBCodeBuilder bbCode = new BBCodeBuilder();

		bbCode.font("courier new");

		//date range
		bbCode.b();
		if (from == null && to == null) {
			bbCode.text("entire history");
		} else if (from == null) {
			bbCode.text("up to ").text(df.format(to));
		} else if (to == null) {
			bbCode.text(df.format(from)).text(" to today");
		} else if (from.equals(to)) {
			bbCode.text(df.format(from));
		} else {
			bbCode.text(df.format(from)).text(" to ").text(df.format(to));
		}
		bbCode.close().nl().nl();

		for (PlayerGroup playerGroup : playerGroups) {
			bbCode.b(playerGroup.getPlayer().getName()).nl();
			generateItemsTableBBCode(itemGroups.get(playerGroup), bbCode, false);
			bbCode.nl().nl();
		}

		//footer
		String footer = "EMC Shopkeeper v" + EMCShopkeeper.VERSION;
		bbCode.url(EMCShopkeeper.URL, footer);

		bbCode.close(); //close "font"

		return bbCode.toString();
	}

	public static String generateTransactionsBBCode(Collection<ShopTransactionDb> transactions, int netTotal, Date from, Date to) {
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		BBCodeBuilder bbCode = new BBCodeBuilder();

		bbCode.font("courier new");

		//date range
		bbCode.b();
		if (from == null && to == null) {
			bbCode.text("entire history");
		} else if (from == null) {
			bbCode.text("up to ").text(df.format(to));
		} else if (to == null) {
			bbCode.text(df.format(from)).text(" to today");
		} else if (from.equals(to)) {
			bbCode.text(df.format(from));
		} else {
			bbCode.text(df.format(from)).text(" to ").text(df.format(to));
		}
		bbCode.close().nl();

		//item table
		QuantityFormatter qf = new QuantityFormatter();
		qf.setPlus(true);
		RupeeFormatter rf = new RupeeFormatter();
		rf.setPlus(true);
		DateFormat transactionDf = new SimpleDateFormat("MMM dd, HH:mm");
		bbCode.u("Date").text("- - - - - | ").u("Player").text(" - - - - | ").u("Item").text(" - - - - - - | ").u("Quantity").text(" | ").u("Amount").nl();
		for (ShopTransactionDb transaction : transactions) {
			Date ts = transaction.getTs();
			bbCodeColumn(transactionDf.format(ts), 13, bbCode);
			bbCode.text(" | ");

			String player = transaction.getShopCustomer();
			bbCodeColumn(player, 14, bbCode);
			bbCode.text(" | ");

			String item = transaction.getItem();
			bbCodeColumn(item, 16, bbCode);
			bbCode.text(" | ");

			int quantity = transaction.getQuantity();
			String quantityStr = qf.format(quantity);
			colorize(quantity, quantityStr, bbCode);
			bbCodeColumn("", 8 - quantityStr.length(), bbCode);
			bbCode.text(" | ");

			int amount = transaction.getAmount();
			String amountStr = rf.format(amount);
			colorize(amount, amountStr, bbCode);

			bbCode.nl();
		}

		//footer and total
		String footer = "EMC Shopkeeper v" + EMCShopkeeper.VERSION;
		bbCode.url(EMCShopkeeper.URL, footer);
		bbCode.text(" | ");
		bbCode.b();
		bbCode.text(" Total: ");
		String netTotalStr = rf.format(netTotal);
		colorize(netTotal, netTotalStr, bbCode);
		bbCode.close(); //close "b"

		bbCode.close(); //close "font"

		return bbCode.toString();
	}

	public static String generateTransactionsCsv(Collection<ShopTransactionDb> transactions, int netTotal, Date from, Date to) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);

		writer.writeNext(new String[] { (from == null) ? "no start date" : df.format(from), (to == null) ? "no end date" : df.format(to) });
		writer.writeNext(new String[] { "Date", "Player", "Item", "Quantity", "Amount" });
		for (ShopTransactionDb group : transactions) {
			//@formatter:off
			writer.writeNext(new String[]{
				df.format(group.getTs()),
				group.getShopCustomer(),
				group.getItem(),
				group.getQuantity() + "",
				group.getAmount() + ""
			});
			//@formatter:on
		}
		writer.writeNext(new String[] { "EMC Shopkeeper v" + EMCShopkeeper.VERSION + " - " + EMCShopkeeper.URL, "", "", "", netTotal + "" });

		//writing to a string
		IOUtils.closeQuietly(writer);

		return sw.toString();
	}

	public static String generateInventoryCsv(Collection<Inventory> inventory) {
		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);

		writer.writeNext(new String[] { "Item", "Remaining" });
		for (Inventory inv : inventory) {
			//@formatter:off
			writer.writeNext(new String[]{
				inv.getItem(),
				inv.getQuantity() + ""
			});
			//@formatter:on
		}
		writer.writeNext(new String[] { "EMC Shopkeeper v" + EMCShopkeeper.VERSION + " - " + EMCShopkeeper.URL });

		//writing to a string
		IOUtils.closeQuietly(writer);

		return sw.toString();
	}

	public static String generateInventoryBBCode(Collection<Inventory> inventory) {
		BBCodeBuilder bbCode = new BBCodeBuilder();
		ItemIndex index = ItemIndex.instance();

		bbCode.font("courier new");

		QuantityFormatter qf = new QuantityFormatter();
		bbCode.u("Item").text(" - - - - - - - - - - - - - - - - - - | ").u("Remaining").nl();
		for (Inventory inv : inventory) {
			String item = inv.getItem();
			bbCodeColumn(item, 40, bbCode);
			bbCode.text(" | ");

			String quantityStr = qf.format(inv.getQuantity(), index.getStackSize(item));
			bbCode.text(quantityStr);

			bbCode.nl();
		}

		//footer and total
		String footer = "EMC Shopkeeper v" + EMCShopkeeper.VERSION;
		bbCode.url(EMCShopkeeper.URL, footer);

		//close "font"
		bbCode.close();

		return bbCode.toString();
	}

	private static void bbCodeColumn(String text, int length, BBCodeBuilder sb) {
		if (text.length() == length) {
			sb.text(text);
			return;
		}

		if (text.length() > length) {
			text = text.substring(0, length).trim();
		}

		if (length - text.length() == 1) {
			sb.text(text + '.');
			return;
		}

		sb.text(text);
		for (int i = text.length(); i < length; i++) {
			if (i == text.length()) {
				sb.text(' ');
			} else {
				sb.text('.');
			}
		}
	}

	private QueryExporter() {
		//hide
	}
}
