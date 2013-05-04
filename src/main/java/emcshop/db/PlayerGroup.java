package emcshop.db;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the transactions of a particular shop customer.
 * @author Michael Angstadt
 */
public class PlayerGroup {
	private Integer playerId;
	private String playerName;
	private Date firstSeen, lastSeen;
	private Map<String, ItemGroup> items = new HashMap<String, ItemGroup>();

	public Integer getPlayerId() {
		return playerId;
	}

	public void setPlayerId(Integer playerId) {
		this.playerId = playerId;
	}

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	/**
	 * Gets the total amount of rupees lost/gained from all of this player's
	 * transactions.
	 * @return the net rupee amount
	 */
	public int getNetAmount() {
		int total = 0;
		for (ItemGroup info : items.values()) {
			total += info.getNetAmount();
		}
		return total;
	}

	/**
	 * Gets the total amount of rupees gained from what this player bought from
	 * the shop.
	 * @return the net bought amount
	 */
	public int getNetBoughtAmount() {
		int total = 0;
		for (ItemGroup info : items.values()) {
			total += info.getBoughtAmount();
		}
		return total;
	}

	/**
	 * Gets the total amount of rupees lost from what this player sold to the
	 * shop.
	 * @return the net sold amount
	 */
	public int getNetSoldAmount() {
		int total = 0;
		for (ItemGroup info : items.values()) {
			total += info.getSoldAmount();
		}
		return total;
	}

	public Map<String, ItemGroup> getItems() {
		return items;
	}

	public Date getFirstSeen() {
		return firstSeen;
	}

	public void setFirstSeen(Date firstSeen) {
		this.firstSeen = firstSeen;
	}

	public Date getLastSeen() {
		return lastSeen;
	}

	public void setLastSeen(Date lastSeen) {
		this.lastSeen = lastSeen;
	}
}
