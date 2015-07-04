package emcshop.db;

import java.util.Date;

/**
 * Represents a shop transaction.
 * @author Michael Angstadt
 */
public class ShopTransactionDb {
	private Integer id;
	private Date ts;
	private int amount, balance;
	private String player;
	private String shopOwner;
	private String item;
	private int quantity;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Date getTs() {
		return ts;
	}

	public void setTs(Date ts) {
		this.ts = ts;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}

	public int getBalance() {
		return balance;
	}

	public void setBalance(int balance) {
		this.balance = balance;
	}

	public String getPlayer() {
		return player;
	}

	public void setPlayer(String player) {
		this.player = player;
	}

	public String getShopOwner() {
		return shopOwner;
	}

	public void setShopOwner(String shopOwner) {
		this.shopOwner = shopOwner;
	}

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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + amount;
		result = prime * result + balance;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((item == null) ? 0 : item.hashCode());
		result = prime * result + ((player == null) ? 0 : player.hashCode());
		result = prime * result + quantity;
		result = prime * result + ((shopOwner == null) ? 0 : shopOwner.hashCode());
		result = prime * result + ((ts == null) ? 0 : ts.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		ShopTransactionDb other = (ShopTransactionDb) obj;
		if (amount != other.amount) return false;
		if (balance != other.balance) return false;
		if (id == null) {
			if (other.id != null) return false;
		} else if (!id.equals(other.id)) return false;
		if (item == null) {
			if (other.item != null) return false;
		} else if (!item.equals(other.item)) return false;
		if (player == null) {
			if (other.player != null) return false;
		} else if (!player.equals(other.player)) return false;
		if (quantity != other.quantity) return false;
		if (shopOwner == null) {
			if (other.shopOwner != null) return false;
		} else if (!shopOwner.equals(other.shopOwner)) return false;
		if (ts == null) {
			if (other.ts != null) return false;
		} else if (!ts.equals(other.ts)) return false;
		return true;
	}
}
