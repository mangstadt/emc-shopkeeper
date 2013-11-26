package emcshop.gui;

import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import emcshop.db.ItemGroup;

/**
 * A panel that displays transactions grouped by item.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ItemsPanel extends JPanel {
	private final List<ItemGroup> itemGroups;
	private final MyJScrollPane scrollPane;
	private final ItemsTable itemsTable;
	private final JLabel netTotal;

	/**
	 * Creates the panel.
	 * @param itemGroups the items to display in the table
	 */
	public ItemsPanel(Collection<ItemGroup> itemGroups) {
		this.itemGroups = new ArrayList<ItemGroup>(itemGroups);

		setLayout(new MigLayout());

		itemsTable = new ItemsTable(this.itemGroups);
		itemsTable.setFillsViewportHeight(true);
		scrollPane = new MyJScrollPane(itemsTable);
		add(scrollPane, "grow, w 100%, h 100%, wrap");

		netTotal = new JLabel();
		add(netTotal, "align right");
	}

	/**
	 * Filters the data by item.
	 */
	public void filterByItems(List<String> items) {
		//filter the table
		itemsTable.filter(items);

		//update the net total label
		List<ItemGroup> displayedItemGroups = itemsTable.getDisplayedItemGroups();
		int total = calculateNetTotal(displayedItemGroups);
		StringBuilder sb = new StringBuilder();
		sb.append("<html><font size=5>Net Total: <code>");
		sb.append(formatRupeesWithColor(total));
		sb.append("</code></font></html>");
		netTotal.setText(sb.toString());
	}

	/**
	 * Scrolls to the top.
	 */
	public void scrollToTop() {
		scrollPane.scrollToTop();
	}

	private int calculateNetTotal(List<ItemGroup> items) {
		int netTotal = 0;
		for (ItemGroup item : items) {
			netTotal += item.getNetAmount();
		}
		return netTotal;
	}
}
