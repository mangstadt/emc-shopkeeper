package emcshop.util;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ForwardingSet;

public class CaseInsensitiveHashSet extends ForwardingSet<String> {
	private final Set<String> set = new HashSet<String>();

	public static Set<String> create() {
		return new CaseInsensitiveHashSet();
	}

	@Override
	public boolean contains(Object value) {
		String s = (String) value;
		return set.contains(s.toLowerCase());
	}

	@Override
	public boolean add(String value) {
		return set.add(value.toLowerCase());
	}

	@Override
	protected Set<String> delegate() {
		return set;
	}
}
