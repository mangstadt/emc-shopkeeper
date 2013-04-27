package emcshop.gui;

import java.awt.Color;
import java.awt.Component;
import java.text.NumberFormat;
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

/**
 * A table that displays transactions grouped by item.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ItemsTable extends GroupableColumnsTable {
	private static final int COL_ITEMNAME = 0;
	private static final int COL_SOLD_QTY = 1;
	private static final int COL_SOLD_AMT = 2;
	private static final int COL_BOUGHT_QTY = 3;
	private static final int COL_BOUGHT_AMT = 4;
	private static final int COL_NET_QTY = 5;
	private static final int COL_NET_AMT = 6;
	private static final String[] columnNames = new String[7];
	{
		columnNames[COL_ITEMNAME] = "Item Name";
		columnNames[COL_SOLD_QTY] = "Qty";
		columnNames[COL_SOLD_AMT] = "Rupees";
		columnNames[COL_BOUGHT_QTY] = "Qty";
		columnNames[COL_BOUGHT_AMT] = "Rupees";
		columnNames[COL_NET_QTY] = "Qty";
		columnNames[COL_NET_AMT] = "Rupees";
	}

	/**
	 * Creates the table.
	 * @param itemGroupsList the items to display in the table
	 */
	public ItemsTable(final List<ItemGroup> itemGroupsList) {
		setColumnSelectionAllowed(false);
		setRowSelectionAllowed(false);
		setCellSelectionEnabled(false);
		setRowHeight(24);

		setModel(new AbstractTableModel() {
			@Override
			public int getColumnCount() {
				return columnNames.length;
			}

			@Override
			public String getColumnName(int col) {
				return columnNames[col];
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
			private final NumberFormat nf = NumberFormat.getNumberInstance();
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
				JLabel label = null;

				StringBuilder sb;
				String netColor;
				ItemGroup group = (ItemGroup) value;
				switch (col) {
				case COL_ITEMNAME:
					ImageIcon img = ImageManager.getItemImage(group.getItem());
					label = new JLabel(group.getItem(), img, SwingConstants.LEFT);
					break;
				case COL_SOLD_QTY:
					if (group.getSoldQuantity() == 0) {
						label = new JLabel("-", SwingConstants.CENTER);
					} else {
						label = new JLabel(formatQuantity(group.getSoldQuantity()));
					}
					break;
				case COL_SOLD_AMT:
					if (group.getSoldQuantity() == 0) {
						label = new JLabel("-", SwingConstants.CENTER);
					} else {
						label = new JLabel(formatRupees(group.getSoldAmount()));
					}
					break;
				case COL_BOUGHT_QTY:
					if (group.getBoughtQuantity() == 0) {
						label = new JLabel("-", SwingConstants.CENTER);
					} else {
						label = new JLabel(formatQuantity(group.getBoughtQuantity()));
					}
					break;
				case COL_BOUGHT_AMT:
					if (group.getBoughtQuantity() == 0) {
						label = new JLabel("-", SwingConstants.CENTER);
					} else {
						label = new JLabel(formatRupees(group.getBoughtAmount()));
					}
					break;
				case COL_NET_QTY:
					sb = new StringBuilder();
					sb.append("<html>");

					netColor = getNetColor(group.getNetQuantity());
					if (netColor != null) {
						sb.append("<font color=").append(netColor).append(">");
						sb.append(formatQuantity(group.getNetQuantity()));
						sb.append("</font>");
					} else {
						sb.append(formatQuantity(group.getNetQuantity()));
					}

					sb.append("</html>");

					label = new JLabel(sb.toString());
					break;
				case COL_NET_AMT:
					sb = new StringBuilder();
					sb.append("<html>");

					netColor = getNetColor(group.getNetAmount());
					if (netColor != null) {
						sb.append("<font color=").append(netColor).append(">");
						sb.append(formatRupees(group.getNetAmount()));
						sb.append("</font>");
					} else {
						sb.append(formatRupees(group.getNetAmount()));
					}

					sb.append("</html>");

					label = new JLabel(sb.toString());
					break;
				}

				//set the background color of the row
				Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
				label.setOpaque(true);
				label.setBackground(color);

				return label;
			}

			/**
			 * Gets the font color to use for the "net" column.
			 * @param number the number (e.g. rupees or quantity)
			 * @return the color or null if the number is zero
			 */
			private String getNetColor(int number) {
				if (number < 0) {
					return "red";
				}
				if (number > 0) {
					return "green";
				}
				return null;
			}

			/**
			 * Formats a rupee amount as a string.
			 * @param rupees the amount of rupees
			 * @return the rupee string
			 */
			private String formatRupees(int rupees) {
				return formatQuantity(rupees) + "r";
			}

			/**
			 * Formats a quantity as a string
			 * @param quantity the quantity
			 * @return the quantity string
			 */
			private String formatQuantity(int quantity) {
				StringBuilder sb = new StringBuilder();
				if (quantity > 0) {
					sb.append('+');
				}
				sb.append(nf.format(quantity));
				return sb.toString();
			}
		});

		//set the width of "item name" column so the name isn't snipped
		columnModel.getColumn(COL_ITEMNAME).setMinWidth(200);

		//set the column groupings
		{
			List<ColumnGroup> columnGroups = new ArrayList<ColumnGroup>();

			ColumnGroup columnGroup = new ColumnGroup("Sold");
			columnGroup.add(columnModel.getColumn(COL_SOLD_QTY));
			columnGroup.add(columnModel.getColumn(COL_SOLD_AMT));
			columnGroups.add(columnGroup);

			columnGroup = new ColumnGroup("Bought");
			columnGroup.add(columnModel.getColumn(COL_BOUGHT_QTY));
			columnGroup.add(columnModel.getColumn(COL_BOUGHT_AMT));
			columnGroups.add(columnGroup);

			columnGroup = new ColumnGroup("Net");
			columnGroup.add(columnModel.getColumn(COL_NET_QTY));
			columnGroup.add(columnModel.getColumn(COL_NET_AMT));
			columnGroups.add(columnGroup);

			setColumnGroups(columnGroups);
		}
	}
}
