package emcshop;

import java.util.Date;

/**
 * Represents a shop transaction.
 * @author Michael Angstadt
 */
public class ShopTransaction {
	private Integer id;
	private Date ts;
	private String player;
	private String item;
	private int quantity;
	private int amount;
	private int balance;

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

	public String getPlayer() {
		return player;
	}

	public void setPlayer(String player) {
		this.player = player;
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + amount;
		result = prime * result + balance;
		result = prime * result + ((item == null) ? 0 : item.hashCode());
		result = prime * result + ((player == null) ? 0 : player.hashCode());
		result = prime * result + quantity;
		result = prime * result + ((ts == null) ? 0 : ts.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		ShopTransaction that = (ShopTransaction) obj;

		if (!ts.equals(that.ts)) {
			return false;
		}

		if (balance != that.balance) {
			return false;
		}

		if (amount != that.amount) {
			return false;
		}

		if (quantity != that.quantity) {
			return false;
		}

		if (!item.equals(that.item)) {
			return false;
		}

		if (!player.equals(that.player)) {
			return false;
		}

		return true;
	}

}
