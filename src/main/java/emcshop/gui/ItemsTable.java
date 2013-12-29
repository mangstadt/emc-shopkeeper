package emcshop.gui;

import static emcshop.util.NumberFormatter.formatQuantity;
import static emcshop.util.NumberFormatter.formatQuantityWithColor;
import static emcshop.util.NumberFormatter.formatRupees;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;
import static emcshop.util.NumberFormatter.formatStacks;
import static emcshop.util.NumberFormatter.formatStacksWithColor;

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
import emcshop.util.FilterList;

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
	private boolean ascending, showQuantitiesInStacks;

	/**
	 * @param itemGroups the items to display
	 */
	public ItemsTable(List<ItemGroup> itemGroups, boolean showQuantitiesInStacks) {
		this(itemGroups, Column.ITEM_NAME, true, showQuantitiesInStacks);
	}

	/**
	 * @param itemGroups the items to display
	 * @param sortedBy the column that the items list is already sorted by
	 * @param sortedByAscending true if the items list is sorted ascending,
	 * false if descending
	 */
	public ItemsTable(List<ItemGroup> itemGroups, Column sortedBy, boolean sortedByAscending, boolean showQtyInStacks) {
		this.itemGroups = itemGroups;
		this.itemGroupsToDisplay = itemGroups;
		prevColumnClicked = sortedBy;
		ascending = sortedByAscending;
		showQuantitiesInStacks = showQtyInStacks;

		getTableHeader().setReorderingAllowed(false);
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
				redraw();
			}
		});

		setModel();

		setDefaultRenderer(ItemGroup.class, new TableCellRenderer() {
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);
			private final JLabel label = new JLabel();

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
				label.setIcon(null);
				label.setHorizontalAlignment(SwingConstants.LEFT);

				ItemGroup group = (ItemGroup) value;
				if (col == Column.ITEM_NAME.ordinal()) {
					ImageIcon img = ImageManager.getItemImage(group.getItem());
					label.setText(group.getItem());
					label.setIcon(img);
				} else if (col == Column.SOLD_QTY.ordinal()) {
					if (group.getSoldQuantity() == 0) {
						label.setText("-");
						label.setHorizontalAlignment(SwingConstants.CENTER);
					} else {
						String text = showQuantitiesInStacks ? formatStacks(group.getSoldQuantity()) : formatQuantity(group.getSoldQuantity());
						label.setText(text);
					}
				} else if (col == Column.SOLD_AMT.ordinal()) {
					if (group.getSoldQuantity() == 0) {
						label.setText("-");
						label.setHorizontalAlignment(SwingConstants.CENTER);
					} else {
						label.setText(formatRupees(group.getSoldAmount()));
					}
				} else if (col == Column.BOUGHT_QTY.ordinal()) {
					if (group.getBoughtQuantity() == 0) {
						label.setText("-");
						label.setHorizontalAlignment(SwingConstants.CENTER);
					} else {
						String text = showQuantitiesInStacks ? formatStacks(group.getBoughtQuantity()) : formatQuantity(group.getBoughtQuantity());
						label.setText(text);
					}
				} else if (col == Column.BOUGHT_AMT.ordinal()) {
					if (group.getBoughtQuantity() == 0) {
						label.setText("-");
						label.setHorizontalAlignment(SwingConstants.CENTER);
					} else {
						label.setText(formatRupees(group.getBoughtAmount()));
					}
				} else if (col == Column.NET_QTY.ordinal()) {
					String text = showQuantitiesInStacks ? formatStacksWithColor(group.getNetQuantity()) : formatQuantityWithColor(group.getNetQuantity());
					label.setText("<html>" + text + "</html>");
				} else if (col == Column.NET_AMT.ordinal()) {
					label.setText("<html>" + formatRupeesWithColor(group.getNetAmount()) + "</html>");
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

	public void filter(FilterList filterList) {
		if (filterList.isEmpty()) {
			itemGroupsToDisplay = itemGroups;
			sortData();
		} else {
			itemGroupsToDisplay = new ArrayList<ItemGroup>();
			for (ItemGroup itemGroup : itemGroups) {
				String itemName = itemGroup.getItem();
				if (filterList.contains(itemName)) {
					itemGroupsToDisplay.add(itemGroup);
				}
			}
		}

		redraw();
	}

	public List<ItemGroup> getDisplayedItemGroups() {
		return itemGroupsToDisplay;
	}

	private void sortData() {
		Collections.sort(itemGroupsToDisplay, new Comparator<ItemGroup>() {
			@Override
			public int compare(ItemGroup a, ItemGroup b) {
				if (!ascending) {
					ItemGroup temp = a;
					a = b;
					b = temp;
				}

				switch (prevColumnClicked) {
				case ITEM_NAME:
					return a.getItem().compareToIgnoreCase(b.getItem());
				case BOUGHT_AMT:
					return a.getBoughtAmount() - b.getBoughtAmount();
				case BOUGHT_QTY:
					return a.getBoughtQuantity() - b.getBoughtQuantity();
				case SOLD_AMT:
					return a.getSoldAmount() - b.getSoldAmount();
				case SOLD_QTY:
					return a.getSoldQuantity() - b.getSoldQuantity();
				case NET_AMT:
					return a.getNetAmount() - b.getNetAmount();
				case NET_QTY:
					return a.getNetQuantity() - b.getNetQuantity();
				}
				return 0;
			}
		});
	}

	public void setShowQuantitiesInStacks(boolean enable) {
		showQuantitiesInStacks = enable;
		AbstractTableModel model = (AbstractTableModel) getModel();
		model.fireTableDataChanged();
	}

	private void redraw() {
		//updates the table's data
		//AbstractTableModel model = (AbstractTableModel) getModel();
		//model.fireTableDataChanged();

		//doing these things will update the table data and update the column header text
		setModel();
		setColumns();
	}

	private void setColumns() {
		//set the width of "item name" column so the name isn't snipped
		columnModel.getColumn(Column.ITEM_NAME.ordinal()).setMinWidth(200);

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
					text = arrow + " " + text;
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
