package emcshop.gui;

import java.text.DateFormat;
import java.text.NumberFormat;
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
import emcshop.db.PlayerGroup;

/**
 * A panel that displays transactions grouped by player.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class PlayersPanel extends JPanel {
	private final List<PlayerGroup> playerGroups;
	private final Map<PlayerGroup, List<ItemGroup>> itemGroups = new HashMap<PlayerGroup, List<ItemGroup>>();
	private List<PlayerGroup> displayedPlayers;
	private Map<PlayerGroup, List<ItemGroup>> displayedItems;
	private List<String> filteredPlayerNames = new ArrayList<String>(0);
	private List<String> filteredItemNames = new ArrayList<String>(0);
	private Sort sort;

	/**
	 * Creates the panel.
	 * @param playerGroups the players to display in the table
	 */
	public PlayersPanel(Collection<PlayerGroup> playerGroups) {
		//add all the data to Lists so they can be sorted
		this.playerGroups = new ArrayList<PlayerGroup>(playerGroups);
		for (PlayerGroup playerGroup : playerGroups) {
			List<ItemGroup> itemGroups = new ArrayList<ItemGroup>(playerGroup.getItems().values());
			this.itemGroups.put(playerGroup, itemGroups);
		}
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
		removeAll();
		DateFormat df = new SimpleDateFormat("MMMM dd yyyy, HH:mm");
		for (PlayerGroup playerGroup : displayedPlayers) {
			//TODO add player icon
			add(new JLabel("<html><h3>" + playerGroup.getPlayerName() + "</h3></html>"), "span 2, wrap");

			add(new JLabel("<html>First seen:</html>"), "align right");
			add(new JLabel("<html>" + df.format(playerGroup.getFirstSeen()) + "</html>"), "wrap");

			add(new JLabel("<html>Last seen:</html>"), "align right");
			add(new JLabel("<html>" + df.format(playerGroup.getLastSeen()) + "</html>"), "wrap");

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

	private List<PlayerGroup> filterPlayers() {
		List<PlayerGroup> filteredPlayers;
		if (filteredPlayerNames.isEmpty()) {
			filteredPlayers = new LinkedList<PlayerGroup>(playerGroups);
		} else {
			filteredPlayers = new LinkedList<PlayerGroup>();
			for (PlayerGroup playerGroup : playerGroups) {
				String playerName = playerGroup.getPlayerName().toLowerCase();
				for (String filteredPlayer : filteredPlayerNames) {
					if (playerName.contains(filteredPlayer.toLowerCase())) {
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
							if (itemName.contains(filteredItem.toLowerCase())) {
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
					return a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
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

	private enum Sort {
		PLAYER, SUPPLIER, CUSTOMER
	}
}
