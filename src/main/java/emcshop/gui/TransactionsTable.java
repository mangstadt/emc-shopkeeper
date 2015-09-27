package emcshop.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import emcshop.AppContext;
import emcshop.ItemIndex;
import emcshop.Settings;
import emcshop.db.ShopTransactionDb;
import emcshop.db.ShopTransactionType;
import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.gui.images.Images;
import emcshop.scraper.PlayerProfile;
import emcshop.util.QuantityFormatter;
import emcshop.util.RelativeDateFormat;
import emcshop.util.RupeeFormatter;
import emcshop.util.UIDefaultsWrapper;

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
	private final Model model;
	private final TableRowSorter<Model> rowSorter;
	private final ShopTransactionType transactionType;

	private boolean showQuantitiesInStacks;
	private FilterList filteredPlayerNames = new FilterList();
	private FilterList filteredItemNames = new FilterList();

	public TransactionsTable(List<ShopTransactionDb> transactions, ShopTransactionType transactionType) {
		this.transactionType = transactionType;
		this.showQuantitiesInStacks = context.get(Settings.class).isShowQuantitiesInStacks();

		setRowHeight(24);
		setDefaultRenderer(ShopTransactionDb.class, new Renderer());

		model = new Model(transactions);
		setModel(model);

		rowSorter = createRowSorter();
		setRowSorter(rowSorter);

		setColumns();

		setSelectionModel();
	}

	public List<ShopTransactionDb> getDisplayedTransactions() {
		List<ShopTransactionDb> transactions = new ArrayList<ShopTransactionDb>(getRowCount());
		for (int row = 0; row < getRowCount(); row++) {
			int rowModel = convertRowIndexToModel(row);
			ShopTransactionDb transaction = model.transactions.get(rowModel);
			transactions.add(transaction);
		}
		return transactions;
	}

	public int getDisplayedPlayersCount() {
		Set<String> players = new HashSet<String>();
		for (ShopTransactionDb transaction : getDisplayedTransactions()) {
			String player = getPlayerName(transaction);
			players.add(player);
		}
		return players.size();
	}

	private TableColumn getTableColumn(Column column) {
		return columnModel.getColumn(column.ordinal());
	}

	private void setColumns() {
		getTableHeader().setReorderingAllowed(false);

		TableColumn tsColumn = getTableColumn(Column.TS);
		tsColumn.setPreferredWidth(200);
		tsColumn.setMinWidth(150);

		TableColumn playerNameColumn = getTableColumn(Column.PLAYER_NAME);
		playerNameColumn.setPreferredWidth(300);

		TableColumn itemNameColumn = getTableColumn(Column.ITEM_NAME);
		itemNameColumn.setPreferredWidth(300);

		TableColumn quantityColumn = getTableColumn(Column.QUANTITY);
		quantityColumn.setPreferredWidth(150);
		quantityColumn.setMinWidth(100);

		TableColumn amountColumn = getTableColumn(Column.AMOUNT);
		amountColumn.setPreferredWidth(150);
		amountColumn.setMinWidth(100);
	}

	private void setSelectionModel() {
		setColumnSelectionAllowed(false);
		setRowSelectionAllowed(false);
		setCellSelectionEnabled(false);
	}

	private TableRowSorter<Model> createRowSorter() {
		TableRowSorter<Model> rowSorter = new TableRowSorter<Model>(model);

		rowSorter.setComparator(Column.TS.ordinal(), new Comparator<ShopTransactionDb>() {
			@Override
			public int compare(ShopTransactionDb one, ShopTransactionDb two) {
				return one.getTs().compareTo(two.getTs());
			}
		});
		rowSorter.setComparator(Column.PLAYER_NAME.ordinal(), new Comparator<ShopTransactionDb>() {
			@Override
			public int compare(ShopTransactionDb one, ShopTransactionDb two) {
				String name1 = getPlayerName(one);
				String name2 = getPlayerName(two);
				return name1.compareToIgnoreCase(name2);
			}
		});
		rowSorter.setComparator(Column.ITEM_NAME.ordinal(), new Comparator<ShopTransactionDb>() {
			@Override
			public int compare(ShopTransactionDb one, ShopTransactionDb two) {
				return one.getItem().compareToIgnoreCase(two.getItem());
			}
		});
		rowSorter.setComparator(Column.QUANTITY.ordinal(), new Comparator<ShopTransactionDb>() {
			@Override
			public int compare(ShopTransactionDb one, ShopTransactionDb two) {
				return one.getQuantity() - two.getQuantity();
			}
		});
		rowSorter.setComparator(Column.AMOUNT.ordinal(), new Comparator<ShopTransactionDb>() {
			@Override
			public int compare(ShopTransactionDb one, ShopTransactionDb two) {
				return one.getAmount() - two.getAmount();
			}
		});
		rowSorter.setSortsOnUpdates(true);
		rowSorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(Column.TS.ordinal(), SortOrder.DESCENDING)));

		return rowSorter;
	}

	private String getPlayerName(ShopTransactionDb transaction) {
		switch (transactionType) {
		case MY_SHOP:
			return transaction.getShopCustomer();
		case OTHER_SHOPS:
			return transaction.getShopOwner();
		default:
			String name = transaction.getShopCustomer();
			if (name != null) {
				return name;
			}
			return transaction.getShopOwner();
		}
	}

	public void filterByItem(FilterList filterList) {
		filteredItemNames = filterList;
		filter();
	}

	public void filterByPlayers(FilterList filterList) {
		filteredPlayerNames = filterList;
		filter();
	}

	public void filter() {
		if (filteredItemNames.isEmpty() && filteredPlayerNames.isEmpty()) {
			rowSorter.setRowFilter(null);
			return;
		}

		RowFilter<Model, Integer> filter = new RowFilter<Model, Integer>() {
			@Override
			public boolean include(RowFilter.Entry<? extends Model, ? extends Integer> entry) {
				int row = entry.getIdentifier();
				ShopTransactionDb transaction = model.transactions.get(row);

				if (!filteredItemNames.isFiltered(transaction.getItem())) {
					return false;
				}

				if (filteredPlayerNames.isEmpty()) {
					return true;
				}

				String name = getPlayerName(transaction);
				return filteredPlayerNames.isFiltered(name);
			}

		};
		rowSorter.setRowFilter(filter);
	}

	public void setShowQuantitiesInStacks(boolean enable) {
		showQuantitiesInStacks = enable;

		//re-render the "quantity" column
		int col = Column.QUANTITY.ordinal();
		for (int row = 0; row < model.getRowCount(); row++) {
			model.fireTableCellUpdated(row, col);
		}
	}

	private class Renderer implements TableCellRenderer {
		private final Color evenRowColor = new Color(255, 255, 255);
		private final Color oddRowColor = new Color(240, 240, 240);
		private final ItemIndex index = ItemIndex.instance();
		private final RelativeDateFormat df = new RelativeDateFormat();

		private final JLabel label = new JLabel();
		{
			label.setOpaque(true);
			label.setBorder(new EmptyBorder(4, 4, 4, 4));
		}

		private final PlayerCellPanel playerPanel = new PlayerCellPanel();

		private final QuantityFormatter qf = new QuantityFormatter();
		{
			qf.setColor(true);
			qf.setPlus(true);
		}
		private final RupeeFormatter rf = new RupeeFormatter();
		{
			rf.setColor(true);
			rf.setPlus(true);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, final int row, final int col) {
			if (value == null) {
				return null;
			}

			final ShopTransactionDb transaction = (ShopTransactionDb) value;
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

				final String playerName = getPlayerName(transaction);
				playerPanel.setPlayer(playerName, new ProfileDownloadedListener() {
					@Override
					public void onProfileDownloaded(PlayerProfile profile) {
						//re-render all cells with this player when the profile is downloaded
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								for (int i = 0; i < model.getRowCount(); i++) {
									ShopTransactionDb transaction = model.transactions.get(i);
									String name = getPlayerName(transaction);
									if (playerName.equalsIgnoreCase(name)) {
										model.fireTableCellUpdated(i, col);
									}
								}
							}
						});
					}
				});
				break;

			case ITEM_NAME:
				component = label;

				String name = transaction.getItem();
				label.setText(name);

				ImageIcon icon = Images.getItemImage(transaction.getItem());
				label.setIcon(icon);
				break;

			case QUANTITY:
				component = label;

				int quantity = transaction.getQuantity();
				int stackSize = showQuantitiesInStacks ? index.getStackSize(transaction.getItem()) : 1;
				label.setText("<html>" + qf.format(quantity, stackSize));
				break;

			case AMOUNT:
				component = label;

				label.setText("<html>" + rf.format(transaction.getAmount()));
				break;
			}

			Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
			component.setBackground(color);

			return component;
		}

		private void resetComponents() {
			label.setIcon(null);
			label.setForeground(UIDefaultsWrapper.getLabelForeground());
			playerPanel.setForeground(UIDefaultsWrapper.getLabelForeground());
		}
	}

	private class Model extends AbstractTableModel {
		private final List<ShopTransactionDb> transactions;

		public Model(List<ShopTransactionDb> transactions) {
			this.transactions = transactions;
		}

		@Override
		public int getColumnCount() {
			return columns.length;
		}

		@Override
		public String getColumnName(int index) {
			Column column = columns[index];

			if (column == Column.PLAYER_NAME && transactionType == ShopTransactionType.OTHER_SHOPS) {
				return "Shop Owner";
			} else {
				return column.getName();
			}
		}

		@Override
		public int getRowCount() {
			return transactions.size();
		}

		@Override
		public Object getValueAt(int row, int col) {
			return transactions.get(row);
		}

		@Override
		public Class<?> getColumnClass(int c) {
			return ShopTransactionDb.class;
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			return false;
		}
	}
}
