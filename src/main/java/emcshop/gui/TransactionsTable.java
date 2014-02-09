package emcshop.gui;

import static emcshop.util.NumberFormatter.formatQuantityWithColor;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;
import static emcshop.util.NumberFormatter.formatStacksWithColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import emcshop.ItemIndex;
import emcshop.gui.ProfileImageLoader.ImageDownloadedListener;
import emcshop.gui.images.ImageManager;
import emcshop.scraper.ShopTransaction;
import emcshop.util.FilterList;

/**
 * A table that displays transactions by date
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class TransactionsTable extends JTable {
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

	private final List<ShopTransaction> transactions;
	private List<ShopTransaction> transactionsToDisplay;

	private Column prevColumnClicked;
	private boolean ascending, showQuantitiesInStacks;
	private FilterList filteredPlayerNames = new FilterList();
	private FilterList filteredItemNames = new FilterList();

	public TransactionsTable(List<ShopTransaction> transactions, boolean showQuantitiesInStacks, ProfileImageLoader profileImageLoader) {
		this(transactions, Column.TS, false, showQuantitiesInStacks, profileImageLoader);
	}

	/**
	 * @param transactions the transactions to display
	 * @param sortedBy the column that the items list is already sorted by
	 * @param sortedByAscending true if the items list is sorted ascending,
	 * false if descending
	 */
	public TransactionsTable(List<ShopTransaction> transactions, Column sortedBy, boolean sortedByAscending, boolean showQuantitiesInStacks, final ProfileImageLoader profileImageLoader) {
		this.transactions = transactions;
		this.transactionsToDisplay = transactions;
		prevColumnClicked = sortedBy;
		ascending = sortedByAscending;
		this.showQuantitiesInStacks = showQuantitiesInStacks;

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
			private final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
			private final DateFormat tf = DateFormat.getTimeInstance(DateFormat.SHORT);
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);
			private final Column[] columns = Column.values();
			private final ItemIndex index = ItemIndex.instance();
			private final Calendar today = Calendar.getInstance();

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
				ShopTransaction transaction = (ShopTransaction) value;
				Column column = columns[col];

				JLabel label = new JLabel();

				ImageIcon image = getIcon(transaction, row, column, label);
				String text = getText(transaction, column);
				int alignment = SwingConstants.LEFT;
				if (text == null) {
					text = "-";
					alignment = SwingConstants.CENTER;
				}

				if (image != null) {
					label.setIcon(image);
				}
				label.setText(text);
				label.setHorizontalAlignment(alignment);

				//set the background color of the row
				Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
				label.setOpaque(true);
				label.setBackground(color);

				return label;
			}

			private ImageIcon getIcon(ShopTransaction transaction, final int row, final Column column, JLabel label) {
				switch (column) {
				case ITEM_NAME:
					return ImageManager.getItemImage(transaction.getItem());
				case PLAYER_NAME:
					profileImageLoader.load(transaction.getPlayer(), label, 16, new ImageDownloadedListener() {
						@Override
						public void onImageDownloaded(JLabel label) {
							AbstractTableModel model = (AbstractTableModel) getModel();
							model.fireTableCellUpdated(row, column.ordinal());
						}
					});
					return null;
				default:
					return null;
				}
			}

			public String getText(ShopTransaction transaction, Column column) {
				switch (column) {
				case TS:
					Date ts = transaction.getTs();
					if (daysAgo(ts) == 0) {
						return "Today at " + tf.format(ts);
					}
					if (daysAgo(ts) == 1) {
						return "Yesterday at " + tf.format(ts);
					}
					return df.format(ts);
				case PLAYER_NAME:
					return transaction.getPlayer();
				case ITEM_NAME:
					return transaction.getItem();
				case QUANTITY:
					int quantity = transaction.getQuantity();
					return "<html>" + (TransactionsTable.this.showQuantitiesInStacks ? formatStacksWithColor(quantity, index.getStackSize(transaction.getItem())) : formatQuantityWithColor(quantity)) + "</html>";
				case AMOUNT:
					return "<html>" + formatRupeesWithColor(transaction.getAmount()) + "</html>";
				default:
					return null;
				}
			}

			private int daysAgo(Date date) {
				Calendar c = Calendar.getInstance();
				c.setTime(date);

				return ((today.get(Calendar.YEAR) - c.get(Calendar.YEAR)) * 365) + (today.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR));
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
				String playerName = transaction.getPlayer();
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
			players.add(transaction.getPlayer());
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
					return a.getPlayer().compareToIgnoreCase(b.getPlayer());
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

				String text = column.getName();
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
