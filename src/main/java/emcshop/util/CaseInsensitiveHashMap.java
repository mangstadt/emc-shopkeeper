package emcshop.util;

import java.util.HashMap;

@SuppressWarnings("serial")
public class CaseInsensitiveHashMap<V> extends HashMap<String, V> {
	@Override
	public V get(Object key) {
		String s = (String) key;
		return super.get(s.toLowerCase());
	}

	@Override
	public V put(String key, V value) {
		return super.put(key.toLowerCase(), value);
	}

	@Override
	public V remove(Object key) {
		String s = (String) key;
		return super.remove(s.toLowerCase());
	}
}
