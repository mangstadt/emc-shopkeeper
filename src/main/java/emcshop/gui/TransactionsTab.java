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
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.QueryExporter;
import emcshop.db.DbDao;
import emcshop.db.ItemGroup;
import emcshop.db.PlayerGroup;
import emcshop.gui.images.ImageManager;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class TransactionsTab extends JPanel {
	private final MainFrame owner;
	private final DbDao dao;
	private final ProfileImageLoader profileImageLoader;

	private final DatePicker toDatePicker;
	private final DatePicker fromDatePicker;
	private final JButton showItems;
	private final JButton showPlayers;

	private final ExportComboBox export;
	private final JLabel filterByItemLabel;
	private final FilterTextField filterByItem;
	private final JLabel filterByPlayerLabel;
	private final FilterTextField filterByPlayer;
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

	private PlayersPanel playersPanel;
	private MyJScrollPane playersPanelScrollPane;

	private int netTotal;

	public TransactionsTab(MainFrame owner, DbDao dao, ProfileImageLoader profileImageLoader) {
		this.owner = owner;
		this.dao = dao;
		this.profileImageLoader = profileImageLoader;

		fromDatePicker = new DatePicker();
		fromDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		fromDatePicker.setShowNoneButton(true);
		fromDatePicker.setShowTodayButton(true);
		fromDatePicker.setStripTime(true);

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
				showTransactions();
			}
		});

		showPlayers = new JButton("By Player", ImageManager.getSearch());
		showPlayers.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (!checkDateRange()) {
					return;
				}
				showPlayers();
			}
		});

		tablePanel = new JPanel(new MigLayout("width 100%, height 100%, fillx, insets 0"));

		export = new ExportComboBoxImpl();

		filterByItemLabel = new HelpLabel("Filter by item(s):", "<b>Filters the table by item.</b>\n<b>Example</b>: <code>wool,\"book\"</code>\n\nMultiple item names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the item name(s), press [<code>Enter</code>] to perform the filtering operation.");

		filterByItem = new FilterTextField();
		filterByItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> filteredItems = filterByItem.getNames();

				if (itemsTable != null) {
					itemsTable.filter(filteredItems);
					itemsTableScrollPane.scrollToTop();
				}
				if (playersPanel != null) {
					playersPanel.filterByItems(filteredItems);
					playersPanelScrollPane.scrollToTop();
				}

				updateNetTotal();
				updateCustomers();
			}
		});

		filterByPlayerLabel = new HelpLabel("Filter by player(s):", "<b>Filters the table by player.</b>\n<b>Example</b>: <code>aikar,max</code>\n\nMultiple player names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the player name(s), press [<code>Enter</code>] to perform the filtering operation.");

		filterByPlayer = new FilterTextField();
		filterByPlayer.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> filteredPlayers = filterByPlayer.getNames();

				if (playersPanel != null) {
					playersPanel.filterByPlayers(filteredPlayers);
					playersPanelScrollPane.scrollToTop();
				}

				updateNetTotal();
				updateCustomers();
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

		add(new JLabel("Start:"), "split 4");
		add(fromDatePicker);
		add(new JLabel("End:"));
		add(toDatePicker);

		add(export, "align right, wrap");

		add(showItems, "split 2");
		add(showPlayers);

		add(filterByItemLabel, "split 3, align right");
		add(filterByItem, "w 150!");
		add(filterByItem.getClearButton(), "w 25!, h 20!, wrap");

		add(filterByPlayerLabel, "span 2, split 3, align right");
		add(filterByPlayer, "w 150!");
		add(filterByPlayer.getClearButton(), "w 25!, h 20!, wrap");

		add(sortByLabel, "span 2, split 2, align right");
		add(sortBy, "wrap");

		add(dateRangeQueried, "span 2, wrap");

		add(tablePanel, "span 2, grow, h 100%, wrap");

		add(customersLabel, "span 2, split 4, align right");
		add(customers);
		add(netTotalLabelLabel);
		add(netTotalLabel);

		///////////////////////////////////////

		export.setEnabled(false);
		filterByItemLabel.setEnabled(false);
		filterByItem.setEnabled(false);
		filterByPlayerLabel.setEnabled(false);
		filterByPlayer.setEnabled(false);
		sortByLabel.setEnabled(false);
		sortBy.setEnabled(false);

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
		playersPanel = null;
		playersPanelScrollPane = null;
		netTotal = 0;

		export.setEnabled(false);
		filterByItemLabel.setEnabled(false);
		filterByItem.setEnabled(false);
		filterByItem.setText("");
		filterByPlayerLabel.setEnabled(false);
		filterByPlayer.setEnabled(false);
		filterByPlayer.setText("");
		sortByLabel.setEnabled(false);
		sortBy.setEnabled(false);

		tablePanel.removeAll();

		updateNetTotal();
		updateCustomers();

		validate();
	}

	private void showTransactions() {
		Date range[] = getDbDateRange();
		Date from = range[0];
		Date to = range[1];
		showTransactions(from, to);
	}

	public void showTransactions(final Date from, final Date to) {
		GuiUtils.busyCursor(owner, true);

		tablePanel.removeAll();
		tablePanel.validate();

		playersPanel = null;
		playersPanelScrollPane = null;

		export.setEnabled(true);
		filterByItemLabel.setEnabled(true);
		filterByItem.setEnabled(true);
		filterByItem.setText("");
		filterByPlayerLabel.setEnabled(false);
		filterByPlayer.setEnabled(false);
		filterByPlayer.setText("");
		sortByLabel.setEnabled(false);
		sortBy.setEnabled(false);

		final LoadingDialog loading = new LoadingDialog(owner, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					final List<ItemGroup> itemGroupsList;
					{
						//query database
						Map<String, ItemGroup> itemGroups = dao.getItemGroups(from, to);
						itemGroupsList = new ArrayList<ItemGroup>(itemGroups.values());

						//sort by item name
						Collections.sort(itemGroupsList, new Comparator<ItemGroup>() {
							@Override
							public int compare(ItemGroup a, ItemGroup b) {
								return a.getItem().compareToIgnoreCase(b.getItem());
							}
						});
					}

					//render table
					itemsTable = new ItemsTable(itemGroupsList);
					itemsTable.setFillsViewportHeight(true);
					itemsTableScrollPane = new MyJScrollPane(itemsTable);
					tablePanel.add(itemsTableScrollPane, "grow, w 100%, h 100%, wrap");
					tablePanel.validate();

					updateDateRangeLabel(from, to);
					updateNetTotal();
					updateCustomers();
				} catch (SQLException e) {
					ErrorDialog.show(owner, "An error occurred querying the database.", e);
				} finally {
					loading.dispose();
					busyCursor(owner, false);
				}
			}
		};
		t.start();
		loading.setVisible(true);
	}

	private void showPlayers() {
		Date range[] = getDbDateRange();
		Date from = range[0];
		Date to = range[1];
		showPlayers(from, to);
	}

	public void showPlayers(final Date from, final Date to) {
		busyCursor(owner, true);

		tablePanel.removeAll();
		tablePanel.validate();

		itemsTable = null;
		itemsTableScrollPane = null;

		export.setEnabled(true);
		filterByItemLabel.setEnabled(true);
		filterByItem.setEnabled(true);
		filterByItem.setText("");
		filterByPlayerLabel.setEnabled(true);
		filterByPlayer.setEnabled(true);
		filterByPlayer.setText("");
		sortBy.setEnabled(true);
		sortBy.setSelectedIndex(0);
		sortByLabel.setEnabled(true);

		final LoadingDialog loading = new LoadingDialog(owner, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					Collection<PlayerGroup> playerGroups = dao.getPlayerGroups(from, to).values();

					//render table
					playersPanel = new PlayersPanel(playerGroups, profileImageLoader);
					playersPanelScrollPane = new MyJScrollPane(playersPanel);
					tablePanel.add(playersPanelScrollPane, "grow, w 100%, h 100%, wrap");
					tablePanel.validate();

					updateDateRangeLabel(from, to);
					updateNetTotal();
					updateCustomers();
				} catch (SQLException e) {
					loading.dispose();
					ErrorDialog.show(owner, "An error occurred querying the database.", e);
				} finally {
					loading.dispose();
					busyCursor(owner, false);
				}
			}
		};
		t.start();
		loading.setVisible(true);
	}

	private void updateDateRangeLabel(Date from, Date to) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dateRangeStr;
		if (from == null && to == null) {
			dateRangeStr = "entire history";
		} else if (from == null) {
			dateRangeStr = "up to <b><code>" + df.format(to) + "</b></code>";
		} else if (to == null) {
			dateRangeStr = "<b><code>" + df.format(from) + "</b></code> to today";
		} else if (from.equals(to)) {
			dateRangeStr = "<b><code>" + df.format(from) + "</b></code>";
		} else {
			dateRangeStr = "<b><code>" + df.format(from) + "</b></code> to <b><code>" + df.format(to) + "</b></code>";
		}

		dateRangeQueried.setText("<html>" + dateRangeStr + "</html>");
	}

	private void updateNetTotal() {
		netTotal = 0;

		if (itemsTable != null) {
			for (ItemGroup item : itemsTable.getDisplayedItemGroups()) {
				netTotal += item.getNetAmount();
			}
		} else if (playersPanel != null) {
			for (List<ItemGroup> playerItems : playersPanel.getDisplayedItems().values()) {
				for (ItemGroup item : playerItems) {
					netTotal += item.getNetAmount();
				}
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<html><font size=5><code>");
		sb.append(formatRupeesWithColor(netTotal));
		sb.append("</code></font></html>");
		netTotalLabel.setText(sb.toString());
	}

	private void updateCustomers() {
		String customers = null;

		if (itemsTable != null) {
			customers = "?";
		} else if (playersPanel != null) {
			customers = playersPanel.getDisplayedPlayers().size() + "";
		} else {
			customers = "0";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<html><font size=5><code>");
		sb.append(customers).append("   ");
		sb.append("</code></font></html>");
		this.customers.setText(sb.toString());
	}

	private boolean checkDateRange() {
		Date from = fromDatePicker.getDate();
		Date to = toDatePicker.getDate();
		if (from.compareTo(to) > 0) {
			JOptionPane.showMessageDialog(this, "Invalid date range: \"Start\" date must come before \"End\" date.", "Invalid date range", JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		return true;
	}

	private Date[] getDbDateRange() {
		Date from = fromDatePicker.getDate();

		Date to = toDatePicker.getDate();
		if (to != null) {

			Calendar c = Calendar.getInstance();
			c.setTime(to);
			c.add(Calendar.DATE, 1);
			to = c.getTime();
		}

		return new Date[] { from, to };
	}

	private class ExportComboBoxImpl extends ExportComboBox implements ActionListener {
		@Override
		public String bbCode() {
			Date from = fromDatePicker.getDate();
			Date to = toDatePicker.getDate();

			if (itemsTable != null) {
				return QueryExporter.generateItemsBBCode(itemsTable.getDisplayedItemGroups(), netTotal, from, to);
			}

			if (playersPanel != null) {
				List<PlayerGroup> players = playersPanel.getDisplayedPlayers();
				Map<PlayerGroup, List<ItemGroup>> items = playersPanel.getDisplayedItems();
				return QueryExporter.generatePlayersBBCode(players, items, from, to);
			}

			return null;
		}

		@Override
		public String csv() {
			Date from = fromDatePicker.getDate();
			Date to = toDatePicker.getDate();

			if (itemsTable != null) {
				return QueryExporter.generateItemsCsv(itemsTable.getDisplayedItemGroups(), netTotal, from, to);
			}

			if (playersPanel != null) {
				List<PlayerGroup> players = playersPanel.getDisplayedPlayers();
				Map<PlayerGroup, List<ItemGroup>> items = playersPanel.getDisplayedItems();
				return QueryExporter.generatePlayersCsv(players, items, from, to);
			}

			return null;
		}
	}

	private class SortComboBox extends JComboBox implements ActionListener {
		private final String playerName = "Player name";
		private final String bestCustomers = "Best Customers";
		private final String bestSuppliers = "Best Suppliers";
		private String currentSelection;

		public SortComboBox() {
			addItem(playerName);
			addItem(bestCustomers);
			addItem(bestSuppliers);
			addActionListener(this);
			currentSelection = playerName;
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
			if (playersPanel == null) {
				return;
			}

			String selected = (String) getSelectedItem();
			if (selected == currentSelection) {
				return;
			}

			busyCursor(owner, true);
			try {
				if (selected == playerName) {
					playersPanel.sortByPlayerName();
				} else if (selected == bestCustomers) {
					playersPanel.sortByCustomers();
				} else if (selected == bestSuppliers) {
					playersPanel.sortBySuppliers();
				}
				currentSelection = selected;

				playersPanelScrollPane.scrollToTop();
			} finally {
				busyCursor(owner, false);
			}
		}
	}
}
