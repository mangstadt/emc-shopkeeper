package emcshop.gui;

import static emcshop.util.NumberFormatter.formatQuantityWithColor;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;
import static emcshop.util.NumberFormatter.formatStacksWithColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;
import emcshop.AppContext;
import emcshop.ItemIndex;
import emcshop.Settings;
import emcshop.gui.FilterPanel.FilterList;
import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.gui.images.ImageManager;
import emcshop.scraper.EmcServer;
import emcshop.scraper.ShopTransaction;
import emcshop.util.RelativeDateFormat;

/**
 * A table that displays transactions by date
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class TransactionsTable extends JTable {
	private static final AppContext context = AppContext.instance();

	/**
	 * Defines all of the columns in this table. The order in which the enums
	 * are defined is the order that they will appear in the table.
	 */
	public static enum Column {
		TS("Date"), PLAYER_NAME("Player"), ITEM_NAME("Item"), QUANTITY("Quantity"), AMOUNT("Amount");

		private final String name;

		private Column(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	private final Column[] columns = Column.values();
	private final List<ShopTransaction> transactions;
	private List<ShopTransaction> transactionsToDisplay;
	private final boolean customers;

	private Column prevColumnClicked;
	private boolean ascending, showQuantitiesInStacks;
	private FilterList filteredPlayerNames = new FilterList();
	private FilterList filteredItemNames = new FilterList();

	public TransactionsTable(List<ShopTransaction> transactions, boolean customers) {
		this(transactions, customers, Column.TS, false);
	}

	/**
	 * @param transactions the transactions to display
	 * @param sortedBy the column that the items list is already sorted by
	 * @param sortedByAscending true if the items list is sorted ascending,
	 * false if descending
	 */
	public TransactionsTable(List<ShopTransaction> transactions, final boolean customers, Column sortedBy, boolean sortedByAscending) {
		this.transactions = transactions;
		this.transactionsToDisplay = transactions;
		this.customers = customers;
		prevColumnClicked = sortedBy;
		ascending = sortedByAscending;

		showQuantitiesInStacks = context.get(Settings.class).isShowQuantitiesInStacks();

		sortData();

		getTableHeader().setReorderingAllowed(false);
		setColumnSelectionAllowed(false);
		setRowSelectionAllowed(false);
		setCellSelectionEnabled(false);
		setRowHeight(24);

		getTableHeader().addMouseListener(new MouseAdapter() {
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
					if (column == Column.AMOUNT || column == Column.TS) {
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

		setDefaultRenderer(ShopTransaction.class, new TableCellRenderer() {
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);
			private final ItemIndex index = ItemIndex.instance();
			private final RelativeDateFormat df = new RelativeDateFormat();
			private final ProfileLoader profileLoader = context.get(ProfileLoader.class);
			private final OnlinePlayersMonitor onlinePlayersMonitor = context.get(OnlinePlayersMonitor.class);

			private final JLabel label = new JLabel();
			{
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(4, 4, 4, 4));
			}

			private final JLabel playerLabel = new JLabel();
			private final JLabel serverLabel = new JLabel();
			private final JPanel playerPanel = new JPanel(new MigLayout("insets 2"));
			{
				playerPanel.setOpaque(true);
				playerPanel.add(playerLabel);
				playerPanel.add(serverLabel);
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, final int row, final int col) {
				if (value == null) {
					return null;
				}

				ShopTransaction transaction = (ShopTransaction) value;
				Column column = columns[col];
				resetComponents();

				JComponent component = null;
				switch (column) {
				case TS:
					component = label;

					Date ts = transaction.getTs();
					label.setText(df.format(ts));
					break;

				case PLAYER_NAME:
					component = playerPanel;

					String playerName = customers ? transaction.getPlayer() : transaction.getShopOwner();
					playerLabel.setText(playerName);

					ImageIcon portrait = profileLoader.getPortraitFromCache(playerName);
					if (portrait == null) {
						portrait = ImageManager.getUnknown();
					}
					portrait = ImageManager.scale(portrait, 16);
					playerLabel.setIcon(portrait);

					if (!profileLoader.wasDownloaded(playerName)) {
						profileLoader.queueProfileForDownload(playerName, new ProfileDownloadedListener() {
							@Override
							public void onProfileDownloaded(JLabel label) {
								//re-render the cell when the profile is downloaded
								AbstractTableModel model = (AbstractTableModel) getModel();
								model.fireTableCellUpdated(row, col);
							}
						});
					}

					EmcServer server = onlinePlayersMonitor.getPlayerServer(playerName);
					if (server != null) {
						serverLabel.setIcon(ImageManager.getOnline(server, 12));
					}
					break;

				case ITEM_NAME:
					component = label;

					String name = transaction.getItem();
					label.setText(name);

					ImageIcon icon = ImageManager.getItemImage(transaction.getItem());
					label.setIcon(icon);
					break;

				case QUANTITY:
					component = label;

					String text;
					int quantity = transaction.getQuantity();
					if (showQuantitiesInStacks) {
						int stackSize = index.getStackSize(transaction.getItem());
						text = formatStacksWithColor(quantity, stackSize);
					} else {
						text = formatQuantityWithColor(quantity);
					}
					label.setText("<html>" + text + "</html>");
					break;

				case AMOUNT:
					component = label;

					label.setText("<html>" + formatRupeesWithColor(transaction.getAmount()) + "</html>");
					break;
				}

				Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
				component.setBackground(color);

				return component;
			}

			private void resetComponents() {
				label.setIcon(null);
				serverLabel.setIcon(null);
			}
		});

		setColumns();
	}

	public void filterByItem(FilterList filterList) {
		filteredItemNames = filterList;
		filter();
	}

	public void filterByPlayers(FilterList filterList) {
		filteredPlayerNames = filterList;
		filter();
	}

	private void filter() {
		if (filteredItemNames.isEmpty() && filteredPlayerNames.isEmpty()) {
			transactionsToDisplay = transactions;
			sortData();
		} else {
			transactionsToDisplay = new ArrayList<ShopTransaction>();
			for (ShopTransaction transaction : transactions) {
				String itemName = transaction.getItem();
				String playerName = customers ? transaction.getPlayer() : transaction.getShopOwner();
				if ((filteredItemNames.isEmpty() || filteredItemNames.contains(itemName)) && (filteredPlayerNames.isEmpty() || filteredPlayerNames.contains(playerName))) {
					transactionsToDisplay.add(transaction);
				}
			}
		}

		redraw();
	}

	public List<ShopTransaction> getDisplayedTransactions() {
		return transactionsToDisplay;
	}

	public List<String> getDisplayedPlayers() {
		Set<String> players = new LinkedHashSet<String>();
		for (ShopTransaction transaction : transactionsToDisplay) {
			String player = customers ? transaction.getPlayer() : transaction.getShopOwner();
			players.add(player);
		}
		return new ArrayList<String>(players);
	}

	private void sortData() {
		Collections.sort(transactionsToDisplay, new Comparator<ShopTransaction>() {
			@Override
			public int compare(ShopTransaction a, ShopTransaction b) {
				if (!ascending) {
					ShopTransaction temp = a;
					a = b;
					b = temp;
				}

				switch (prevColumnClicked) {
				case TS:
					return a.getTs().compareTo(b.getTs());
				case PLAYER_NAME:
					if (customers) {
						return a.getPlayer().compareToIgnoreCase(b.getPlayer());
					} else {
						return a.getShopOwner().compareToIgnoreCase(b.getShopOwner());
					}
				case ITEM_NAME:
					return a.getItem().compareToIgnoreCase(b.getItem());
				case AMOUNT:
					return a.getAmount() - b.getAmount();
				case QUANTITY:
					return a.getQuantity() - b.getQuantity();
				}
				return 0;
			}
		});
	}

	public void setShowQuantitiesInStacks(boolean enable) {
		showQuantitiesInStacks = enable;

		//re-render the "quantity" column
		AbstractTableModel model = (AbstractTableModel) getModel();
		int col = Column.QUANTITY.ordinal();
		for (int row = 0; row < model.getRowCount(); row++) {
			model.fireTableCellUpdated(row, col);
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
		//columnModel.getColumn(Column.ITEM_NAME.ordinal()).setMinWidth(200);
		//columnModel.getColumn(Column.PLAYER_NAME.ordinal()).setMinWidth(200);
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

				String text;
				if (column == Column.PLAYER_NAME && !customers) {
					text = "Shop Owner";
				} else {
					text = column.getName();
				}

				if (prevColumnClicked == column) {
					String arrow = (ascending) ? "\u25bc" : "\u25b2";
					text = arrow + " " + text;
				}

				return text;
			}

			@Override
			public int getRowCount() {
				return transactionsToDisplay.size();
			}

			@Override
			public Object getValueAt(int row, int col) {
				return transactionsToDisplay.get(row);
			}

			@Override
			public Class<?> getColumnClass(int c) {
				return ShopTransaction.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				return false;
			}
		});
	}
}
