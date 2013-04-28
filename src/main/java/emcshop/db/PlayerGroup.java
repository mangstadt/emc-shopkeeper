package emcshop.db;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Michael Angstadt
 */
public class PlayerGroup {
	private String playerName;
	private Date firstSeen, lastSeen;
	private List<ItemInfo> items = new ArrayList<ItemInfo>();

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public void addItem(String name, int quantity, int amount) {
		ItemInfo info = new ItemInfo();
		info.setItem(name);
		info.setQuantity(quantity);
		info.setAmount(amount);
		items.add(info);
	}

	public int getNetAmount() {
		int total = 0;
		for (ItemInfo info : items) {
			total += info.getAmount();
		}
		return total;
	}

	public int getNetBoughtAmount() {
		int total = 0;
		for (ItemInfo info : items) {
			int amount = info.getAmount();
			if (amount > 0) {
				total += amount;
			}
		}
		return total;
	}

	public int getNetSoldAmount() {
		int total = 0;
		for (ItemInfo info : items) {
			int amount = info.getAmount();
			if (amount < 0) {
				total += amount;
			}
		}
		return total;
	}

	public List<ItemInfo> getItems() {
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

	public static class ItemInfo {
		private String item;
		private int quantity;
		private int amount;

		public String getItem() {
			return item;
		}

		public void setItem(String item) {
			this.item = item;
		}

		public int getQuantity() {
			return quantity;
		}

		public void setQuantity(int quantity) {
			this.quantity = quantity;
		}

		public int getAmount() {
			return amount;
		}

		public void setAmount(int amount) {
			this.amount = amount;
		}
	}
}
