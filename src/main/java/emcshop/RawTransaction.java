package emcshop;

import java.util.Date;

/**
 * Represents a transaction on the transaction history page.
 * @author Michael Angstadt
 */
public class RawTransaction {
	protected Date ts;
	protected String description;
	protected int amount, balance;

	public RawTransaction() {
		//empty
	}

	public RawTransaction(RawTransaction source) {
		ts = source.ts;
		description = source.description;
		amount = source.amount;
		balance = source.balance;
	}

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
