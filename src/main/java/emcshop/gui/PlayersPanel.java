package emcshop.gui;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
	private List<String> filteredPlayers = new ArrayList<String>(0);
	private List<String> filteredItems = new ArrayList<String>(0);

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
		//sort by player name
		Collections.sort(playerGroups, new Comparator<PlayerGroup>() {
			@Override
			public int compare(PlayerGroup a, PlayerGroup b) {
				return a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
			}
		});

		//sort each player's item list by item name
		for (PlayerGroup group : playerGroups) {
			Collections.sort(itemGroups.get(group), new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					return a.getItem().compareToIgnoreCase(b.getItem());
				}
			});
		}

		refresh();
	}

	/**
	 * Sorts the data by best supplier.
	 */
	public void sortBySuppliers() {
		//sort by net sold amount ascending
		Collections.sort(playerGroups, new Comparator<PlayerGroup>() {
			@Override
			public int compare(PlayerGroup a, PlayerGroup b) {
				return a.getNetSoldAmount() - b.getNetSoldAmount();
			}
		});

		//sort each player's item list by item amount ascending
		for (PlayerGroup group : playerGroups) {
			Collections.sort(itemGroups.get(group), new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					return a.getNetAmount() - b.getNetAmount();
				}
			});
		}

		refresh();
	}

	/**
	 * Sorts the data by best customer.
	 */
	public void sortByCustomers() {
		//sort by net bought amount descending
		Collections.sort(playerGroups, new Comparator<PlayerGroup>() {
			@Override
			public int compare(PlayerGroup a, PlayerGroup b) {
				return b.getNetBoughtAmount() - a.getNetBoughtAmount();
			}
		});

		//sort each player's item list by item amount descending
		for (PlayerGroup group : playerGroups) {
			Collections.sort(itemGroups.get(group), new Comparator<ItemGroup>() {
				@Override
				public int compare(ItemGroup a, ItemGroup b) {
					return b.getNetAmount() - a.getNetAmount();
				}
			});
		}

		refresh();
	}

	/**
	 * Filters the data by player.
	 */
	public void filterByPlayers(List<String> players) {
		filteredPlayers = players;
		refresh();
	}

	private void refresh() {
		removeAll();

		DateFormat df = new SimpleDateFormat("MMMM dd yyyy, HH:mm");
		for (PlayerGroup playerGroup : playerGroups) {
			//is this player in the filter list?
			boolean include = false;
			if (filteredPlayers.isEmpty()) {
				include = true;
			} else {
				String playerName = playerGroup.getPlayerName().toLowerCase();
				for (String filteredPlayer : filteredPlayers) {
					if (playerName.contains(filteredPlayer.toLowerCase())) {
						include = true;
						break;
					}
				}
			}
			if (!include) {
				continue;
			}

			//TODO add player icon
			add(new JLabel("<html><h3>" + playerGroup.getPlayerName() + "</h3></html>"), "span 2, wrap");

			add(new JLabel("<html>First seen:</html>"), "align right");
			add(new JLabel("<html>" + df.format(playerGroup.getFirstSeen()) + "</html>"), "wrap");

			add(new JLabel("<html>Last seen:</html>"), "align right");
			add(new JLabel("<html>" + df.format(playerGroup.getLastSeen()) + "</html>"), "wrap");

			ItemsTable table = new ItemsTable(itemGroups.get(playerGroup));
			table.getTableHeader().setReorderingAllowed(false);
			add(table.getTableHeader(), "span 2, wrap");
			add(table, "span 2, wrap");

			JLabel netAmount;
			{
				int amount = playerGroup.getNetAmount();
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
}
