package emcshop.gui;

import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
	private Sort sort;
	private MyJScrollPane scrollPane;

	/**
	 * Creates the panel.
	 * @param itemGroups the items to display in the table
	 */
	public ItemsPanel(Collection<ItemGroup> itemGroups) {
		//add all the data to Lists so they can be sorted
		this.itemGroups = new ArrayList<ItemGroup>(itemGroups);
		setLayout(new MigLayout());
		sortByItemName();
	}

	/**
	 * Sorts the data by item name.
	 */
	public void sortByItemName() {
		sort = Sort.ITEM;
		refresh();
	}

	/**
	 * Sorts the data by most profitable.
	 */
	public void sortByMostProfitable() {
		sort = Sort.MOST_PROFITABLE;
		refresh();
	}

	/**
	 * Sorts the data by least profitable.
	 */
	public void sortByLeastProfitable() {
		sort = Sort.LEAST_PROFITABLE;
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

		//sort data
		sortData(filteredItems);

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
					if (itemName.contains(filteredItem.toLowerCase())) {
						filteredItems.add(itemGroup);
						break;
					}
				}
			}
		}
		return filteredItems;
	}

	private void sortData(List<ItemGroup> items) {
		switch (sort) {
		case ITEM:
			//sort by item name
			Collections.sort(items, new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					return a.getItem().compareToIgnoreCase(b.getItem());
				}
			});
			break;
		case MOST_PROFITABLE:
			Collections.sort(items, new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					return b.getNetAmount() - a.getNetAmount();
				}
			});
			break;
		case LEAST_PROFITABLE:
			Collections.sort(items, new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					return a.getNetAmount() - b.getNetAmount();
				}
			});
			break;
		}
	}

	private int calculateNetTotal(List<ItemGroup> items) {
		int netTotal = 0;
		for (ItemGroup item : items) {
			netTotal += item.getNetAmount();
		}
		return netTotal;
	}

	private enum Sort {
		ITEM, MOST_PROFITABLE, LEAST_PROFITABLE
	}
}
