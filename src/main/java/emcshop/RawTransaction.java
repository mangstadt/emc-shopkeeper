package emcshop;

import java.util.Date;

/**
 * Represents a transaction on the transaction history page.
 * @author Michael Angstadt
 */
public class RawTransaction {
	private Date ts;
	private String description;
	private int amount, balance;

	public Date getTs() {
		return ts;
	}

	public void setTs(Date ts) {
		this.ts = ts;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
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
}
