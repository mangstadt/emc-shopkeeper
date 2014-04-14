package emcshop.util;

import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ForwardingListMultimap;
import com.google.common.collect.ListMultimap;

public class CaseInsensitiveMultimap<V> extends ForwardingListMultimap<String, V> {
	private final ListMultimap<String, V> map = ArrayListMultimap.create();

	public static <V> ListMultimap<String, V> create() {
		return new CaseInsensitiveMultimap<V>();
	}

	@Override
	public List<V> get(String key) {
		return map.get(key.toLowerCase());
	}

	@Override
	public boolean put(String key, V value) {
		return map.put(key.toLowerCase(), value);
	}

	@Override
	public List<V> removeAll(Object key) {
		String s = (String) key;
		return map.removeAll(s.toLowerCase());
	}

	@Override
	public boolean containsKey(Object key) {
		String s = (String) key;
		return map.containsKey(s.toLowerCase());
	}

	@Override
	protected ListMultimap<String, V> delegate() {
		return map;
	}
}
