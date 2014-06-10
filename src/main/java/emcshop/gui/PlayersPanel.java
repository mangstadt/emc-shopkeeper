package emcshop.gui;

import static emcshop.util.NumberFormatter.formatRupees;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.Color;
import java.awt.Component;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.miginfocom.swing.MigLayout;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import emcshop.AppContext;
import emcshop.Settings;
import emcshop.db.ItemGroup;
import emcshop.db.Player;
import emcshop.db.PlayerGroup;
import emcshop.gui.FilterPanel.FilterList;
import emcshop.gui.ItemsTable.Column;
import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.ClickableLabel;
import emcshop.scraper.EmcServer;
import emcshop.util.UIDefaultsWrapper;

/**
 * A panel that displays transactions grouped by player.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class PlayersPanel extends JPanel {
	private static final AppContext context = AppContext.instance();

	private final List<PlayerGroup> playerGroups;
	private final ProfileLoader profileLoader;
	private final OnlinePlayersMonitor onlinePlayersMonitor;
	private boolean showQuantitiesInStacks, showFirstLastSeen = true;

	private final DateFormat dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
	private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
	private final ListMultimap<PlayerGroup, ItemGroup> itemGroups = ArrayListMultimap.create();
	private List<PlayerGroup> displayedPlayers;
	private ListMultimap<PlayerGroup, ItemGroup> displayedItems;
	private FilterList filteredPlayerNames = new FilterList();
	private FilterList filteredItemNames = new FilterList();
	private List<ItemsTable> tables = new ArrayList<ItemsTable>();
	private Sort sort;
	private JPanel tablesPanel;
	private MyJScrollPane tablesPanelScrollPane;

	/**
	 * @param playerGroups the players to display
	 */
	public PlayersPanel(Collection<PlayerGroup> playerGroups) {
		super(new MigLayout("fillx, insets 0"));

		//add all the data to Lists so they can be sorted
		this.playerGroups = new ArrayList<PlayerGroup>(playerGroups);
		for (PlayerGroup playerGroup : playerGroups) {
			Collection<ItemGroup> itemGroups = playerGroup.getItems().values();
			this.itemGroups.putAll(playerGroup, itemGroups);
		}

		profileLoader = context.get(ProfileLoader.class);
		onlinePlayersMonitor = context.get(OnlinePlayersMonitor.class);
		showQuantitiesInStacks = context.get(Settings.class).isShowQuantitiesInStacks();

		sortByPlayerName();
	}

	/**
	 * Sorts the data by player name.
	 */
	public void sortByPlayerName() {
		sort = Sort.PLAYER;
		refresh();
	}

	/**
	 * Sorts the data by best supplier.
	 */
	public void sortBySuppliers() {
		sort = Sort.SUPPLIER;
		refresh();
	}

	/**
	 * Sorts the data by best customer.
	 */
	public void sortByCustomers() {
		sort = Sort.CUSTOMER;
		refresh();
	}

	/**
	 * Filters the data by player.
	 * @param players the players to filter by
	 */
	public void filterByPlayers(FilterList players) {
		filteredPlayerNames = players;
		refresh();
	}

	/**
	 * Filters the data by item.
	 * @param items the items to filter by
	 */
	public void filterByItems(FilterList items) {
		filteredItemNames = items;
		refresh();
	}

	public List<PlayerGroup> getDisplayedPlayers() {
		return displayedPlayers;
	}

	public ListMultimap<PlayerGroup, ItemGroup> getDisplayedItems() {
		return displayedItems;
	}

	public void setShowQuantitiesInStacks(boolean enable) {
		showQuantitiesInStacks = enable;
		for (ItemsTable table : tables) {
			table.setShowQuantitiesInStacks(enable);
		}
	}

	public void setShowFirstLastSeen(boolean show) {
		this.showFirstLastSeen = show;
	}

	private void refresh() {
		//filter players
		displayedPlayers = filterPlayers();

		//filter items
		displayedItems = filterItems(displayedPlayers);

		//sort data
		sortData(displayedPlayers, displayedItems);

		removeAll();
		tables.clear();

		final JList list = new JList(new Vector<PlayerGroup>(displayedPlayers));
		list.setCellRenderer(new ListCellRenderer() {
			private static final int profileImageSize = 32;
			private final Map<String, Icon> playerIcons = new HashMap<String, Icon>();
			private final ProfileDownloadedListener onImageDownloaded = new ProfileDownloadedListener() {
				@Override
				public void onProfileDownloaded(JLabel label) {
					synchronized (playerIcons) {
						playerIcons.put(label.getName(), label.getIcon());
						list.repaint();
					}
				}
			};

			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
				PlayerGroup playerGroup = (PlayerGroup) value;
				Player player = playerGroup.getPlayer();
				String playerName = player.getName();

				JPanel row = new JPanel(new MigLayout("insets 5"));
				UIDefaultsWrapper.assignListFormats(row, selected);

				JLabel profileImage = new JLabel();
				profileImage.setHorizontalAlignment(SwingConstants.CENTER);
				profileImage.setVerticalAlignment(SwingConstants.CENTER);

				synchronized (playerIcons) {
					Icon icon = playerIcons.get(playerName);

					if (icon == null) {
						profileImage.setName(playerName);
						profileLoader.loadPortrait(playerName, profileImage, profileImageSize, onImageDownloaded);
					} else {
						profileImage.setIcon(icon);
					}
				}

				row.add(profileImage, "w " + profileImageSize + "!, h " + profileImageSize + "!, span 1 2");

				JLabel playerNameLabel = new JLabel(playerName);
				EmcServer server = onlinePlayersMonitor.getPlayerServer(playerName);
				if (server != null) {
					playerNameLabel.setIcon(ImageManager.getOnline(server, 16));
					playerNameLabel.setHorizontalTextPosition(SwingConstants.LEFT);
					playerNameLabel.setVerticalTextPosition(SwingConstants.TOP);
				}
				if (selected) {
					playerNameLabel.setForeground(UIDefaultsWrapper.getListForegroundSelected());
				} else {
					profileLoader.loadRank(playerName, playerNameLabel, null);
				}
				row.add(playerNameLabel, "gapbottom 0, wrap");

				int netTotal = calculateNetTotal(playerGroup);
				JLabel rupeeTotalLabel;
				if (selected) {
					rupeeTotalLabel = new JLabel(formatRupees(netTotal));
					rupeeTotalLabel.setForeground(UIDefaultsWrapper.getListForegroundSelected());
				} else {
					rupeeTotalLabel = new JLabel("<html>" + formatRupeesWithColor(netTotal));
				}
				row.add(rupeeTotalLabel, "gaptop 0");

				return row;
			}
		});
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		list.addListSelectionListener(new ListSelectionListener() {
			private int[] prevSelected;

			@Override
			public void valueChanged(ListSelectionEvent arg0) {
				int[] selectedIndices = list.getSelectedIndices();
				if (sameSelection(selectedIndices)) {
					return;
				}

				List<PlayerGroup> selected = new ArrayList<PlayerGroup>();
				for (int index : selectedIndices) {
					selected.add(displayedPlayers.get(index));
				}
				showTables(selected);

				prevSelected = selectedIndices;
			}

			private boolean sameSelection(int[] selectedIndices) {
				return (prevSelected != null && Arrays.equals(prevSelected, selectedIndices));
			}
		});
		add(new MyJScrollPane(list), "w 450, growy");

		tablesPanel = new JPanel(new MigLayout("insets 1, fillx"));
		tablesPanelScrollPane = new MyJScrollPane(tablesPanel);
		add(tablesPanelScrollPane, "grow, w 100%, h 100%");

		validate();
	}

	private void showTables(List<PlayerGroup> playerGroups) {
		tablesPanel.removeAll();
		tables.clear();

		final int profileImageSize = 64;
		for (PlayerGroup playerGroup : playerGroups) {
			Player player = playerGroup.getPlayer();

			JPanel header = new JPanel(new MigLayout("insets 3"));
			header.setOpaque(false);

			int rows = 3;
			String title = profileLoader.getTitle(player.getName());
			if (title != null) {
				rows++;
			}
			Date joined = profileLoader.getJoinDate(player.getName());
			if (joined != null) {
				rows++;
			}

			JLabel profileImage = new JLabel();
			profileImage.setHorizontalAlignment(SwingConstants.CENTER);
			profileImage.setVerticalAlignment(SwingConstants.TOP);
			profileLoader.loadPortrait(player.getName(), profileImage, profileImageSize);
			header.add(profileImage, "span 1 " + rows + ", w " + profileImageSize + "!, h " + profileImageSize + "!, gapright 10");

			JLabel playerName = new ClickableLabel("<html><h3><u>" + player.getName(), "http://u.emc.gs/" + player.getName());
			playerName.setToolTipText("View player's profile");

			EmcServer server = onlinePlayersMonitor.getPlayerServer(player.getName());
			if (server != null) {
				header.add(playerName, "span 2, split 2");

				JLabel onlineLabel = new JLabel("<html><font size=2><i>Connected to <b>" + server, ImageManager.getOnline(null, 16), SwingConstants.LEFT);
				onlineLabel.setIconTextGap(2);
				header.add(onlineLabel, "gapleft 10, wrap");
			} else {
				header.add(playerName, "span 2, wrap");
			}

			if (title != null) {
				Color rankColor = profileLoader.getRankColor(player.getName());
				String hex = String.format("#%02x%02x%02x", rankColor.getRed(), rankColor.getGreen(), rankColor.getBlue());

				JLabel playerTitle = new JLabel("<html><font color=" + hex + ">" + title);
				header.add(playerTitle, "gaptop 0, span 2, wrap");
			}

			if (joined != null) {
				header.add(new JLabel("Joined:"));
				header.add(new JLabel(dateFormat.format(joined)), "wrap");
			}

			if (showFirstLastSeen) {
				Date firstSeen = player.getFirstSeen();
				if (firstSeen != null) {
					header.add(new JLabel("First seen:"));
					header.add(new JLabel(dateTimeFormat.format(firstSeen)), "wrap");
				}

				Date lastSeen = player.getLastSeen();
				if (lastSeen != null) {
					header.add(new JLabel("Last seen:"));
					header.add(new JLabel(dateTimeFormat.format(lastSeen)), "wrap");
				}
			}

			tablesPanel.add(header, "wrap");

			Column column = null;
			boolean ascending = true;
			switch (sort) {
			case PLAYER:
				column = Column.ITEM_NAME;
				ascending = true;
				break;
			case SUPPLIER:
				column = Column.NET_AMT;
				ascending = true;
				break;
			case CUSTOMER:
				column = Column.NET_AMT;
				ascending = false;
				break;
			}

			ItemsTable table = new ItemsTable(displayedItems.get(playerGroup), column, ascending, showQuantitiesInStacks);
			tablesPanel.add(table.getTableHeader(), "growx, wrap");
			tablesPanel.add(table, "growx, wrap");
			tables.add(table);

			JLabel netAmount;
			{
				int amount = calculateNetTotal(playerGroup);

				StringBuilder sb = new StringBuilder();
				sb.append("<html><code><b>");
				sb.append(formatRupeesWithColor(amount));
				sb.append("</b></code></html>");
				netAmount = new JLabel(sb.toString());
			}
			tablesPanel.add(netAmount, "align right, span 2, wrap");
		}

		tablesPanel.validate();
		tablesPanelScrollPane.scrollToTop();
	}

	private int calculateNetTotal(PlayerGroup playerGroup) {
		int total = 0;
		for (ItemGroup item : displayedItems.get(playerGroup)) {
			total += item.getNetAmount();
		}
		return total;
	}

	private List<PlayerGroup> filterPlayers() {
		if (filteredPlayerNames.isEmpty()) {
			return new LinkedList<PlayerGroup>(playerGroups);
		}

		List<PlayerGroup> filteredPlayers = new LinkedList<PlayerGroup>();
		for (PlayerGroup playerGroup : playerGroups) {
			String playerName = playerGroup.getPlayer().getName();
			if (filteredPlayerNames.contains(playerName)) {
				filteredPlayers.add(playerGroup);
			}
		}
		return filteredPlayers;
	}

	private ListMultimap<PlayerGroup, ItemGroup> filterItems(List<PlayerGroup> filteredPlayers) {
		if (filteredPlayerNames.isEmpty() && filteredItemNames.isEmpty()) {
			return itemGroups;
		}

		List<PlayerGroup> removePlayers = new ArrayList<PlayerGroup>();
		ListMultimap<PlayerGroup, ItemGroup> filteredItems = ArrayListMultimap.create();
		for (PlayerGroup playerGroup : filteredPlayers) {
			Collection<ItemGroup> itemGroups;
			if (filteredItemNames.isEmpty()) {
				itemGroups = this.itemGroups.get(playerGroup);
				filteredItems.putAll(playerGroup, itemGroups);
			} else {
				itemGroups = new ArrayList<ItemGroup>();
				for (ItemGroup itemGroup : this.itemGroups.get(playerGroup)) {
					String itemName = itemGroup.getItem();
					if (filteredItemNames.contains(itemName)) {
						itemGroups.add(itemGroup);
					}
				}
				if (itemGroups.isEmpty()) {
					removePlayers.add(playerGroup);
				} else {
					filteredItems.putAll(playerGroup, itemGroups);
				}
			}
		}
		for (PlayerGroup playerGroup : removePlayers) {
			filteredPlayers.remove(playerGroup);
		}
		return filteredItems;
	}

	private void sortData(List<PlayerGroup> players, final ListMultimap<PlayerGroup, ItemGroup> items) {
		switch (sort) {
		case PLAYER:
			//sort by player name
			Collections.sort(players, new Comparator<PlayerGroup>() {
				@Override
				public int compare(PlayerGroup a, PlayerGroup b) {
					return a.getPlayer().getName().compareToIgnoreCase(b.getPlayer().getName());
				}
			});

			//sort each player's item list by item name
			for (PlayerGroup group : players) {
				Collections.sort(items.get(group), new Comparator<ItemGroup>() {
					@Override
					public int compare(ItemGroup a, ItemGroup b) {
						return a.getItem().compareToIgnoreCase(b.getItem());
					}
				});
			}
			break;
		case SUPPLIER:
			//sort by net sold amount ascending
			Collections.sort(players, new Comparator<PlayerGroup>() {
				@Override
				public int compare(PlayerGroup a, PlayerGroup b) {
					int netA = 0;
					for (ItemGroup item : items.get(a)) {
						netA += item.getNetAmount();
					}

					int netB = 0;
					for (ItemGroup item : items.get(b)) {
						netB += item.getNetAmount();
					}

					return netA - netB;
				}
			});

			//sort each player's item list by item amount ascending
			for (PlayerGroup group : players) {
				Collections.sort(items.get(group), new Comparator<ItemGroup>() {
					@Override
					public int compare(ItemGroup a, ItemGroup b) {
						return a.getNetAmount() - b.getNetAmount();
					}
				});
			}
			break;
		case CUSTOMER:
			//sort by net bought amount descending
			Collections.sort(players, new Comparator<PlayerGroup>() {
				@Override
				public int compare(PlayerGroup a, PlayerGroup b) {
					int netA = 0;
					for (ItemGroup item : items.get(a)) {
						netA += item.getNetAmount();
					}

					int netB = 0;
					for (ItemGroup item : items.get(b)) {
						netB += item.getNetAmount();
					}

					return netB - netA;
				}
			});

			//sort each player's item list by item amount descending
			for (PlayerGroup group : players) {
				Collections.sort(items.get(group), new Comparator<ItemGroup>() {
					@Override
					public int compare(ItemGroup a, ItemGroup b) {
						return b.getNetAmount() - a.getNetAmount();
					}
				});
			}
			break;
		}
	}

	private enum Sort {
		PLAYER, SUPPLIER, CUSTOMER
	}
}
