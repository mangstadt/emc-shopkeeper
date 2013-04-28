package emcshop.gui;

import java.awt.Color;
import java.awt.Component;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;
import emcshop.db.PlayerGroup;
import emcshop.gui.images.ImageManager;

/**
 * A panel that displays transactions grouped by player.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class PlayersPanel extends JPanel {
	private final List<PlayerGroup> playerGroups;

	/**
	 * Creates the panel.
	 * @param playerGroups the players to display in the table
	 */
	public PlayersPanel(List<PlayerGroup> playerGroups) {
		this.playerGroups = playerGroups;
		setLayout(new MigLayout());
		sortByPlayerName();
	}

	public void sortByPlayerName() {
		//sort by player name
		Collections.sort(playerGroups, new Comparator<PlayerGroup>() {
			@Override
			public int compare(PlayerGroup a, PlayerGroup b) {
				return a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
			}
		});

		//sort each player's item list by item name
		for (PlayerGroup group : playerGroups) {
			Collections.sort(group.getItems(), new Comparator<PlayerGroup.ItemInfo>() {
				@Override
				public int compare(PlayerGroup.ItemInfo a, PlayerGroup.ItemInfo b) {
					return a.getItem().compareToIgnoreCase(b.getItem());
				}
			});
		}

		refresh();
	}

	public void sortBySuppliers() {
		//sort by net sold amount ascending
		Collections.sort(playerGroups, new Comparator<PlayerGroup>() {
			@Override
			public int compare(PlayerGroup a, PlayerGroup b) {
				return a.getNetSoldAmount() - b.getNetSoldAmount();
			}
		});

		//sort each player's item list by item amount ascending
		for (PlayerGroup group : playerGroups) {
			Collections.sort(group.getItems(), new Comparator<PlayerGroup.ItemInfo>() {
				@Override
				public int compare(PlayerGroup.ItemInfo a, PlayerGroup.ItemInfo b) {
					return a.getAmount() - b.getAmount();
				}
			});
		}

		refresh();
	}

	public void sortByCustomers() {
		//sort by net bought amount descending
		Collections.sort(playerGroups, new Comparator<PlayerGroup>() {
			@Override
			public int compare(PlayerGroup a, PlayerGroup b) {
				return b.getNetBoughtAmount() - a.getNetBoughtAmount();
			}
		});

		//sort each player's item list by item amount descending
		for (PlayerGroup group : playerGroups) {
			Collections.sort(group.getItems(), new Comparator<PlayerGroup.ItemInfo>() {
				@Override
				public int compare(PlayerGroup.ItemInfo a, PlayerGroup.ItemInfo b) {
					return b.getAmount() - a.getAmount();
				}
			});
		}

		refresh();
	}

	private void refresh() {
		removeAll();

		DateFormat df = new SimpleDateFormat("MMMM dd yyyy, HH:mm");
		for (PlayerGroup playerGroup : playerGroups) {
			add(new JLabel("<html><h3>" + playerGroup.getPlayerName() + "</h3></html>"), "span 2, wrap");

			add(new JLabel("<html>First seen:</html>"), "align right");
			add(new JLabel("<html>" + df.format(playerGroup.getFirstSeen()) + "</html>"), "wrap");

			add(new JLabel("<html>Last seen:</html>"), "align right");
			add(new JLabel("<html>" + df.format(playerGroup.getLastSeen()) + "</html>"), "wrap");

			PlayerTable table = new PlayerTable(playerGroup);
			table.getTableHeader().setReorderingAllowed(false);
			if (playerGroup.getItems().size() > 15) {
				table.setFillsViewportHeight(true);
				JScrollPane tableScrollPane = new JScrollPane(table);
				add(tableScrollPane, "h 360!, span 2, wrap");
			} else {
				add(table.getTableHeader(), "span 2, wrap");
				add(table, "span 2, wrap");
			}

			JLabel netAmount;
			{
				int amount = playerGroup.getNetAmount();
				String color = getNetColor(amount);

				StringBuilder sb = new StringBuilder();
				sb.append("<html><code><b>");

				if (color != null) {
					sb.append("<font color=").append(color).append(">");
					sb.append(formatRupees(amount));
					sb.append("</font>");
				} else {
					sb.append(formatRupees(amount));
				}

				sb.append("</b></code></html>");
				netAmount = new JLabel(sb.toString());
			}
			add(netAmount, "align right, span 2, wrap");
		}

		validate();
	}

	private static class PlayerTable extends JTable {
		private static final int COL_ITEMNAME = 0;
		private static final int COL_QTY = 1;
		private static final int COL_AMT = 2;
		private static final String[] columnNames = new String[3];
		{
			columnNames[COL_ITEMNAME] = "Item Name";
			columnNames[COL_QTY] = "Qty";
			columnNames[COL_AMT] = "Rupees";
		}

		public PlayerTable(final PlayerGroup playerGroup) {
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
					return playerGroup.getItems().size();
				}

				@Override
				public Object getValueAt(int row, int col) {
					return playerGroup.getItems().get(row);
				}

				public Class<?> getColumnClass(int c) {
					return PlayerGroup.ItemInfo.class;
				}

				@Override
				public boolean isCellEditable(int row, int col) {
					return false;
				}
			});

			setDefaultRenderer(PlayerGroup.ItemInfo.class, new TableCellRenderer() {

				private final Color evenRowColor = new Color(255, 255, 255);
				private final Color oddRowColor = new Color(240, 240, 240);

				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
					JLabel label = null;

					StringBuilder sb;
					String netColor;
					PlayerGroup.ItemInfo item = (PlayerGroup.ItemInfo) value;
					switch (col) {
					case COL_ITEMNAME:
						ImageIcon img = ImageManager.getItemImage(item.getItem());
						label = new JLabel(item.getItem(), img, SwingConstants.LEFT);
						break;
					case COL_QTY:
						label = new JLabel(formatQuantity(item.getQuantity()));
						break;
					case COL_AMT:
						sb = new StringBuilder();
						sb.append("<html>");

						netColor = getNetColor(item.getAmount());
						if (netColor != null) {
							sb.append("<font color=").append(netColor).append(">");
							sb.append(formatRupees(item.getAmount()));
							sb.append("</font>");
						} else {
							sb.append(formatRupees(item.getAmount()));
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

			//set the width of "item name" column so the name isn't snipped
			columnModel.getColumn(COL_ITEMNAME).setMinWidth(175);
			columnModel.getColumn(COL_ITEMNAME).setMaxWidth(175);
			columnModel.getColumn(COL_ITEMNAME).setPreferredWidth(175);

			columnModel.getColumn(COL_QTY).setMinWidth(100);
			columnModel.getColumn(COL_QTY).setMaxWidth(100);
			columnModel.getColumn(COL_QTY).setPreferredWidth(100);

			columnModel.getColumn(COL_AMT).setMinWidth(100);
			columnModel.getColumn(COL_AMT).setMaxWidth(100);
			columnModel.getColumn(COL_AMT).setPreferredWidth(100);
		}
	}

	/**
	 * Gets the font color to use for the "net" column.
	 * @param number the number (e.g. rupees or quantity)
	 * @return the color or null if the number is zero
	 */
	private static String getNetColor(int number) {
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
	private static String formatRupees(int rupees) {
		return formatQuantity(rupees) + "r";
	}

	/**
	 * Formats a quantity as a string
	 * @param quantity the quantity
	 * @return the quantity string
	 */
	private static String formatQuantity(int quantity) {
		NumberFormat nf = NumberFormat.getNumberInstance();
		StringBuilder sb = new StringBuilder();
		if (quantity > 0) {
			sb.append('+');
		}
		sb.append(nf.format(quantity));
		return sb.toString();
	}
}
