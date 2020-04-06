package emcshop.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.github.mangstadt.emc.net.EmcServer;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import emcshop.AppContext;
import emcshop.Settings;
import emcshop.db.ItemGroup;
import emcshop.db.Player;
import emcshop.db.PlayerGroup;
import emcshop.db.ShopTransactionType;
import emcshop.gui.ItemsTable.Column;
import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.gui.images.Images;
import emcshop.gui.lib.ClickableLabel;
import emcshop.scraper.PlayerProfile;
import emcshop.util.BaseFormatter;
import emcshop.util.RelativeDateFormat;
import emcshop.util.RupeeFormatter;
import emcshop.util.UIDefaultsWrapper;
import net.miginfocom.swing.MigLayout;

/**
 * A panel that displays transactions grouped by player.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class PlayersPanel extends JPanel {
	private static final AppContext context = AppContext.instance();
	private static final int profileImageSize = 96, profileImageOnlineIconSize = 32;

	private final List<PlayerGroup> playerGroups;
	private final ProfileLoader profileLoader;
	private final OnlinePlayersMonitor onlinePlayersMonitor;
	private boolean showQuantitiesInStacks, showFirstLastSeen = true;
	private final ShopTransactionType shopTransactionType;

	private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
	private final RelativeDateFormat relativeDateFormat = new RelativeDateFormat();
	private final ListMultimap<PlayerGroup, ItemGroup> itemGroups = ArrayListMultimap.create();
	private List<PlayerGroup> displayedPlayers;
	private ListMultimap<PlayerGroup, ItemGroup> displayedItems;
	private FilterList filteredPlayerNames = new FilterList();
	private FilterList filteredItemNames = new FilterList();
	private ItemsTable table = null;
	private Sort sort;
	private JPanel tablesPanel;

	/**
	 * @param playerGroups the players to display
	 */
	public PlayersPanel(Collection<PlayerGroup> playerGroups, ShopTransactionType shopTransactionType) {
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
		this.shopTransactionType = shopTransactionType;

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
		if (table != null) {
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
		table = null;

		final JList<PlayerGroup> list = new JList<>(new Vector<PlayerGroup>(displayedPlayers));
		list.setCellRenderer(new ListCellRenderer<PlayerGroup>() {
			private static final int profileImageSize = 32;
			private final ProfileDownloadedListener onImageDownloaded = profile -> {
				synchronized (this) {
					list.repaint();
				}
			};

			private final RupeeFormatter rf = new RupeeFormatter();
			{
				rf.setPlus(true);
			}

			@Override
			public Component getListCellRendererComponent(JList<? extends PlayerGroup> list, PlayerGroup playerGroup, int index, boolean selected, boolean hasFocus) {
				Player player = playerGroup.getPlayer();
				String playerName = player.getName();

				JPanel row = new JPanel(new MigLayout("insets 5"));
				UIDefaultsWrapper.assignListFormats(row, selected);

				JLabel profileImage = new JLabel();
				profileImage.setHorizontalAlignment(SwingConstants.CENTER);
				profileImage.setVerticalAlignment(SwingConstants.CENTER);

				synchronized (this) {
					profileLoader.getPortrait(playerName, profileImage, profileImageSize, onImageDownloaded);
				}

				row.add(profileImage, "w " + profileImageSize + "!, h " + profileImageSize + "!, span 1 2");

				JLabel playerNameLabel = new JLabel(playerName);
				EmcServer server = onlinePlayersMonitor.getPlayerServer(playerName);
				if (server != null) {
					playerNameLabel.setIcon(Images.getOnline(server, 16));
					playerNameLabel.setHorizontalTextPosition(SwingConstants.LEFT);
					playerNameLabel.setVerticalTextPosition(SwingConstants.TOP);
				}
				if (selected) {
					playerNameLabel.setForeground(UIDefaultsWrapper.getListForegroundSelected());
				} else {
					PlayerProfile profile = profileLoader.getProfile(playerName, null);
					if (profile != null) {
						String rankColorStr = profile.getRankColor();
						if (rankColorStr != null) {
							try {
								Color color = Color.decode(rankColorStr);
								playerNameLabel.setForeground(color);
							} catch (NumberFormatException e) {
								/*
								 * If the color string is not in the correct
								 * format, ignore it.
								 */
							}
						}
					}
				}
				row.add(playerNameLabel, "gapbottom 0, wrap");

				int netTotal = calculateNetTotal(playerGroup);
				JLabel rupeeTotalLabel = new JLabel(rf.format(netTotal));
				Color foreground = selected ? UIDefaultsWrapper.getListForegroundSelected() : BaseFormatter.getColor(netTotal);
				rupeeTotalLabel.setForeground(foreground);
				row.add(rupeeTotalLabel, "gaptop 0");

				return row;
			}
		});
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener(new ListSelectionListener() {
			private Integer prevSelected;

			@Override
			public void valueChanged(ListSelectionEvent arg0) {
				int selectedIndex = list.getSelectedIndex();
				if (sameSelection(selectedIndex)) {
					return;
				}

				showTable(displayedPlayers.get(selectedIndex));

				prevSelected = selectedIndex;
			}

			private boolean sameSelection(int selectedIndex) {
				return (prevSelected != null && prevSelected == selectedIndex);
			}
		});
		add(new MyJScrollPane(list), "w 450, growy");

		tablesPanel = new JPanel(new MigLayout("insets 3, fillx"));
		add(tablesPanel, "grow, w 100%, h 100%");

		validate();
	}

	private ImageIcon buildProfilePortrait(String playerName) {
		ImageIcon portrait = profileLoader.getPortrait(playerName, profileImageSize);
		if (portrait == null) {
			//user has no portrait
			return null;
		}

		EmcServer server = onlinePlayersMonitor.getPlayerServer(playerName);
		if (server == null) {
			//user is not online
			return portrait;
		}

		ImageIcon online = Images.getOnline(server, profileImageOnlineIconSize);
		if (portrait.getIconWidth() < online.getIconWidth() || portrait.getIconHeight() < online.getIconHeight()) {
			//the online icon is too big for the portrait
			return portrait;
		}

		//draw the "online" icon on top of the player portrait
		BufferedImage img = new BufferedImage(portrait.getIconWidth(), portrait.getIconHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(portrait.getImage(), 0, 0, null);
		g.drawImage(online.getImage(), img.getWidth() - online.getIconHeight(), img.getHeight() - online.getIconWidth(), null);
		g.dispose();
		return new ImageIcon(img);
	}

	private JPanel buildPlayerInfoPanel(Player player) {
		String playerName = player.getName();
		String rank = null, rankColor = null;
		String title = null;
		Date joined = null;
		PlayerProfile profile = profileLoader.getProfile(playerName, null);
		if (profile != null) {
			rank = profile.getRank();
			rankColor = profile.getRankColor();
			title = profile.getTitle();
			joined = profile.getJoined();
		}

		JPanel header = new JPanel(new MigLayout("insets 0"));
		header.setOpaque(false);

		ImageIcon portrait = buildProfilePortrait(playerName);
		if (portrait != null) {
			int profileImageRowSpan = 1;
			if (joined != null) {
				profileImageRowSpan++;
			}
			if (showFirstLastSeen) {
				if (player.getFirstSeen() != null) {
					profileImageRowSpan++;
				}
				if (player.getLastSeen() != null) {
					profileImageRowSpan++;
				}
			}

			JLabel profileImage = new JLabel(portrait);
			profileImage.setHorizontalAlignment(SwingConstants.CENTER);
			profileImage.setVerticalAlignment(SwingConstants.TOP);
			header.add(profileImage, "span 1 " + profileImageRowSpan + ", gapright 10");
		}

		JLabel playerNameLabel;
		{
			StringBuilder sb = new StringBuilder();
			sb.append("<html><span style=\"font-size:2em\"><b>").append(playerName).append("</b></span>");
			if (rank != null) {
				sb.append(" <i><b>");
				if (rankColor == null) {
					sb.append(rank);
				} else {
					sb.append("<span style=\"color:").append(rankColor).append("\">").append(rank).append("</span>");
				}
				sb.append("</i></b>");
			}
			if (title != null) {
				if (rank != null) {
					sb.append(",");
				}
				sb.append(" ").append(title);
			}

			playerNameLabel = new ClickableLabel(sb.toString(), "https://u.emc.gs/" + playerName);
			playerNameLabel.setToolTipText("View " + playerName + "'s profile");
		}
		header.add(playerNameLabel, "span 2, wrap");

		if (joined != null) {
			header.add(new JLabel("Joined:"));
			header.add(new JLabel(dateFormat.format(joined)), "wrap");
		}

		if (showFirstLastSeen) {
			Date firstSeen = player.getFirstSeen();
			if (firstSeen != null) {
				header.add(new JLabel("First seen:"));
				header.add(new JLabel(relativeDateFormat.format(firstSeen)), "wrap");
			}

			Date lastSeen = player.getLastSeen();
			if (lastSeen != null) {
				header.add(new JLabel("Last seen:"));
				header.add(new JLabel(relativeDateFormat.format(lastSeen)));
			}
		}

		return header;
	}

	private ItemsTable buildItemsTable(PlayerGroup playerGroup) {
		Column column;
		boolean ascending;
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
		default:
			column = null;
			ascending = true;
			break;
		}

		return new ItemsTable(displayedItems.get(playerGroup), column, ascending, shopTransactionType, showQuantitiesInStacks);
	}

	private void showTable(PlayerGroup playerGroup) {
		tablesPanel.removeAll();

		tablesPanel.add(buildPlayerInfoPanel(playerGroup.getPlayer()), "wrap");
		table = buildItemsTable(playerGroup);
		table.setFillsViewportHeight(true);
		MyJScrollPane pane = new MyJScrollPane(table);
		tablesPanel.add(pane, "grow, w 100%, h 100%, wrap");

		JLabel netAmount;
		{
			int amount = calculateNetTotal(playerGroup);
			RupeeFormatter rf = new RupeeFormatter();
			rf.setPlus(true);
			rf.setColor(true);

			StringBuilder sb = new StringBuilder();
			sb.append("<html><code><b>Total: ");
			sb.append(rf.format(amount));
			sb.append("</b></code></html>");
			netAmount = new JLabel(sb.toString());
		}
		tablesPanel.add(netAmount, "align right");

		tablesPanel.validate();
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

		List<PlayerGroup> filteredPlayers = new LinkedList<>();
		for (PlayerGroup playerGroup : playerGroups) {
			String playerName = playerGroup.getPlayer().getName();
			if (filteredPlayerNames.isFiltered(playerName)) {
				filteredPlayers.add(playerGroup);
			}
		}
		return filteredPlayers;
	}

	private ListMultimap<PlayerGroup, ItemGroup> filterItems(List<PlayerGroup> filteredPlayers) {
		if (filteredPlayerNames.isEmpty() && filteredItemNames.isEmpty()) {
			return itemGroups;
		}

		List<PlayerGroup> removePlayers = new ArrayList<>();
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
					if (filteredItemNames.isFiltered(itemName)) {
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
			Collections.sort(players, (a, b) -> a.getPlayer().getName().compareToIgnoreCase(b.getPlayer().getName()));

			//sort each player's item list by item name
			for (PlayerGroup group : players) {
				Collections.sort(items.get(group), (a, b) -> a.getItem().compareToIgnoreCase(b.getItem()));
			}
			break;
		case SUPPLIER:
			//sort by net sold amount ascending
			Collections.sort(players, (a, b) -> {
				int netA = 0;
				for (ItemGroup item : items.get(a)) {
					netA += item.getNetAmount();
				}

				int netB = 0;
				for (ItemGroup item : items.get(b)) {
					netB += item.getNetAmount();
				}

				return netA - netB;
			});

			//sort each player's item list by item amount ascending
			for (PlayerGroup group : players) {
				Collections.sort(items.get(group), (a, b) -> a.getNetAmount() - b.getNetAmount());
			}
			break;
		case CUSTOMER:
			//sort by net bought amount descending
			Collections.sort(players, (a, b) -> {
				int netA = 0;
				for (ItemGroup item : items.get(a)) {
					netA += item.getNetAmount();
				}

				int netB = 0;
				for (ItemGroup item : items.get(b)) {
					netB += item.getNetAmount();
				}

				return netB - netA;
			});

			//sort each player's item list by item amount descending
			for (PlayerGroup group : players) {
				Collections.sort(items.get(group), (a, b) -> b.getNetAmount() - a.getNetAmount());
			}
			break;
		}
	}

	private enum Sort {
		PLAYER, SUPPLIER, CUSTOMER
	}
}
