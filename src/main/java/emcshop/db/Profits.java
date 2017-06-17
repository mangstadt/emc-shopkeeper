package emcshop.db;

import java.util.HashMap;
import java.util.Map;

public class Profits {
	private final Map<String, Integer> customerTotals = new HashMap<String, Integer>();
	private final Map<String, Integer> supplierTotals = new HashMap<String, Integer>();
	private int balance;

	public void addTransaction(String item, int amount) {
		item = item.toLowerCase();
		Map<String, Integer> map = (amount > 0) ? customerTotals : supplierTotals;
		Integer value = map.get(item);
		if (value == null) {
			value = 0;
		}
		value += amount;
		map.put(item, value);
	}

	public boolean hasTransactions() {
		return !customerTotals.isEmpty() || !supplierTotals.isEmpty();
	}

	public Map<String, Integer> getCustomerTotals() {
		return customerTotals;
	}

	public Map<String, Integer> getSupplierTotals() {
		return supplierTotals;
	}

	public int getCustomerTotal() {
		return getTotal(customerTotals);
	}

	public int getSupplierTotal() {
		return getTotal(supplierTotals);
	}

	public int getBalance() {
		return balance;
	}

	public void setBalance(int balance) {
		this.balance = balance;
	}

	private int getTotal(Map<String, Integer> map) {
		int total = 0;
		for (Integer value : map.values()) {
			total += value;
		}
		return total;
	}
}
