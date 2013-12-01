package emcshop;

import static emcshop.util.NumberFormatter.formatQuantity;
import static emcshop.util.NumberFormatter.formatRupees;

import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import au.com.bytecode.opencsv.CSVWriter;
import emcshop.db.Inventory;
import emcshop.db.ItemGroup;
import emcshop.db.Player;
import emcshop.db.PlayerGroup;
import emcshop.util.BBCodeBuilder;

/**
 * Exports query results to various formats.
 * @author Michael Angstadt
 */
public class QueryExporter {
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
		writer.writeNext(new String[] { "EMC Shopkeeper v" + Main.VERSION + " - " + Main.URL, "", "", "", "", "", netTotal + "" });

		try {
			writer.close();
		} catch (IOException e) {
			//writing to string
		}
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
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
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
		bbCode.text("- - - -Item - - - | - - - -Sold- - - -| - - -Bought- - - -| - - - -Net- - - -").nl();
		for (ItemGroup group : itemGroups) {
			String item = group.getItem();
			bbCodeColumn(item, 17, bbCode);
			bbCode.text(" | ");

			String sold;
			if (group.getSoldQuantity() == 0) {
				sold = StringUtils.repeat("- ", 8) + "-";
			} else {
				sold = formatQuantity(group.getSoldQuantity()) + " / " + formatRupees(group.getSoldAmount());
			}
			bbCodeColumn(sold, 17, bbCode);
			bbCode.text(" | ");

			String bought;
			if (group.getBoughtQuantity() == 0) {
				bought = StringUtils.repeat("- ", 8) + "-";
			} else {
				bought = formatQuantity(group.getBoughtQuantity()) + " / " + formatRupees(group.getBoughtAmount());
			}
			bbCodeColumn(bought, 17, bbCode);
			bbCode.text(" | ");

			String netQuantityStr = formatQuantity(group.getNetQuantity());
			if (group.getNetQuantity() > 0) {
				bbCode.color("green", netQuantityStr);
			} else if (group.getNetQuantity() < 0) {
				bbCode.color("red", netQuantityStr);
			} else {
				bbCode.text(netQuantityStr);
			}

			bbCode.text(" / ");

			String netAmountStr = formatRupees(group.getNetAmount());
			if (group.getNetAmount() > 0) {
				bbCode.color("green", netAmountStr);
			} else if (group.getNetAmount() < 0) {
				bbCode.color("red", netAmountStr);
			} else {
				bbCode.text(netAmountStr);
			}

			bbCode.nl();
		}

		//footer and total
		String footer = "EMC Shopkeeper v" + Main.VERSION;
		bbCode.url(Main.URL, footer);
		bbCode.text(StringUtils.repeat('_', 50 - footer.length()));
		bbCode.b(" Total").text(" | ");
		bbCode.b();
		String netTotalStr = formatRupees(netTotal);
		if (netTotal > 0) {
			bbCode.color("green", netTotalStr);
		} else if (netTotal < 0) {
			bbCode.color("red", netTotalStr);
		} else {
			bbCode.text(netTotalStr);
		}
		bbCode.close(); //close "b"

		bbCode.close(); //close "font"

		return bbCode.toString();
	}

	public static String generatePlayersCsv(List<PlayerGroup> players, Map<PlayerGroup, List<ItemGroup>> items, Date from, Date to) {
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
					df.format(p.getFirstSeen()),
					df.format(p.getLastSeen()),
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
		writer.writeNext(new String[] { "EMC Shopkeeper v" + Main.VERSION + " - " + Main.URL });

		try {
			writer.close();
		} catch (IOException e) {
			//writing to string
		}
		return sw.toString();
	}

	public static String generatePlayersBBCode(List<PlayerGroup> playerGroups, Map<PlayerGroup, List<ItemGroup>> itemGroups, Date from, Date to) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
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

		for (PlayerGroup playerGroup : playerGroups) {
			bbCode.b(playerGroup.getPlayer().getName()).nl();

			//item table
			bbCode.text("- - - -Item - - - | - - - -Sold- - - -| - - -Bought- - - -| - - - -Net- - - -").nl();
			for (ItemGroup group : itemGroups.get(playerGroup)) {
				String item = group.getItem();
				bbCodeColumn(item, 17, bbCode);
				bbCode.text(" | ");

				String sold;
				if (group.getSoldQuantity() == 0) {
					sold = StringUtils.repeat("- ", 8) + "-";
				} else {
					sold = formatQuantity(group.getSoldQuantity()) + " / " + formatRupees(group.getSoldAmount());
				}
				bbCodeColumn(sold, 17, bbCode);
				bbCode.text(" | ");

				String bought;
				if (group.getBoughtQuantity() == 0) {
					bought = StringUtils.repeat("- ", 8) + "-";
				} else {
					bought = formatQuantity(group.getBoughtQuantity()) + " / " + formatRupees(group.getBoughtAmount());
				}
				bbCodeColumn(bought, 17, bbCode);
				bbCode.text(" | ");

				String netQuantityStr = formatQuantity(group.getNetQuantity());
				if (group.getNetQuantity() > 0) {
					bbCode.color("green", netQuantityStr);
				} else if (group.getNetQuantity() < 0) {
					bbCode.color("red", netQuantityStr);
				} else {
					bbCode.text(netQuantityStr);
				}

				bbCode.text(" / ");

				String netAmountStr = formatRupees(group.getNetAmount());
				if (group.getNetAmount() > 0) {
					bbCode.color("green", netAmountStr);
				} else if (group.getNetAmount() < 0) {
					bbCode.color("red", netAmountStr);
				} else {
					bbCode.text(netAmountStr);
				}

				bbCode.nl();
			}
			bbCode.nl();
		}

		//footer
		String footer = "EMC Shopkeeper v" + Main.VERSION;
		bbCode.url(Main.URL, footer);

		bbCode.close(); //close "font"

		return bbCode.toString();
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
		writer.writeNext(new String[] { "EMC Shopkeeper v" + Main.VERSION + " - " + Main.URL });

		try {
			writer.close();
		} catch (IOException e) {
			//writing to string
		}
		return sw.toString();
	}

	public static String generateInventoryBBCode(Collection<Inventory> inventory) {
		BBCodeBuilder bbCode = new BBCodeBuilder();

		bbCode.font("courier new");

		bbCode.text("- - - - - - - - Item - - - - - - | - - - - - -Remaining- - - - - -").nl();
		for (Inventory inv : inventory) {
			String item = inv.getItem();
			bbCodeColumn(item, 32, bbCode);
			bbCode.text(" | ");

			String qty = formatQuantity(inv.getQuantity(), false);
			bbCodeColumn(qty, 32, bbCode);

			bbCode.nl();
		}

		//footer and total
		String footer = "EMC Shopkeeper v" + Main.VERSION;
		bbCode.url(Main.URL, footer);

		//close "font"
		bbCode.close();

		return bbCode.toString();
	}

	private static void bbCodeColumn(String text, int length, BBCodeBuilder sb) {
		sb.text(text);
		if (length - text.length() == 1) {
			sb.text('.');
		} else {
			for (int i = text.length(); i < length; i++) {
				if (i == text.length()) {
					sb.text(' ');
				} else {
					sb.text('.');
				}
			}
		}
	}
}
