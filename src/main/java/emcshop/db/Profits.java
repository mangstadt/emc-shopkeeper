package emcshop.db;

import java.util.HashMap;
import java.util.Map;

public class Profits {
	private int customer, supplier;
	private Map<String, Integer> groupCustomer = new HashMap<String, Integer>();
	private Map<String, Integer> groupSupplier = new HashMap<String, Integer>();

	public int getCustomer() {
		return customer;
	}

	public void setCustomer(int customer) {
		this.customer = customer;
	}

	public int getSupplier() {
		return supplier;
	}

	public void setSupplier(int supplier) {
		this.supplier = supplier;
	}

	public void putGroup(String group, int amount) {
		Map<String, Integer> map = (amount > 0) ? groupCustomer : groupSupplier;
		Integer value = map.get(group);
		if (value == null) {
			value = 0;
		}
		value += amount;
		map.put(group, value);
	}

	public Map<String, Integer> getGroupCustomer() {
		return groupCustomer;
	}

	public Map<String, Integer> getGroupSupplier() {
		return groupSupplier;
	}

}
