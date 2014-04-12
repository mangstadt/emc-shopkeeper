package emcshop.scraper;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class RupeeTransactions extends ArrayList<RupeeTransaction> {
	public RupeeTransactions() {
		super();
	}

	public RupeeTransactions(List<? extends RupeeTransaction> list) {
		super(list);
	}

	/**
	 * Gets all instances of a specific sub-class of {@link RupeeTransaction}.
	 * @param <T> the class to find
	 * @param filterBy the class to find
	 * @return the instances
	 */
	public <T extends RupeeTransaction> List<T> find(Class<T> filterBy) {
		List<T> filtered = new ArrayList<T>();
		for (RupeeTransaction transaction : this) {
			if (transaction.getClass() == filterBy) {
				T t = filterBy.cast(transaction);
				filtered.add(t);
			}
		}
		return filtered;
	}
}
