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
import emcshop.gui.ItemsTable.Column;
import emcshop.gui.ItemsTable.ColumnClickHandler;

/**
 * A panel that displays transactions grouped by item.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ItemsPanel extends JPanel {
	private final List<ItemGroup> itemGroups;
	private List<String> filteredItemNames = new ArrayList<String>(0);
	private MyJScrollPane scrollPane;
	private Column prevColumnClicked;
	private boolean ascending;

	/**
	 * Creates the panel.
	 * @param itemGroups the items to display in the table
	 */
	public ItemsPanel(Collection<ItemGroup> itemGroups) {
		//add all the data to Lists so they can be sorted
		this.itemGroups = new ArrayList<ItemGroup>(itemGroups);
		setLayout(new MigLayout());
		prevColumnClicked = Column.ITEM_NAME;
		ascending = true;
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

		final ItemsTable table = new ItemsTable(filteredItems, new ColumnClickHandler() {
			@Override
			public void onClick(Column column) {
				if (column == prevColumnClicked) {
					ascending = !ascending;
				} else {
					prevColumnClicked = column;
					ascending = true;
				}
				refresh();
			}
		});
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

	private void sortData(List<ItemGroup> items) {
		Comparator<ItemGroup> comparator = null;

		switch (prevColumnClicked) {
		case ITEM_NAME:
			//sort by item name
			comparator = new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					if (ascending) {
						return a.getItem().compareToIgnoreCase(b.getItem());
					}
					return b.getItem().compareToIgnoreCase(a.getItem());
				}
			};
			break;
		case BOUGHT_AMT:
			comparator = new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					if (ascending) {
						return a.getBoughtAmount() - b.getBoughtAmount();
					}
					return b.getBoughtAmount() - a.getBoughtAmount();
				}
			};
			break;
		case BOUGHT_QTY:
			comparator = new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					if (ascending) {
						return a.getBoughtQuantity() - b.getBoughtQuantity();
					}
					return b.getBoughtQuantity() - a.getBoughtQuantity();
				}
			};
			break;

		case SOLD_AMT:
			comparator = new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					if (ascending) {
						return a.getSoldAmount() - b.getSoldAmount();
					}
					return b.getSoldAmount() - a.getSoldAmount();
				}
			};
			break;
		case SOLD_QTY:
			comparator = new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					if (ascending) {
						return a.getSoldQuantity() - b.getSoldQuantity();
					}
					return b.getSoldQuantity() - a.getSoldQuantity();
				}
			};
			break;
		case NET_AMT:
			comparator = new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					if (ascending) {
						return a.getNetAmount() - b.getNetAmount();
					}
					return b.getNetAmount() - a.getNetAmount();
				}
			};
			break;
		case NET_QTY:
			comparator = new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					if (ascending) {
						return a.getNetQuantity() - b.getNetQuantity();
					}
					return b.getNetQuantity() - a.getNetQuantity();
				}
			};
			break;
		}

		Collections.sort(items, comparator);
	}

	private int calculateNetTotal(List<ItemGroup> items) {
		int netTotal = 0;
		for (ItemGroup item : items) {
			netTotal += item.getNetAmount();
		}
		return netTotal;
	}
}
