package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

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
import emcshop.gui.images.ImageManager;
import emcshop.scraper.ShopTransaction;
import emcshop.util.DateRange;
import emcshop.util.FilterList;

@SuppressWarnings("serial")
public class MyTransactionsTab extends JPanel {
	private static final AppContext context = AppContext.instance();

	private final MainFrame owner;
	private final DbDao dao;
	private final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

	private final JCheckBox entireHistory, showSinceLastUpdate;
	private final JLabel fromDatePickerLabel, toDatePickerLabel;
	private final DatePicker fromDatePicker, toDatePicker;
	private final JButton showItems, showShopOwners, showTransactions;

	private final JLabel filterByItemLabel;
	private final FilterTextField filterByItem;
	private final JLabel filterByShopOwnerLabel;
	private final FilterTextField filterByShopOwner;
	private final JLabel sortByLabel;
	private final SortComboBox sortBy;

	private final JLabel dateRangeQueried;
	private final JPanel tablePanel;
	private final JLabel netTotalLabelLabel;
	private final JLabel netTotalLabel;
	private final JLabel customersLabel;
	private final JLabel customers;

	private ItemsTable itemsTable;
	private MyJScrollPane itemsTableScrollPane;

	private PlayersPanel shopOwnersPanel;

	private TransactionsTable transactionsTable;
	private MyJScrollPane transactionsTableScrollPane;

	private int netTotal;
	private boolean exportable;

	public MyTransactionsTab(MainFrame owner) {
		this.owner = owner;
		dao = context.get(DbDao.class);

		entireHistory = new JCheckBox();
		entireHistory.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (entireHistory.isSelected()) {
					showSinceLastUpdate.setSelected(false);
				}

				boolean enableDatePickers = !entireHistory.isSelected();
				fromDatePickerLabel.setEnabled(enableDatePickers);
				fromDatePicker.setEnabled(enableDatePickers);
				toDatePickerLabel.setEnabled(enableDatePickers);
				toDatePicker.setEnabled(enableDatePickers);
			}
		});

		showSinceLastUpdate = new JCheckBox();
		showSinceLastUpdate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (showSinceLastUpdate.isSelected()) {
					entireHistory.setSelected(false);
				}

				boolean enableDatePickers = !showSinceLastUpdate.isSelected();
				fromDatePickerLabel.setEnabled(enableDatePickers);
				fromDatePicker.setEnabled(enableDatePickers);
				toDatePickerLabel.setEnabled(enableDatePickers);
				toDatePicker.setEnabled(enableDatePickers);
			}
		});

		fromDatePickerLabel = new JLabel("Start:");
		fromDatePicker = new DatePicker();
		fromDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		fromDatePicker.setShowNoneButton(true);
		fromDatePicker.setShowTodayButton(true);
		fromDatePicker.setStripTime(true);

		toDatePickerLabel = new JLabel("End:");
		toDatePicker = new DatePicker();
		toDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		toDatePicker.setShowNoneButton(true);
		toDatePicker.setShowTodayButton(true);
		toDatePicker.setStripTime(true);

		showItems = new JButton("By Item", ImageManager.getSearch());
		showItems.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (!checkDateRange()) {
					return;
				}
				showItems();
			}
		});

		showShopOwners = new JButton("By Shop Owner", ImageManager.getSearch());
		showShopOwners.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (!checkDateRange()) {
					return;
				}
				showShopOwners();
			}
		});

		showTransactions = new JButton("By Date", ImageManager.getSearch());
		showTransactions.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (!checkDateRange()) {
					return;
				}
				showTransactions();
			}
		});

		tablePanel = new JPanel(new MigLayout("width 100%, height 100%, fillx, insets 0"));

		filterByItemLabel = new HelpLabel("Filter by item(s):", "<b>Filters the table by item.</b>\n<b>Example</b>: <code>wool,\"book\"</code>\n\nMultiple item names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the item name(s), press [<code>Enter</code>] to perform the filtering operation.");

		filterByItem = new FilterTextField();
		filterByItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				filter();
			}
		});

		filterByShopOwnerLabel = new HelpLabel("Filter by shop owner(s):", "<b>Filters the table by shop owner.</b>\n<b>Example</b>: <code>shaving, wassatthen</code>\n\nMultiple player names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the player name(s), press [<code>Enter</code>] to perform the filtering operation.");

		filterByShopOwner = new FilterTextField();
		filterByShopOwner.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				filter();
			}
		});

		sortByLabel = new JLabel("Sort by:");
		sortBy = new SortComboBox();

		dateRangeQueried = new JLabel();

		netTotalLabelLabel = new JLabel("<html><font size=5>Net Total:</font></html>");
		netTotalLabel = new JLabel();

		customersLabel = new JLabel("<html><font size=5>Customers:</font></html>");
		customers = new JLabel();

		///////////////////////////////////////

		setLayout(new MigLayout("fillx, insets 5"));

		JPanel left = new JPanel(new MigLayout("insets 0"));

		left.add(entireHistory, "wrap");
		left.add(showSinceLastUpdate, "wrap");
		left.add(fromDatePickerLabel, "split 4");
		left.add(fromDatePicker);
		left.add(toDatePickerLabel);
		left.add(toDatePicker, "wrap");

		left.add(showItems, "split 3");
		left.add(showShopOwners);
		left.add(showTransactions);

		add(left, "w 100%"); //width of 100% forces the right panel to be compact

		JPanel right = new JPanel(new MigLayout("fillx, insets 0"));

		right.add(filterByItemLabel, "align right");
		right.add(filterByItem, "split 2, w 150!");
		right.add(filterByItem.getClearButton(), "w 25!, h 20!, wrap");

		right.add(filterByShopOwnerLabel, "align right");
		right.add(filterByShopOwner, "split 2, w 150!");
		right.add(filterByShopOwner.getClearButton(), "w 25!, h 20!, wrap");

		right.add(sortByLabel, "align right");
		right.add(sortBy, "w 185!");

		add(right, "span 1 2, align right, wrap");

		add(dateRangeQueried, "gaptop 20, w 100%, wrap"); //putting this label here allows the left panel to be vertically aligned to the top of the tab

		add(tablePanel, "span 2, grow, h 100%, wrap");

		add(customersLabel, "span 2, split 4, align right");
		add(customers, "gapright 20");
		add(netTotalLabelLabel);
		add(netTotalLabel);

		///////////////////////////////////////

		exportable = false;
		owner.setExportEnabled(exportable);
		filterByItemLabel.setEnabled(false);
		filterByItem.setEnabled(false);
		filterByShopOwnerLabel.setEnabled(false);
		filterByShopOwner.setEnabled(false);
		sortByLabel.setEnabled(false);
		sortBy.setEnabled(false);

		customersLabel.setVisible(false);
		customers.setVisible(false);

		updateNetTotal();
		updateCustomers();
		updateSinceLastUpdateCheckbox();
		updateEntireHistoryCheckbox();
	}

	private void filter() {
		FilterList items = filterByItem.getNames();
		FilterList shopOwners = filterByShopOwner.getNames();

		if (itemsTable != null) {
			itemsTable.filter(items);
			itemsTableScrollPane.scrollToTop();
		}
		if (shopOwnersPanel != null) {
			shopOwnersPanel.filterByItems(items);
			shopOwnersPanel.filterByPlayers(shopOwners);
		}
		if (transactionsTable != null) {
			transactionsTable.filterByItem(items);
			transactionsTable.filterByPlayers(shopOwners);
			transactionsTableScrollPane.scrollToTop();
		}

		updateNetTotal();
		updateCustomers();
	}

	public void clear() {
		try {
			fromDatePicker.setDate(new Date());
			toDatePicker.setDate(new Date());
		} catch (PropertyVetoException e) {
			throw new RuntimeException(e);
		}

		itemsTable = null;
		itemsTableScrollPane = null;
		shopOwnersPanel = null;
		transactionsTable = null;
		transactionsTableScrollPane = null;
		netTotal = 0;

		exportable = false;
		owner.setExportEnabled(exportable);
		filterByItemLabel.setEnabled(false);
		filterByItem.setEnabled(false);
		filterByItem.setText("");
		filterByShopOwnerLabel.setEnabled(false);
		filterByShopOwner.setEnabled(false);
		filterByShopOwner.setText("");
		sortByLabel.setEnabled(false);
		sortBy.setEnabled(false);
		dateRangeQueried.setText("");

		tablePanel.removeAll();

		updateNetTotal();
		updateCustomers();
		updateSinceLastUpdateCheckbox();
		updateEntireHistoryCheckbox();

		validate();
		repaint(); //the table was still visible in Linux
	}

	public void updateComplete(boolean showResults, boolean firstUpdate) {
		showSinceLastUpdate.setVisible(true);
		updateSinceLastUpdateCheckbox();

		if (firstUpdate) {
			updateEntireHistoryCheckbox();
		}

		if (showResults) {
			showSinceLastUpdate.doClick();
			if (!showSinceLastUpdate.isSelected()) {
				showSinceLastUpdate.setSelected(true);
			}

			showItems();
		}
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
			sb.append(" (").append(df.format(date)).append(")");
		}

		showSinceLastUpdate.setText(sb.toString());
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
			text += " (since " + df.format(earliestTransactionDate) + ")";
		}
		entireHistory.setText(text);
	}

	public void showItems() {
		final DateRange range = getQueryDateRange();
		busyCursor(owner, true);

		final LoadingDialog loading = new LoadingDialog(owner, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					final List<ItemGroup> itemGroupsList;
					{
						//query database
						itemGroupsList = new ArrayList<ItemGroup>(dao.getItemGroups(range.getFrom(), range.getTo(), false));

						//sort by item name
						Collections.sort(itemGroupsList, new Comparator<ItemGroup>() {
							@Override
							public int compare(ItemGroup a, ItemGroup b) {
								return a.getItem().compareToIgnoreCase(b.getItem());
							}
						});
					}

					//reset GUI
					tablePanel.removeAll();
					tablePanel.validate();

					shopOwnersPanel = null;
					transactionsTable = null;
					transactionsTableScrollPane = null;

					exportable = true;
					owner.setExportEnabled(exportable);
					filterByItemLabel.setEnabled(true);
					filterByItem.setEnabled(true);
					filterByItem.setText("");
					filterByShopOwnerLabel.setEnabled(false);
					filterByShopOwner.setEnabled(false);
					filterByShopOwner.setText("");
					sortByLabel.setEnabled(false);
					sortBy.setEnabled(false);

					//render table
					itemsTable = new ItemsTable(itemGroupsList, context.get(Settings.class).isShowQuantitiesInStacks());
					itemsTable.setFillsViewportHeight(true);
					itemsTableScrollPane = new MyJScrollPane(itemsTable);
					tablePanel.add(itemsTableScrollPane, "grow, w 100%, h 100%, wrap");
					tablePanel.validate();

					updateDateRangeLabel(range);
					updateNetTotal();
					updateCustomers();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				} finally {
					loading.dispose();
					busyCursor(owner, false);
				}
			}
		};
		t.start();
		loading.setVisible(true);
	}

	public void showShopOwners() {
		final DateRange range = getQueryDateRange();
		busyCursor(owner, true);

		final LoadingDialog loading = new LoadingDialog(owner, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					Collection<PlayerGroup> shopOwnerGroups = dao.getPlayerGroups(range.getFrom(), range.getTo(), false);

					//reset GUI
					tablePanel.removeAll();
					tablePanel.validate();

					itemsTable = null;
					itemsTableScrollPane = null;
					transactionsTable = null;
					transactionsTableScrollPane = null;

					exportable = true;
					owner.setExportEnabled(exportable);
					filterByItemLabel.setEnabled(true);
					filterByItem.setEnabled(true);
					filterByItem.setText("");
					filterByShopOwnerLabel.setEnabled(true);
					filterByShopOwner.setEnabled(true);
					filterByShopOwner.setText("");
					sortBy.setEnabled(true);
					sortBy.setSelectedIndex(0);
					sortByLabel.setEnabled(true);

					//render table
					shopOwnersPanel = new PlayersPanel(shopOwnerGroups);
					tablePanel.add(shopOwnersPanel, "grow, w 100%, h 100%, wrap");
					tablePanel.validate();

					updateDateRangeLabel(range);
					updateNetTotal();
					updateCustomers();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				} finally {
					loading.dispose();
					busyCursor(owner, false);
				}
			}
		};
		t.start();
		loading.setVisible(true);
	}

	private void showTransactions() {
		final DateRange range = getQueryDateRange();
		busyCursor(owner, true);

		final LoadingDialog loading = new LoadingDialog(owner, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					List<ShopTransaction> transactions = dao.getTransactionsByDate(range.getFrom(), range.getTo(), false);

					//reset GUI
					tablePanel.removeAll();
					tablePanel.validate();

					itemsTable = null;
					itemsTableScrollPane = null;
					shopOwnersPanel = null;

					exportable = true;
					owner.setExportEnabled(exportable);
					filterByItemLabel.setEnabled(true);
					filterByItem.setEnabled(true);
					filterByItem.setText("");
					filterByShopOwnerLabel.setEnabled(true);
					filterByShopOwner.setEnabled(true);
					filterByShopOwner.setText("");
					sortByLabel.setEnabled(false);
					sortBy.setEnabled(false);

					//render table
					transactionsTable = new TransactionsTable(transactions);
					transactionsTable.setFillsViewportHeight(true);
					transactionsTableScrollPane = new MyJScrollPane(transactionsTable);
					tablePanel.add(transactionsTableScrollPane, "grow, w 100%, h 100%, wrap");
					tablePanel.validate();

					updateDateRangeLabel(range);
					updateNetTotal();
					updateCustomers();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				} finally {
					loading.dispose();
					busyCursor(owner, false);
				}
			}
		};
		t.start();
		loading.setVisible(true);
	}

	private void updateDateRangeLabel(DateRange range) {
		Date from = range.getFrom();
		Date to = range.getTo();

		String dateRangeStr;
		String startFont = "<b><i><font color=navy>";
		String endFont = "</font></i></b>";
		if (from == null && to == null) {
			dateRangeStr = startFont + "entire history" + endFont;
		} else if (from == null) {
			dateRangeStr = "up to " + startFont + df.format(to) + endFont;
		} else if (to == null) {
			dateRangeStr = startFont + df.format(from) + endFont + " to " + startFont + "now" + endFont;
		} else if (from.equals(to)) {
			dateRangeStr = startFont + df.format(from) + endFont;
		} else {
			dateRangeStr = startFont + df.format(from) + endFont + " to " + startFont + df.format(to) + endFont;
		}

		dateRangeQueried.setText("<html>" + dateRangeStr + "</html>");
	}

	private void updateNetTotal() {
		netTotal = 0;

		if (itemsTable != null) {
			for (ItemGroup item : itemsTable.getDisplayedItemGroups()) {
				netTotal += item.getNetAmount();
			}
		} else if (shopOwnersPanel != null) {
			for (ItemGroup item : shopOwnersPanel.getDisplayedItems().values()) {
				netTotal += item.getNetAmount();
			}
		} else if (transactionsTable != null) {
			for (ShopTransaction transaction : transactionsTable.getDisplayedTransactions()) {
				netTotal += transaction.getAmount();
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<html><font size=5><code>");
		sb.append(formatRupeesWithColor(netTotal));
		sb.append("</code></font></html>");
		netTotalLabel.setText(sb.toString());
	}

	private void updateCustomers() {
		//get the number of customers being displayed
		Integer customersCount = null;
		if (shopOwnersPanel != null) {
			customersCount = shopOwnersPanel.getDisplayedPlayers().size();
		}
		if (transactionsTable != null) {
			customersCount = transactionsTable.getDisplayedPlayers().size();
		}

		//update the label text
		if (customersCount != null) {
			customers.setText("<html><font size=5><code>" + customersCount + "</code></font></html>");
		}

		//make label visible
		boolean visible = (customersCount != null);
		customersLabel.setVisible(visible);
		customers.setVisible(visible);
	}

	private boolean checkDateRange() {
		if (showSinceLastUpdate.isSelected() || entireHistory.isSelected()) {
			return true;
		}

		Date from = fromDatePicker.getDate();
		Date to = toDatePicker.getDate();
		if (from.compareTo(to) > 0) {
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
	private DateRange getQueryDateRange() {
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

	public void setShowQuantitiesInStacks(boolean stacks) {
		if (itemsTable != null) {
			itemsTable.setShowQuantitiesInStacks(stacks);
		}
		if (shopOwnersPanel != null) {
			shopOwnersPanel.setShowQuantitiesInStacks(stacks);
		}
		if (transactionsTable != null) {
			transactionsTable.setShowQuantitiesInStacks(stacks);
		}
	}

	public String export(ExportType type) {
		DateRange range = getQueryDateRange();

		switch (type) {
		case BBCODE:
			if (itemsTable != null) {
				return QueryExporter.generateItemsBBCode(itemsTable.getDisplayedItemGroups(), netTotal, range.getFrom(), range.getTo());
			}

			if (shopOwnersPanel != null) {
				List<PlayerGroup> players = shopOwnersPanel.getDisplayedPlayers();
				ListMultimap<PlayerGroup, ItemGroup> items = shopOwnersPanel.getDisplayedItems();
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

			if (shopOwnersPanel != null) {
				List<PlayerGroup> players = shopOwnersPanel.getDisplayedPlayers();
				ListMultimap<PlayerGroup, ItemGroup> items = shopOwnersPanel.getDisplayedItems();
				return QueryExporter.generatePlayersCsv(players, items, range.getFrom(), range.getTo());
			}

			if (transactionsTable != null) {
				return QueryExporter.generateTransactionsCsv(transactionsTable.getDisplayedTransactions(), netTotal, range.getFrom(), range.getTo());
			}
		}

		return null;
	}

	public boolean isExportable() {
		return exportable;
	}

	private class SortComboBox extends JComboBox implements ActionListener {
		private static final String shopOwnerName = "Shop owner name";
		private static final String soldMost = "Sold the most to";
		private static final String boughtMost = "Bought the most from";
		private String currentSelection;

		public SortComboBox() {
			addItem(shopOwnerName);
			addItem(soldMost);
			addItem(boughtMost);
			addActionListener(this);
			currentSelection = shopOwnerName;
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
			if (shopOwnersPanel == null) {
				return;
			}

			String selected = (String) getSelectedItem();
			if (selected == currentSelection) {
				return;
			}

			busyCursor(owner, true);
			try {
				if (selected == shopOwnerName) {
					shopOwnersPanel.sortByPlayerName();
				} else if (selected == soldMost) {
					shopOwnersPanel.sortByCustomers();
				} else if (selected == boughtMost) {
					shopOwnersPanel.sortBySuppliers();
				}
				currentSelection = selected;
			} finally {
				busyCursor(owner, false);
			}
		}
	}
}
