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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + amount;
		result = prime * result + balance;
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((ts == null) ? 0 : ts.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		RawTransaction other = (RawTransaction) obj;
		if (amount != other.amount) return false;
		if (balance != other.balance) return false;
		if (description == null) {
			if (other.description != null) return false;
		} else if (!description.equals(other.description)) return false;
		if (ts == null) {
			if (other.ts != null) return false;
		} else if (!ts.equals(other.ts)) return false;
		return true;
	}

	@Override
	public String toString() {
		return "RawTransaction [ts=" + ts + ", description=" + description + ", amount=" + amount + ", balance=" + balance + "]";
	}
}
