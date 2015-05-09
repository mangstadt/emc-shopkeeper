package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.GuiUtils.shrinkFont;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyVetoException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import net.miginfocom.swing.MigLayout;

import com.google.common.collect.ListMultimap;
import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.AppContext;
import emcshop.ExportType;
import emcshop.QueryExporter;
import emcshop.Settings;
import emcshop.db.DbDao;
import emcshop.db.ItemGroup;
import emcshop.db.PlayerGroup;
import emcshop.db.ShopTransactionType;
import emcshop.gui.ExportButton.ExportListener;
import emcshop.gui.images.Images;
import emcshop.gui.lib.GroupPanel;
import emcshop.scraper.ShopTransaction;
import emcshop.util.DateRange;
import emcshop.util.RelativeDateFormat;
import emcshop.util.RupeeFormatter;

@SuppressWarnings("serial")
public class TransactionsTab extends JPanel implements ExportListener {
	private static final AppContext context = AppContext.instance();

	private final DbDao dao = context.get(DbDao.class);
	private final MainFrame owner;

	private final QueryPanel queryPanel;
	private final FilterPanel filterPanel;
	private final JPanel tablePanel;
	private final StatsPanel statsPanel;

	private ItemsTable itemsTable;
	private MyJScrollPane itemsTableScrollPane;

	private PlayersPanel playersPanel;

	private TransactionsTable transactionsTable;
	private MyJScrollPane transactionsTableScrollPane;

	private int netTotal;

	public TransactionsTab(MainFrame owner) {
		this.owner = owner;

		queryPanel = new QueryPanel();
		queryPanel.addSearchListener(new SearchListener() {
			@Override
			public void searchPerformed(DateRange range, SearchType type, ShopTransactionType transactionType) {
				filterPanel.clear();

				switch (type) {
				case ITEMS:
					showItems(range, transactionType);
					break;

				case PLAYERS:
					showPlayers(range, transactionType);
					break;

				case DATES:
					showTransactions(range, transactionType);
					break;
				}
			}
		});

		filterPanel = new FilterPanel(this);
		filterPanel.addFilterListener(new FilterListener() {
			@Override
			public void filterPerformed(FilterList items, FilterList players) {
				filter(items, players);
			}
		});
		filterPanel.addSortListener(new SortListener() {
			@Override
			public void sortPerformed(SortItem sort) {
				sort(sort);
			}
		});

		tablePanel = new JPanel(new MigLayout("w 100%, h 100%, fillx, insets 0"));

		statsPanel = new StatsPanel();

		///////////////////////////////////////

		setLayout(new MigLayout("fillx, insets 5"));

		add(queryPanel, "wrap");
		add(filterPanel, "w 100%, wrap");
		add(tablePanel, "grow, h 100%, wrap");
		add(statsPanel, "align right");

		///////////////////////////////////////

		updateNetTotal();
		updateCustomers();
	}

	private void filter(FilterList items, FilterList players) {
		if (itemsTable != null) {
			itemsTable.filter(items);
			itemsTableScrollPane.scrollToTop();
		}
		if (playersPanel != null) {
			playersPanel.filterByItems(items);
			playersPanel.filterByPlayers(players);
		}
		if (transactionsTable != null) {
			transactionsTable.filterByItem(items);
			transactionsTable.filterByPlayers(players);
			transactionsTableScrollPane.scrollToTop();
		}

		updateNetTotal();
		updateCustomers();
	}

	private void sort(SortItem selected) {
		if (playersPanel == null) {
			return;
		}

		busyCursor(owner, true);
		try {
			switch (selected) {
			case PLAYER:
				playersPanel.sortByPlayerName();
				break;

			case HIGHEST:
				playersPanel.sortByCustomers();
				break;

			case LOWEST:
				playersPanel.sortBySuppliers();
				break;
			}
		} finally {
			busyCursor(owner, false);
		}
	}

	public void clear() {
		queryPanel.reset();

		itemsTable = null;
		itemsTableScrollPane = null;
		playersPanel = null;
		transactionsTable = null;
		transactionsTableScrollPane = null;
		netTotal = 0;

		filterPanel.removeAll();
		tablePanel.removeAll();

		updateNetTotal();
		updateCustomers();

		validate();
		repaint(); //the table was still visible in Linux
	}

	public void updateComplete(boolean showResults, boolean firstUpdate) {
		queryPanel.updateSinceLastUpdateCheckbox();

		if (firstUpdate) {
			queryPanel.updateEntireHistoryCheckbox();
		}

		if (showResults) {
			queryPanel.shopTransactions.doClick();
			queryPanel.showSinceLastUpdate.doClick();
			queryPanel.showItems.doClick();
		}
	}

	public void showItems(final DateRange range, final ShopTransactionType transactionType) {
		owner.startProgress("Querying...");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					final List<ItemGroup> itemGroupsList;
					{
						//query database
						itemGroupsList = new ArrayList<ItemGroup>(dao.getItemGroups(range.getFrom(), range.getTo(), transactionType));

						//sort by item name
						Collections.sort(itemGroupsList, new Comparator<ItemGroup>() {
							@Override
							public int compare(ItemGroup a, ItemGroup b) {
								return a.getItem().compareToIgnoreCase(b.getItem());
							}
						});
					}

					//reset GUI
					filterPanel.removeAll();
					filterPanel.validate();
					tablePanel.removeAll();
					tablePanel.validate();

					playersPanel = null;
					transactionsTable = null;
					transactionsTableScrollPane = null;

					//render filter panel
					filterPanel.setVisible(true, false, false);

					//render table
					itemsTable = new ItemsTable(itemGroupsList, transactionType, context.get(Settings.class).isShowQuantitiesInStacks());
					itemsTable.setFillsViewportHeight(true);
					itemsTableScrollPane = new MyJScrollPane(itemsTable);
					tablePanel.add(itemsTableScrollPane, "grow, w 100%, h 100%, wrap");
					tablePanel.validate();

					updateNetTotal();
					updateCustomers();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				} finally {
					owner.stopProgress();
					validate();
				}
			}
		};
		t.start();
	}

	public void showPlayers(final DateRange range, final ShopTransactionType transactionType) {
		owner.startProgress("Querying...");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					Collection<PlayerGroup> playerGroups = dao.getPlayerGroups(range.getFrom(), range.getTo(), transactionType);

					//reset GUI
					filterPanel.removeAll();
					filterPanel.validate();
					tablePanel.removeAll();
					tablePanel.validate();

					itemsTable = null;
					itemsTableScrollPane = null;
					transactionsTable = null;
					transactionsTableScrollPane = null;

					//render filter panel
					filterPanel.setVisible(true, true, true);

					//render table
					playersPanel = new PlayersPanel(playerGroups, transactionType);
					playersPanel.setShowFirstLastSeen(transactionType != ShopTransactionType.OTHER_SHOPS);
					tablePanel.add(playersPanel, "grow, w 100%, h 100%, wrap");
					tablePanel.validate();

					updateNetTotal();
					updateCustomers();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				} finally {
					owner.stopProgress();
					validate();
				}
			}
		};
		t.start();
	}

	private void showTransactions(final DateRange range, final ShopTransactionType transactionType) {
		owner.startProgress("Querying...");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					List<ShopTransaction> transactions = dao.getTransactionsByDate(range.getFrom(), range.getTo(), transactionType);

					//reset GUI
					filterPanel.removeAll();
					filterPanel.validate();
					tablePanel.removeAll();
					tablePanel.validate();

					itemsTable = null;
					itemsTableScrollPane = null;
					playersPanel = null;

					//render filter panel
					filterPanel.setVisible(true, true, false);

					//render table
					transactionsTable = new TransactionsTable(transactions, transactionType);
					transactionsTable.setFillsViewportHeight(true);
					transactionsTableScrollPane = new MyJScrollPane(transactionsTable);
					tablePanel.add(transactionsTableScrollPane, "grow, w 100%, h 100%, wrap");
					tablePanel.validate();

					updateNetTotal();
					updateCustomers();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				} finally {
					owner.stopProgress();
					validate();
				}
			}
		};
		t.start();
	}

	public void afterPopulate() {
		queryPanel.updateState();
	}

	private void updateNetTotal() {
		int gained = 0, lost = 0;
		netTotal = 0;

		if (itemsTable != null) {
			for (ItemGroup item : itemsTable.getDisplayedItemGroups()) {
				gained += item.getSoldAmount();
				lost += item.getBoughtAmount();
				netTotal += item.getNetAmount();
			}
		} else if (playersPanel != null) {
			for (ItemGroup item : playersPanel.getDisplayedItems().values()) {
				gained += item.getSoldAmount();
				lost += item.getBoughtAmount();
				netTotal += item.getNetAmount();
			}
		} else if (transactionsTable != null) {
			for (ShopTransaction transaction : transactionsTable.getDisplayedTransactions()) {
				int amount = transaction.getAmount();
				if (amount > 0) {
					gained += amount;
				} else {
					lost += amount;
				}
				netTotal += transaction.getAmount();
			}
		}

		statsPanel.setGained(gained);
		statsPanel.setLost(lost);
		statsPanel.setNetTotal(netTotal);
	}

	private void updateCustomers() {
		//get the number of customers being displayed
		Integer customers = null;
		if (playersPanel != null) {
			customers = playersPanel.getDisplayedPlayers().size();
		}
		if (transactionsTable != null) {
			customers = transactionsTable.getDisplayedPlayersCount();
		}

		if (queryPanel.shopTransactions.isSelected()) {
			statsPanel.setCustomers(customers);
		} else if (queryPanel.myTransactions.isSelected()) {
			statsPanel.setShopsVisited(customers);
		} else if (queryPanel.bothTransactions.isSelected()) {
			statsPanel.setPlayers(customers);
		}
	}

	public void setShowQuantitiesInStacks(boolean stacks) {
		if (itemsTable != null) {
			itemsTable.setShowQuantitiesInStacks(stacks);
		}
		if (playersPanel != null) {
			playersPanel.setShowQuantitiesInStacks(stacks);
		}
		if (transactionsTable != null) {
			transactionsTable.setShowQuantitiesInStacks(stacks);
		}
	}

	@Override
	public String exportData(ExportType type) {
		DateRange range = queryPanel.getDateRange();

		switch (type) {
		case BBCODE:
			if (itemsTable != null) {
				return QueryExporter.generateItemsBBCode(itemsTable.getDisplayedItemGroups(), netTotal, range.getFrom(), range.getTo());
			}

			if (playersPanel != null) {
				List<PlayerGroup> players = playersPanel.getDisplayedPlayers();
				ListMultimap<PlayerGroup, ItemGroup> items = playersPanel.getDisplayedItems();
				return QueryExporter.generatePlayersBBCode(players, items, range.getFrom(), range.getTo());
			}

			if (transactionsTable != null) {
				return QueryExporter.generateTransactionsBBCode(transactionsTable.getDisplayedTransactions(), netTotal, range.getFrom(), range.getTo());
			}

			break;

		case CSV:
			if (itemsTable != null) {
				return QueryExporter.generateItemsCsv(itemsTable.getDisplayedItemGroups(), netTotal, range.getFrom(), range.getTo());
			}

			if (playersPanel != null) {
				List<PlayerGroup> players = playersPanel.getDisplayedPlayers();
				ListMultimap<PlayerGroup, ItemGroup> items = playersPanel.getDisplayedItems();
				return QueryExporter.generatePlayersCsv(players, items, range.getFrom(), range.getTo());
			}

			if (transactionsTable != null) {
				return QueryExporter.generateTransactionsCsv(transactionsTable.getDisplayedTransactions(), netTotal, range.getFrom(), range.getTo());
			}
		}

		return null;
	}

	private class QueryPanel extends JPanel {
		private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
		private final DateFormat dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		private final RelativeDateFormat relativeFormat = new RelativeDateFormat();

		private final JPanel fullPanel;
		private final JRadioButton entireHistory, showSinceLastUpdate, dateRange;
		private final JRadioButton shopTransactions, myTransactions, bothTransactions;
		private final DatePicker fromDatePicker, toDatePicker;
		private final JButton compress, showItems, showPlayers, showTransactions;

		private final JPanel compressedPanel;
		private final JButton expand, showItemsSmall, showPlayersSmall, showTransactionsSmall;
		private final JLabel description;

		private final List<SearchListener> listeners = new ArrayList<SearchListener>();

		public QueryPanel() {
			fullPanel = new JPanel(new MigLayout("insets 0"));
			{
				ButtonGroup dateRangeGroup = new ButtonGroup();

				final ActionListener radioButtonListener = new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent event) {
						updateState();
					}
				};

				entireHistory = new JRadioButton();
				entireHistory.setName("dateRange.entireHistory");
				dateRangeGroup.add(entireHistory);
				entireHistory.addActionListener(radioButtonListener);

				showSinceLastUpdate = new JRadioButton();
				showSinceLastUpdate.setName("dateRange.sinceLastUpdate");
				dateRangeGroup.add(showSinceLastUpdate);
				showSinceLastUpdate.addActionListener(radioButtonListener);

				dateRange = new JRadioButton("date range:");
				dateRange.setName("dateRange.range");
				dateRangeGroup.add(dateRange);
				dateRange.addActionListener(radioButtonListener);

				fromDatePicker = new DatePicker();
				fromDatePicker.setName("dateRange.from");
				fromDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
				fromDatePicker.setShowNoneButton(true);
				fromDatePicker.setShowTodayButton(true);
				fromDatePicker.setStripTime(true);

				toDatePicker = new DatePicker();
				toDatePicker.setName("dateRange.to");
				toDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
				toDatePicker.setShowNoneButton(true);
				toDatePicker.setShowTodayButton(true);
				toDatePicker.setStripTime(true);

				ButtonGroup transactionTypeGroup = new ButtonGroup();

				shopTransactions = new JRadioButton("My Shop");
				shopTransactions.setName("transactionType.myShop");
				transactionTypeGroup.add(shopTransactions);
				myTransactions = new JRadioButton("Other Shops");
				myTransactions.setName("transactionType.otherShops");
				transactionTypeGroup.add(myTransactions);
				bothTransactions = new JRadioButton("Both");
				bothTransactions.setName("transactionType.both");
				transactionTypeGroup.add(bothTransactions);
				shopTransactions.setSelected(true);

				compress = new JButton(Images.UP_ARROW);
				compress.setToolTipText("Hide search controls.");
				compress.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						setCompressed(true);
					}
				});

				showItems = new JButton("By Item", Images.SEARCH_ITEMS);
				showItems.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						onSearch(SearchType.ITEMS);
					}
				});

				showPlayers = new JButton("By Player", Images.SEARCH_PLAYERS);
				showPlayers.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						onSearch(SearchType.PLAYERS);
					}
				});

				showTransactions = new JButton("By Date", Images.SEARCH_DATE);
				showTransactions.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						onSearch(SearchType.DATES);
					}
				});

				//////////////////////

				JPanel datePanel = new GroupPanel("Date Range");
				datePanel.add(entireHistory, "wrap");
				datePanel.add(showSinceLastUpdate, "wrap");
				datePanel.add(dateRange, "split 5");
				datePanel.add(fromDatePicker);
				datePanel.add(new JLabel("to"));
				datePanel.add(toDatePicker);
				fullPanel.add(datePanel, "split 2");

				JPanel typePanel = new GroupPanel("Transaction Type");
				typePanel.add(shopTransactions);
				typePanel.add(new HelpLabel(null, "Shows the transactions that occurred when players bought/sold items to/from your shop."), "wrap");
				typePanel.add(myTransactions);
				typePanel.add(new HelpLabel(null, "Shows the transactions that occurred when you bought/sold items to/from someone else's shop."), "wrap");
				typePanel.add(bothTransactions);
				typePanel.add(new HelpLabel(null, "Includes shop transactions from both your shop and other players' shops."), "wrap");
				fullPanel.add(typePanel, "growy, wrap");

				fullPanel.add(compress, "w 30, split 4");
				fullPanel.add(showItems);
				fullPanel.add(showPlayers);
				fullPanel.add(showTransactions, "wrap");
			}

			compressedPanel = new JPanel(new MigLayout("insets 0"));
			{
				expand = new JButton(Images.DOWN_ARROW);
				expand.setToolTipText("Show search controls.");
				expand.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						setCompressed(false);
					}
				});

				description = new JLabel();
				description.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						setCompressed(false);
					}
				});

				showItemsSmall = new JButton("Item", Images.SEARCH_ITEMS);
				showItemsSmall.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						onSearch(SearchType.ITEMS);
					}
				});

				showPlayersSmall = new JButton("Player", Images.SEARCH_PLAYERS);
				showPlayersSmall.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						onSearch(SearchType.PLAYERS);
					}
				});

				showTransactionsSmall = new JButton("Date", Images.SEARCH_DATE);
				showTransactionsSmall.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						onSearch(SearchType.DATES);
					}
				});

				///////////////////

				compressedPanel.add(expand, "w 30");
				compressedPanel.add(description);
				compressedPanel.add(showItemsSmall);
				compressedPanel.add(showPlayersSmall);
				compressedPanel.add(showTransactionsSmall);
			}

			reset();
		}

		public void updateState() {
			boolean enableDatePickers = dateRange.isSelected();
			fromDatePicker.setEnabled(enableDatePickers);
			toDatePicker.setEnabled(enableDatePickers);
		}

		public void reset() {
			try {
				fromDatePicker.setDate(new Date());
				toDatePicker.setDate(new Date());
			} catch (PropertyVetoException e) {
				throw new RuntimeException(e);
			}

			showSinceLastUpdate.doClick();
			shopTransactions.doClick();
			description.setText("");
			updateEntireHistoryCheckbox();
			updateSinceLastUpdateCheckbox();

			setCompressed(false);
		}

		public void addSearchListener(SearchListener listener) {
			listeners.add(listener);
		}

		private void onSearch(SearchType type) {
			if (!checkDateRange()) {
				return;
			}

			DateRange range = getDateRange();
			setDescription(type, range);

			for (SearchListener listener : listeners) {
				ShopTransactionType transactionType;
				if (shopTransactions.isSelected()) {
					transactionType = ShopTransactionType.MY_SHOP;
				} else if (myTransactions.isSelected()) {
					transactionType = ShopTransactionType.OTHER_SHOPS;
				} else {
					transactionType = ShopTransactionType.ALL;
				}

				listener.searchPerformed(range, type, transactionType);
			}

			if (getComponent(0) == fullPanel) {
				setCompressed(true);
			}
		}

		private void setCompressed(boolean compressed) {
			JPanel panel = compressed ? compressedPanel : fullPanel;

			removeAll();
			add(panel);
			validate();
			TransactionsTab.this.validate();
		}

		private boolean checkDateRange() {
			if (showSinceLastUpdate.isSelected() || entireHistory.isSelected()) {
				return true;
			}

			Date from = fromDatePicker.getDate();
			Date to = toDatePicker.getDate();
			if (from != null && to != null && from.compareTo(to) > 0) {
				JOptionPane.showMessageDialog(this, "Invalid date range: \"Start\" date must come before \"End\" date.", "Invalid date range", JOptionPane.INFORMATION_MESSAGE);
				return false;
			}

			return true;
		}

		/**
		 * Calculates the date range that the query should search over from the
		 * various input elements on the panel.
		 * @return the date range
		 */
		public DateRange getDateRange() {
			Date from, to;
			if (showSinceLastUpdate.isSelected()) {
				try {
					from = dao.getSecondLatestUpdateDate();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				to = null;
			} else if (entireHistory.isSelected()) {
				from = to = null;
			} else {
				from = fromDatePicker.getDate();

				to = toDatePicker.getDate();
				if (to != null) {
					Calendar c = Calendar.getInstance();
					c.setTime(to);
					c.add(Calendar.DATE, 1);
					to = c.getTime();
				}
			}

			return new DateRange(from, to);
		}

		private void updateEntireHistoryCheckbox() {
			Date earliestTransactionDate;
			try {
				earliestTransactionDate = dao.getEarliestTransactionDate();
			} catch (SQLException e) {
				earliestTransactionDate = null;
			}

			String text = "entire history";
			if (earliestTransactionDate != null) {
				text += " (since " + dateTimeFormat.format(earliestTransactionDate) + ")";
			}
			entireHistory.setText(text);
		}

		private void updateSinceLastUpdateCheckbox() {
			Date date;
			try {
				date = dao.getSecondLatestUpdateDate();
				if (date == null) {
					date = dao.getEarliestTransactionDate();
				}
			} catch (SQLException e) {
				date = null;
			}

			StringBuilder sb = new StringBuilder("since previous update");
			if (date != null) {
				sb.append(" (").append(relativeFormat.format(date)).append(")");
			}

			showSinceLastUpdate.setText(sb.toString());
		}

		private void setDescription(SearchType type, DateRange range) {
			Date from = range.getFrom();
			Date to = range.getTo();

			StringBuilder sb = new StringBuilder("<html><b><i><font color=navy>");

			if (from == null && to == null) {
				sb.append("entire history");
			} else if (from != null && to == null) {
				if (showSinceLastUpdate.isSelected()) {
					sb.append("since last update (" + relativeFormat.format(from) + ")");
				} else {
					sb.append("since " + dateFormat.format(from));
				}
			} else if (from == null && to != null) {
				sb.append("up to " + dateFormat.format(to));
			} else if (from != null && to != null) {
				Date toMod = new Date(to.getTime() - 1);
				sb.append(dateFormat.format(from)).append(" to ").append(dateFormat.format(toMod));
			}

			sb.append(" | ");
			if (shopTransactions.isSelected()) {
				sb.append("My Shop");
			} else if (myTransactions.isSelected()) {
				sb.append("Other Shops");
			} else {
				sb.append("All Shop Transactions");
			}

			description.setText(sb.toString());
		}
	}

	public class FilterPanel extends JPanel {
		private final JLabel filterByItemLabel;
		private final FilterTextField filterByItem;
		private final JLabel filterByPlayerLabel;
		private final FilterTextField filterByPlayer;
		private final JLabel sortByLabel;
		private final SortComboBox sortBy;
		private final ExportButton export;

		private final List<FilterListener> filterListeners = new ArrayList<FilterListener>();
		private final List<SortListener> sortListeners = new ArrayList<SortListener>();

		public FilterPanel(ExportListener listener) {
			ActionListener filterAction = new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					for (FilterListener listener : filterListeners) {
						listener.filterPerformed(filterByItem.getNames(), filterByPlayer.getNames());
					}
				}
			};

			filterByItemLabel = new HelpLabel("<html><font size=2>Filter by item(s):", "<b>Filters the table by item.</b>\n<b>Example</b>: <code>wool,\"book\"</code>\n\nMultiple item names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the item name(s), press [<code>Enter</code>] to perform the filtering operation.");
			filterByItem = new FilterTextField();
			filterByItem.addActionListener(filterAction);

			filterByPlayerLabel = new HelpLabel("<html><font size=2>Filter by player(s):", "<b>Filters the table by player.</b>\n<b>Example</b>: <code>aikar,max</code>\n\nMultiple player names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the player name(s), press [<code>Enter</code>] to perform the filtering operation.");
			filterByPlayer = new FilterTextField();
			filterByPlayer.addActionListener(filterAction);

			sortByLabel = new JLabel("<html><font size=2>Sort by:");
			sortBy = new SortComboBox();
			shrinkFont(sortBy);
			sortBy.addActionListener(new ActionListener() {
				private SortItem prev;

				@Override
				public void actionPerformed(ActionEvent e) {
					SortItem selected = sortBy.getSelectedItem();
					if (prev == selected) {
						//the same item was selected
						return;
					}

					for (SortListener listener : sortListeners) {
						listener.sortPerformed(selected);
					}
					prev = selected;
				}
			});

			export = new ExportButton(owner, listener);

			setLayout(new MigLayout("insets 0"));
		}

		public void setVisible(boolean item, boolean player, boolean sort) {
			removeAll();

			if (item) {
				add(filterByItemLabel);
				add(filterByItem, "w 120");
			}

			if (player) {
				add(filterByPlayerLabel);
				add(filterByPlayer, "w 120");
			}

			if (sort) {
				add(sortByLabel);
				add(sortBy, "w 120");
			}

			add(export);

			validate();
		}

		public void addFilterListener(FilterListener listener) {
			filterListeners.add(listener);
		}

		public void addSortListener(SortListener listener) {
			sortListeners.add(listener);
		}

		public void clear() {
			filterByItem.setText("");
			filterByPlayer.setText("");
			sortBy.setSelectedIndex(0);
		}
	}

	private static class SortComboBox extends JComboBox {
		public SortComboBox() {
			super(SortItem.values());
		}

		@Override
		public SortItem getSelectedItem() {
			return (SortItem) super.getSelectedItem();
		}
	}

	private static enum SortItem {
		PLAYER("Player Name"), HIGHEST("Highest Net Total"), LOWEST("Lowest Net Total");

		private final String display;

		private SortItem(String display) {
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}

	private static enum SearchType {
		ITEMS, PLAYERS, DATES
	}

	private interface SearchListener {
		void searchPerformed(DateRange range, SearchType type, ShopTransactionType transactionType);
	}

	private interface FilterListener {
		void filterPerformed(FilterList items, FilterList players);
	}

	private interface SortListener {
		void sortPerformed(SortItem sort);
	}

	private class StatsPanel extends JPanel {
		private final JLabel netTotalLabel, gainedLabel, lostLabel;
		private final JLabel customersLabel;
		private final JLabel customers;
		private final RupeeFormatter rf = new RupeeFormatter();
		{
			rf.setColor(true);
			rf.setPlus(true);
		}

		public StatsPanel() {
			netTotalLabel = new JLabel();
			gainedLabel = new JLabel();
			lostLabel = new JLabel();

			customersLabel = new JLabel();
			customers = new JLabel();
			customersLabel.setVisible(false);
			customers.setVisible(false);

			///////////////////////////////////////
			setLayout(new MigLayout("insets 0"));

			add(customersLabel, "span 1 2");
			add(customers, "span 1 2, gapright 20");

			add(new JLabel("Gained:"), "align right");
			add(gainedLabel, "gapright 10");

			add(new JLabel("<html><font size=5>Net Total:"), "span 1 2");
			add(netTotalLabel, "span 1 2, wrap");

			add(new JLabel("Lost:"), "align right");
			add(lostLabel);

		}

		public void setGained(int gained) {
			gainedLabel.setText("<html><code>" + rf.format(gained));
		}

		public void setLost(int lost) {
			lostLabel.setText("<html><code>" + rf.format(lost));
		}

		public void setNetTotal(int netTotal) {
			netTotalLabel.setText("<html><font size=5><code>" + rf.format(netTotal));
		}

		public void setCustomers(Integer customers) {
			boolean show = (customers != null);

			if (show) {
				customersLabel.setText("<html><font size=5>Customers:");
				this.customers.setText("<html><font size=5><code>" + customers);
			}

			customersLabel.setVisible(show);
			this.customers.setVisible(show);
		}

		public void setShopsVisited(Integer shopsVisited) {
			boolean show = (shopsVisited != null);

			if (show) {
				customersLabel.setText("<html><font size=5>Shops Visited:");
				customers.setText("<html><font size=5><code>" + shopsVisited);
			}

			customersLabel.setVisible(show);
			this.customers.setVisible(show);
		}

		public void setPlayers(Integer players) {
			boolean show = (players != null);

			if (show) {
				customersLabel.setText("<html><font size=5>Players:");
				this.customers.setText("<html><font size=5><code>" + players);
			}

			customersLabel.setVisible(show);
			this.customers.setVisible(show);
		}
	}
}
