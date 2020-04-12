package emcshop.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import emcshop.ItemIndex;
import emcshop.db.ItemGroup;
import emcshop.db.ShopTransactionType;
import emcshop.gui.images.Images;
import emcshop.gui.lib.GroupableColumnsTable;
import emcshop.util.QuantityFormatter;
import emcshop.util.RupeeFormatter;

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
		ITEM_NAME("Item Name"), PPU_BUY("Buy"), PPU_SELL("Sell"), SOLD_QTY("Qty"), SOLD_AMT("Rupees"), BOUGHT_QTY("Qty"), BOUGHT_AMT("Rupees"), NET_QTY("Qty"), NET_AMT("Rupees");

		private final String name;

		private Column(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	private final Column columns[] = Column.values();
	private final List<ItemGroup> itemGroups;
	private List<ItemGroup> itemGroupsToDisplay;

	private Column prevColumnClicked;
	private boolean ascending, showQuantitiesInStacks;

	/**
	 * @param itemGroups the items to display
	 * @param shopTransactionType the shop transaction type
	 * @param showQtyInStacks true to display item quantities in stacks, false
	 * not to
	 */
	public ItemsTable(List<ItemGroup> itemGroups, ShopTransactionType shopTransactionType, boolean showQtyInStacks) {
		this(itemGroups, Column.ITEM_NAME, true, shopTransactionType, showQtyInStacks);
	}

	/**
	 * @param itemGroups the items to display
	 * @param sortedBy the column that the items list is already sorted by
	 * @param sortedByAscending true if the items list is sorted ascending,
	 * false if descending
	 * @param shopTransactionType the shop transaction type
	 * @param showQtyInStacks true to display item quantities in stacks, false
	 * not to
	 */
	public ItemsTable(List<ItemGroup> itemGroups, Column sortedBy, boolean sortedByAscending, final ShopTransactionType shopTransactionType, boolean showQtyInStacks) {
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
			private final Set<Column> descendingByDefault = EnumSet.of(Column.NET_AMT, Column.BOUGHT_QTY, Column.SOLD_AMT, Column.PPU_BUY, Column.PPU_SELL);

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
					ascending = !descendingByDefault.contains(column);
				}

				sortData();
				redraw();
			}
		});

		setModel();

		setDefaultRenderer(ItemGroup.class, new TableCellRenderer() {
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);
			private final ItemIndex index = ItemIndex.instance();
			private final JLabel label = new JLabel();
			{
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(4, 4, 4, 4));
			}
			private final RupeeFormatter ppuRf = new RupeeFormatter(2);

			private final RupeeFormatter rf = new RupeeFormatter();
			{
				rf.setPlus(true);
			}

			private final QuantityFormatter qf = new QuantityFormatter();
			{
				qf.setPlus(true);
			}

			private final QuantityFormatter netQf = new QuantityFormatter();
			{
				netQf.setColor(true);
				netQf.setPlus(true);
			}
			private final RupeeFormatter netRf = new RupeeFormatter();
			{
				netRf.setColor(true);
				netRf.setPlus(true);
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
				if (value == null) {
					return null;
				}

				ItemGroup group = (ItemGroup) value;
				Column column = columns[col];

				ImageIcon image = getIcon(group, column);
				String text = getText(group, column);
				int alignment = SwingConstants.LEFT;
				if (text == null) {
					text = "-";
					alignment = SwingConstants.CENTER;
				}

				label.setIcon(image);
				label.setText(text);
				label.setHorizontalAlignment(alignment);

				//set the background color of the row
				Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
				label.setBackground(color);

				return label;
			}

			private ImageIcon getIcon(ItemGroup group, Column column) {
				switch (column) {
				case ITEM_NAME:
					return Images.getItemImage(group.getItem());
				default:
					return null;
				}
			}

			private int stackSizeOf(ItemGroup group) {
				return showQuantitiesInStacks ? index.getStackSize(group.getItem()) : 1;
			}

			public String getText(ItemGroup group, Column column) {
				switch (column) {
				case ITEM_NAME:
					return group.getItem();
				case PPU_BUY:
					switch (shopTransactionType) {
					case OTHER_SHOPS:
						return (group.getBoughtQuantity() != 0) ? ppuRf.format(group.getBoughtPPU()) : null;
					default:
						if (group.getSoldQuantity() != 0) {
							double boughtPpu = group.getBoughtPPU();
							double soldPpu = group.getSoldPPU();
							String red = "";
							if (boughtPpu > 0 && boughtPpu > soldPpu) {
								red = "<html><font color=red>";
							}
							return red + ppuRf.format(soldPpu);
						}
						return null;
					}
				case PPU_SELL:
					switch (shopTransactionType) {
					case OTHER_SHOPS:
						return (group.getSoldQuantity() != 0) ? ppuRf.format(group.getSoldPPU()) : null;
					default:
						if (group.getBoughtQuantity() != 0) {
							double boughtPpu = group.getBoughtPPU();
							double soldPpu = group.getSoldPPU();
							String red = "";
							if (soldPpu > 0 && boughtPpu > soldPpu) {
								red = "<html><font color=red>";
							}
							return red + ppuRf.format(boughtPpu);
						}
						return null;
					}
				case SOLD_QTY:
					return (group.getSoldQuantity() == 0) ? null : qf.format(group.getSoldQuantity(), stackSizeOf(group));
				case SOLD_AMT:
					return (group.getSoldQuantity() == 0) ? null : rf.format(group.getSoldAmount());
				case BOUGHT_QTY:
					return (group.getBoughtQuantity() == 0) ? null : qf.format(group.getBoughtQuantity(), stackSizeOf(group));
				case BOUGHT_AMT:
					return (group.getBoughtQuantity() == 0) ? null : rf.format(group.getBoughtAmount());
				case NET_QTY:
					return "<html>" + netQf.format(group.getNetQuantity(), stackSizeOf(group));
				case NET_AMT:
					return "<html>" + netRf.format(group.getNetAmount());
				default:
					return null;
				}
			}
		});

		setColumns();
	}

	public void filter(FilterList filterList) {
		if (filterList.isEmpty()) {
			itemGroupsToDisplay = itemGroups;
			sortData();
		} else {
			itemGroupsToDisplay = new ArrayList<>();
			for (ItemGroup itemGroup : itemGroups) {
				String itemName = itemGroup.getItem();
				if (filterList.isFiltered(itemName)) {
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
		Collections.sort(itemGroupsToDisplay, (a, b) -> {
			if (!ascending) {
				ItemGroup temp = a;
				a = b;
				b = temp;
			}

			switch (prevColumnClicked) {
			case ITEM_NAME:
				return a.getItem().compareToIgnoreCase(b.getItem());
			case PPU_BUY:
				return Double.valueOf(a.getSoldPPU()).compareTo(Double.valueOf(b.getSoldPPU()));
			case PPU_SELL:
				return Double.valueOf(a.getBoughtPPU()).compareTo(Double.valueOf(b.getBoughtPPU()));
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
		});
	}

	public void setShowQuantitiesInStacks(boolean enable) {
		showQuantitiesInStacks = enable;

		//re-render the "quantity" columns
		AbstractTableModel model = (AbstractTableModel) getModel();
		int cols[] = { Column.SOLD_QTY.ordinal(), Column.BOUGHT_QTY.ordinal(), Column.NET_QTY.ordinal() };
		for (int col : cols) {
			for (int row = 0; row < model.getRowCount(); row++) {
				model.fireTableCellUpdated(row, col);
			}
		}
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

		columnModel.getColumn(Column.PPU_BUY.ordinal()).setMaxWidth(75);
		columnModel.getColumn(Column.PPU_SELL.ordinal()).setMaxWidth(75);

		//define column groups
		List<ColumnGroup> columnGroups = new ArrayList<>();

		ColumnGroup columnGroup = new ColumnGroup("Avg PPU");
		columnGroup.add(columnModel.getColumn(Column.PPU_BUY.ordinal()));
		columnGroup.add(columnModel.getColumn(Column.PPU_SELL.ordinal()));
		columnGroups.add(columnGroup);

		columnGroup = new ColumnGroup("Sold");
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

			@Override
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
