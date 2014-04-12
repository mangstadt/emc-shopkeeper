package emcshop.scraper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Contains information from a scraped rupee transaction history page.
 */
public class TransactionPage {
	private boolean loggedIn = false;
	private Integer rupeeBalance;
	private List<RupeeTransaction> transactions = new ArrayList<RupeeTransaction>();

	public boolean isLoggedIn() {
		return loggedIn;
	}

	public void setLoggedIn(boolean loggedIn) {
		this.loggedIn = loggedIn;
	}

	public Integer getRupeeBalance() {
		return rupeeBalance;
	}

	public void setRupeeBalance(Integer rupeeBalance) {
		this.rupeeBalance = rupeeBalance;
	}

	public RupeeTransactions getTransactions() {
		return new RupeeTransactions(transactions);
	}

	public void setTransactions(List<RupeeTransaction> transactions) {
		this.transactions = transactions;
	}

	/**
	 * Gets the date of the very first transaction on the page (may or may not
	 * be a shop transaction).
	 * @return the date or null if no transactions were found
	 */
	public Date getFirstTransactionDate() {
		return transactions.isEmpty() ? null : transactions.get(0).getTs();
	}
}
