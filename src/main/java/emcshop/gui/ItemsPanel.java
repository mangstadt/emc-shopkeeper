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
	private List<String> filteredItemNames = new ArrayList<String>(0);
	private MyJScrollPane scrollPane;

	/**
	 * Creates the panel.
	 * @param itemGroups the items to display in the table
	 */
	public ItemsPanel(Collection<ItemGroup> itemGroups) {
		//add all the data to Lists so they can be sorted
		this.itemGroups = new ArrayList<ItemGroup>(itemGroups);
		setLayout(new MigLayout());
		refresh();
	}

	/**
	 * Filters the data by item.
	 */
	public void filterByItems(List<String> items) {
		filteredItemNames = items;
		refresh();
	}

	/**
	 * Scrolls to the top.
	 */
	public void scrollToTop() {
		scrollPane.scrollToTop();
	}

	private void refresh() {
		//filter items
		List<ItemGroup> filteredItems = filterItems();

		//calculate net total
		int netTotal = calculateNetTotal(filteredItems);

		//display data
		removeAll();

		final ItemsTable table = new ItemsTable(filteredItems);
		table.setFillsViewportHeight(true);
		scrollPane = new MyJScrollPane(table);
		add(scrollPane, "grow, w 100%, h 100%, wrap");

		String netTotalLabel;
		{
			StringBuilder sb = new StringBuilder();
			sb.append("<html><font size=5>Net Total: <code>");
			sb.append(formatRupeesWithColor(netTotal));
			sb.append("</code></font></html>");
			netTotalLabel = sb.toString();
		}
		add(new JLabel(netTotalLabel), "align right");

		validate();
	}

	private List<ItemGroup> filterItems() {
		List<ItemGroup> filteredItems;
		if (filteredItemNames.isEmpty()) {
			filteredItems = itemGroups;
		} else {
			filteredItems = new ArrayList<ItemGroup>();
			for (ItemGroup itemGroup : itemGroups) {
				String itemName = itemGroup.getItem().toLowerCase();
				for (String filteredItem : filteredItemNames) {
					filteredItem = filteredItem.toLowerCase();
					boolean add = false;
					if (filteredItem.startsWith("\"") && filteredItem.endsWith("\"")) {
						filteredItem = filteredItem.substring(1, filteredItem.length() - 1); //remove double quotes
						if (itemName.equals(filteredItem)) {
							add = true;
						}
					} else if (itemName.contains(filteredItem)) {
						add = true;
					}

					if (add) {
						filteredItems.add(itemGroup);
						break;
					}
				}
			}
		}
		return filteredItems;
	}

	private int calculateNetTotal(List<ItemGroup> items) {
		int netTotal = 0;
		for (ItemGroup item : items) {
			netTotal += item.getNetAmount();
		}
		return netTotal;
	}
}
