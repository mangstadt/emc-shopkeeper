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

	/**
	 * Creates the table.
	 * @param itemGroupsList the items to display in the table
	 */
	public ItemsTable(final List<ItemGroup> itemGroupsList) {
		this(itemGroupsList, null);
	}

	/**
	 * Creates the table.
	 * @param itemGroupsList the items to display in the table
	 */
	public ItemsTable(final List<ItemGroup> itemGroupsList, final ColumnClickHandler handler) {
		setColumnSelectionAllowed(false);
		setRowSelectionAllowed(false);
		setCellSelectionEnabled(false);
		setRowHeight(24);

		if (handler != null) {
			getTableHeader().addMouseListener(new MouseAdapter() {
				private final Column columns[] = Column.values();

				@Override
				public void mouseClicked(MouseEvent e) {
					int index = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
					if (index < 0) {
						return;
					}

					Column column = columns[index];
					handler.onClick(column);
				}
			});
		}

		setModel(new AbstractTableModel() {
			private final Column columns[] = Column.values();

			@Override
			public int getColumnCount() {
				return columns.length;
			}

			@Override
			public String getColumnName(int col) {
				return columns[col].getName();
			}

			@Override
			public int getRowCount() {
				return itemGroupsList.size();
			}

			@Override
			public Object getValueAt(int row, int col) {
				return itemGroupsList.get(row);
			}

			public Class<?> getColumnClass(int c) {
				return ItemGroup.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				return false;
			}
		});

		setDefaultRenderer(ItemGroup.class, new TableCellRenderer() {
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
				JLabel label = null;

				StringBuilder sb;
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
					sb = new StringBuilder();
					sb.append("<html>");
					sb.append(formatQuantityWithColor(group.getNetQuantity()));
					sb.append("</html>");

					label = new JLabel(sb.toString());
				} else if (col == Column.NET_AMT.ordinal()) {
					sb = new StringBuilder();
					sb.append("<html>");
					sb.append(formatRupeesWithColor(group.getNetAmount()));
					sb.append("</html>");

					label = new JLabel(sb.toString());
				}

				//set the background color of the row
				Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
				label.setOpaque(true);
				label.setBackground(color);

				return label;
			}
		});

		//set the width of "item name" column so the name isn't snipped
		columnModel.getColumn(Column.ITEM_NAME.ordinal()).setMinWidth(150);

		//set the column groupings
		{
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
	}

	public static interface ColumnClickHandler {
		/**
		 * Called when a column's header is clicked.
		 * @param column the column that was clicked
		 */
		void onClick(Column column);
	}
}
