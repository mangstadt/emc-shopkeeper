package emcshop.util;

import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

public class CaseInsensitiveMultimap<V> {
	private final ListMultimap<String, V> map = ArrayListMultimap.create();

	public List<V> get(String key) {
		return map.get(key.toLowerCase());
	}

	public boolean put(String key, V value) {
		return map.put(key.toLowerCase(), value);
	}

	public List<V> removeAll(String key) {
		return map.removeAll(key.toLowerCase());
	}

	public boolean containsKey(String key) {
		return map.containsKey(key.toLowerCase());
	}
}
