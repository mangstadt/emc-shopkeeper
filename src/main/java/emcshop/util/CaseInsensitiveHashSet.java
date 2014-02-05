package emcshop.util;

import java.util.HashSet;

@SuppressWarnings("serial")
public class CaseInsensitiveHashSet extends HashSet<String> {
	@Override
	public boolean contains(Object value) {
		String s = (String) value;
		return super.contains(s.toLowerCase());
	}

	@Override
	public boolean add(String value) {
		return super.add(value.toLowerCase());
	}
}
