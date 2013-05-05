package emcshop.gui;

import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import emcshop.db.ItemGroup;
import emcshop.db.Player;
import emcshop.db.PlayerGroup;

/**
 * A panel that displays transactions grouped by player.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class PlayersPanel extends JPanel {
	private final List<PlayerGroup> playerGroups;
	private final Map<PlayerGroup, List<ItemGroup>> itemGroups = new HashMap<PlayerGroup, List<ItemGroup>>();
	private final ProfileImageLoader profileImageLoader;
	private List<PlayerGroup> displayedPlayers;
	private Map<PlayerGroup, List<ItemGroup>> displayedItems;
	private List<String> filteredPlayerNames = new ArrayList<String>(0);
	private List<String> filteredItemNames = new ArrayList<String>(0);
	private Sort sort;

	/**
	 * Creates the panel.
	 * @param playerGroups the players to display in the table
	 */
	public PlayersPanel(Collection<PlayerGroup> playerGroups, ProfileImageLoader profileImageLoader) {
		//add all the data to Lists so they can be sorted
		this.playerGroups = new ArrayList<PlayerGroup>(playerGroups);
		for (PlayerGroup playerGroup : playerGroups) {
			List<ItemGroup> itemGroups = new ArrayList<ItemGroup>(playerGroup.getItems().values());
			this.itemGroups.put(playerGroup, itemGroups);
		}

		this.profileImageLoader = profileImageLoader;

		setLayout(new MigLayout());
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
	 */
	public void filterByPlayers(List<String> players) {
		filteredPlayerNames = players;
		refresh();
	}

	/**
	 * Filters the data by item.
	 */
	public void filterByItems(List<String> items) {
		filteredItemNames = items;
		refresh();
	}

	public List<PlayerGroup> getDisplayedPlayers() {
		return displayedPlayers;
	}

	public Map<PlayerGroup, List<ItemGroup>> getDisplayedItems() {
		return displayedItems;
	}

	private void refresh() {
		//filter players
		displayedPlayers = filterPlayers();

		//filter items
		displayedItems = filterItems(displayedPlayers);

		//sort data
		sortData(displayedPlayers, displayedItems);

		//display data
		Map<String, JLabel> images = new HashMap<String, JLabel>();
		removeAll();
		DateFormat df = new SimpleDateFormat("MMMM dd yyyy, HH:mm");
		for (PlayerGroup playerGroup : displayedPlayers) {
			Player player = playerGroup.getPlayer();

			JLabel profileImage = new JLabel();
			images.put(player.getName(), profileImage);
			add(profileImage, "w 64!, h 64!");

			//@formatter:off
			String header =
			"<html>" +
				"<h3>" + player.getName() + "</h3>" +
				"<table>" +
					"<tr><td align=right>First seen:</td><td>" + df.format(player.getFirstSeen()) + "</td></tr>" +
					"<tr><td align=right>Last seen:</td><td>" + df.format(player.getLastSeen()) + "</td></tr>" +
				"</table>" +
			"</html>";
			//@formatter:on
			add(new JLabel(header), "wrap");

			ItemsTable table = new ItemsTable(displayedItems.get(playerGroup));
			table.getTableHeader().setReorderingAllowed(false);
			add(table.getTableHeader(), "span 2, wrap");
			add(table, "span 2, wrap");

			JLabel netAmount;
			{
				int amount = 0;
				for (ItemGroup item : displayedItems.get(playerGroup)) {
					amount += item.getNetAmount();
				}

				StringBuilder sb = new StringBuilder();
				sb.append("<html><code><b>");
				sb.append(formatRupeesWithColor(amount));
				sb.append("</b></code></html>");
				netAmount = new JLabel(sb.toString());
			}
			add(netAmount, "align right, span 2, wrap");
		}
		validate();

		profileImageLoader.load(images);
	}

	private List<PlayerGroup> filterPlayers() {
		List<PlayerGroup> filteredPlayers;
		if (filteredPlayerNames.isEmpty()) {
			filteredPlayers = new LinkedList<PlayerGroup>(playerGroups);
		} else {
			filteredPlayers = new LinkedList<PlayerGroup>();
			for (PlayerGroup playerGroup : playerGroups) {
				String playerName = playerGroup.getPlayer().getName().toLowerCase();
				for (String filteredPlayer : filteredPlayerNames) {
					filteredPlayer = filteredPlayer.toLowerCase();
					boolean add = false;
					if (filteredPlayer.startsWith("\"") && filteredPlayer.endsWith("\"")) {
						filteredPlayer = filteredPlayer.substring(1, filteredPlayer.length() - 1); //remove double quotes
						if (playerName.equals(filteredPlayer)) {
							add = true;
						}
					} else if (playerName.contains(filteredPlayer)) {
						add = true;
					}

					if (add) {
						filteredPlayers.add(playerGroup);
						break;
					}
				}
			}
		}
		return filteredPlayers;
	}

	private Map<PlayerGroup, List<ItemGroup>> filterItems(List<PlayerGroup> filteredPlayers) {
		Map<PlayerGroup, List<ItemGroup>> filteredItems;
		if (filteredPlayerNames.isEmpty() && filteredItemNames.isEmpty()) {
			filteredItems = itemGroups;
		} else {
			List<PlayerGroup> removePlayers = new ArrayList<PlayerGroup>();
			filteredItems = new HashMap<PlayerGroup, List<ItemGroup>>();
			for (PlayerGroup playerGroup : filteredPlayers) {
				List<ItemGroup> itemGroups;
				if (filteredItemNames.isEmpty()) {
					itemGroups = this.itemGroups.get(playerGroup);
					filteredItems.put(playerGroup, itemGroups);
				} else {
					itemGroups = new ArrayList<ItemGroup>();
					for (ItemGroup itemGroup : this.itemGroups.get(playerGroup)) {
						String itemName = itemGroup.getItem().toLowerCase();
						for (String filteredItem : filteredItemNames) {
							filteredItem = filteredItem.toLowerCase();
							boolean add = false;
							if (filteredItem.startsWith("\"") && filteredItem.endsWith("\"")) {
								filteredItem = filteredItem.substring(1, filteredItem.length() - 1); //remove double quotes
								if (itemName.equals(filteredItem)) {
									add = true;
								}
							} else if (itemName.contains(filteredItem)) {
								add = true;
							}

							if (add) {
								itemGroups.add(itemGroup);
								break;
							}
						}
					}
					if (itemGroups.isEmpty()) {
						removePlayers.add(playerGroup);
					} else {
						filteredItems.put(playerGroup, itemGroups);
					}
				}
			}
			for (PlayerGroup playerGroup : removePlayers) {
				filteredPlayers.remove(playerGroup);
			}
		}
		return filteredItems;
	}

	private void sortData(List<PlayerGroup> players, final Map<PlayerGroup, List<ItemGroup>> items) {
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
