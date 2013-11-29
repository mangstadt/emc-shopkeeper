package emcshop.gui;

import static emcshop.util.NumberFormatter.formatQuantity;
import static emcshop.util.NumberFormatter.formatQuantityWithColor;
import static emcshop.util.NumberFormatter.formatRupees;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import emcshop.db.ItemGroup;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.GroupableColumnsTable;

/**
 * A table that displays transactions grouped by item.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ItemsTable extends GroupableColumnsTable {
	/**
	 * Defines all of the columns in this table. The order in which the enums
	 * are defined is the order that they will appear in the table.
	 */
	public static enum Column {
		ITEM_NAME("Item Name"), SOLD_QTY("Qty"), SOLD_AMT("Rupees"), BOUGHT_QTY("Qty"), BOUGHT_AMT("Rupees"), NET_QTY("Qty"), NET_AMT("Rupees");

		private final String name;

		private Column(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	private final List<ItemGroup> itemGroups;
	private List<ItemGroup> itemGroupsToDisplay;

	private Column prevColumnClicked;
	private boolean ascending;

	/**
	 * @param itemGroups the items to display
	 */
	public ItemsTable(List<ItemGroup> itemGroups) {
		this(itemGroups, Column.ITEM_NAME, true);
	}

	/**
	 * @param itemGroups the items to display
	 * @param sortedBy the column that the items list is already sorted by
	 * @param sortedByAscending true if the items list is sorted ascending,
	 * false if descending
	 */
	public ItemsTable(List<ItemGroup> itemGroups, Column sortedBy, boolean sortedByAscending) {
		this.itemGroups = itemGroups;
		this.itemGroupsToDisplay = itemGroups;
		prevColumnClicked = sortedBy;
		ascending = sortedByAscending;

		setColumnSelectionAllowed(false);
		setRowSelectionAllowed(false);
		setCellSelectionEnabled(false);
		setRowHeight(24);

		getTableHeader().addMouseListener(new MouseAdapter() {
			private final Column columns[] = Column.values();

			@Override
			public void mouseClicked(MouseEvent e) {
				int index = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
				if (index < 0) {
					return;
				}

				Column column = columns[index];
				if (column == prevColumnClicked) {
					ascending = !ascending;
				} else {
					prevColumnClicked = column;
					if (column == Column.NET_AMT || column == Column.BOUGHT_QTY || column == Column.SOLD_AMT) {
						ascending = false;
					} else {
						ascending = true;
					}
				}

				sortData();
			}
		});

		setModel();

		setDefaultRenderer(ItemGroup.class, new TableCellRenderer() {
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
				JLabel label = null;

				ItemGroup group = (ItemGroup) value;
				if (col == Column.ITEM_NAME.ordinal()) {
					ImageIcon img = ImageManager.getItemImage(group.getItem());
					label = new JLabel(group.getItem(), img, SwingConstants.LEFT);
				} else if (col == Column.SOLD_QTY.ordinal()) {
					if (group.getSoldQuantity() == 0) {
						label = new JLabel("-", SwingConstants.CENTER);
					} else {
						label = new JLabel(formatQuantity(group.getSoldQuantity()));
					}
				} else if (col == Column.SOLD_AMT.ordinal()) {
					if (group.getSoldQuantity() == 0) {
						label = new JLabel("-", SwingConstants.CENTER);
					} else {
						label = new JLabel(formatRupees(group.getSoldAmount()));
					}
				} else if (col == Column.BOUGHT_QTY.ordinal()) {
					if (group.getBoughtQuantity() == 0) {
						label = new JLabel("-", SwingConstants.CENTER);
					} else {
						label = new JLabel(formatQuantity(group.getBoughtQuantity()));
					}
				} else if (col == Column.BOUGHT_AMT.ordinal()) {
					if (group.getBoughtQuantity() == 0) {
						label = new JLabel("-", SwingConstants.CENTER);
					} else {
						label = new JLabel(formatRupees(group.getBoughtAmount()));
					}
				} else if (col == Column.NET_QTY.ordinal()) {
					label = new JLabel("<html>" + formatQuantityWithColor(group.getNetQuantity()) + "</html>");
				} else if (col == Column.NET_AMT.ordinal()) {
					label = new JLabel("<html>" + formatRupeesWithColor(group.getNetAmount()) + "</html>");
				}

				//set the background color of the row
				Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
				label.setOpaque(true);
				label.setBackground(color);

				return label;
			}
		});

		setColumns();
	}

	public void filter(List<String> filteredItemNames) {
		if (filteredItemNames.isEmpty()) {
			itemGroupsToDisplay = itemGroups;
			sortData();
		} else {
			itemGroupsToDisplay = new ArrayList<ItemGroup>();
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
						itemGroupsToDisplay.add(itemGroup);
						break;
					}
				}
			}
		}

		refresh();
	}

	public List<ItemGroup> getDisplayedItemGroups() {
		return itemGroupsToDisplay;
	}

	private void sortData() {
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

		Collections.sort(itemGroupsToDisplay, comparator);
		refresh();
	}

	private void refresh() {
		//updates the table's data
		//AbstractTableModel model = (AbstractTableModel) getModel();
		//model.fireTableDataChanged();

		//doing these things will update the table data and update the column header text
		setModel();
		setColumns();
	}

	private void setColumns() {
		//set the width of "item name" column so the name isn't snipped
		columnModel.getColumn(Column.ITEM_NAME.ordinal()).setMinWidth(150);

		//define column groups
		List<ColumnGroup> columnGroups = new ArrayList<ColumnGroup>();

		ColumnGroup columnGroup = new ColumnGroup("Sold");
		columnGroup.add(columnModel.getColumn(Column.SOLD_QTY.ordinal()));
		columnGroup.add(columnModel.getColumn(Column.SOLD_AMT.ordinal()));
		columnGroups.add(columnGroup);

		columnGroup = new ColumnGroup("Bought");
		columnGroup.add(columnModel.getColumn(Column.BOUGHT_QTY.ordinal()));
		columnGroup.add(columnModel.getColumn(Column.BOUGHT_AMT.ordinal()));
		columnGroups.add(columnGroup);

		columnGroup = new ColumnGroup("Net");
		columnGroup.add(columnModel.getColumn(Column.NET_QTY.ordinal()));
		columnGroup.add(columnModel.getColumn(Column.NET_AMT.ordinal()));
		columnGroups.add(columnGroup);

		setColumnGroups(columnGroups);
	}

	private void setModel() {
		setModel(new AbstractTableModel() {
			private final Column columns[] = Column.values();

			@Override
			public int getColumnCount() {
				return columns.length;
			}

			@Override
			public String getColumnName(int index) {
				Column column = columns[index];

				String text = column.getName();
				if (prevColumnClicked == column) {
					String arrow = (ascending) ? "\u25bc" : "\u25b2";
					text = arrow + text;
				}
				return text;
			}

			@Override
			public int getRowCount() {
				return itemGroupsToDisplay.size();
			}

			@Override
			public Object getValueAt(int row, int col) {
				return itemGroupsToDisplay.get(row);
			}

			public Class<?> getColumnClass(int c) {
				return ItemGroup.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				return false;
			}
		});
	}
}
