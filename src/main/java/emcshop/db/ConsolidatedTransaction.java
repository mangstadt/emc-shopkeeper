package emcshop.db;

import java.util.Date;

public class ConsolidatedTransaction {
	private Date firstTransactionDate, lastTransactionDate;
	private String player, item;
	private int quantity, amount;

	public Date getFirstTransactionDate() {
		return firstTransactionDate;
	}

	public void setFirstTransactionDate(Date firstTransactionDate) {
		this.firstTransactionDate = firstTransactionDate;
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

	public Date getLastTransactionDate() {
		return lastTransactionDate;
	}

	public void setLastTransactionDate(Date lastTransactionDate) {
		this.lastTransactionDate = lastTransactionDate;
	}
}
