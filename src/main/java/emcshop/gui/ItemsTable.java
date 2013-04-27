package emcshop.gui;

import java.awt.Color;
import java.awt.Component;
import java.text.NumberFormat;
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
public class ItemsTable extends JTable {
	private static final int COL_ITEMNAME = 0;
	private static final int COL_SOLD = 1;
	private static final int COL_BOUGHT = 2;
	private static final int COL_NET = 3;
	private static final String[] columnNames = new String[4];
	{
		columnNames[COL_ITEMNAME] = "Item Name";
		columnNames[COL_SOLD] = "Sold";
		columnNames[COL_BOUGHT] = "Bought";
		columnNames[COL_NET] = "Net";
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

				ItemGroup group = (ItemGroup) value;
				switch (col) {
				case COL_ITEMNAME:
					ImageIcon img = ImageManager.getItemImage(group.getItem());
					label = new JLabel(group.getItem(), img, SwingConstants.LEFT);
					break;
				case COL_SOLD:
					if (group.getSoldQuantity() == 0) {
						label = new JLabel("-");
					} else {
						label = new JLabel(nf.format(group.getSoldQuantity()) + " / " + nf.format(group.getSoldAmount()) + "r");
					}
					break;
				case COL_BOUGHT:
					if (group.getBoughtQuantity() == 0) {
						label = new JLabel("-");
					} else {
						label = new JLabel(nf.format(group.getBoughtQuantity()) + " / " + nf.format(group.getBoughtAmount()) + "r");
					}
					break;
				case COL_NET:
					StringBuilder sb = new StringBuilder();
					sb.append("<html>");

					if (group.getNetQuantity() < 0) {
						sb.append("<font color=red>");
						sb.append(nf.format(group.getNetQuantity()));
						sb.append("</font>");
					} else {
						sb.append("<font color=green>");
						sb.append('+').append(nf.format(group.getNetQuantity()));
						sb.append("</font>");
					}

					sb.append(" / ");

					if (group.getNetAmount() < 0) {
						sb.append("<font color=red>");
						sb.append(nf.format(group.getNetAmount())).append('r');
						sb.append("</font>");
					} else {
						sb.append("<font color=green>");
						sb.append('+').append(nf.format(group.getNetAmount())).append('r');
						sb.append("</font>");
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
		});
	}
}
