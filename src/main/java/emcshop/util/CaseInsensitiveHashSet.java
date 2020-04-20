package emcshop.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ForwardingSet;

/**
 * A set of strings where case is ignored (all values are converted to lower
 * case).
 */
public class CaseInsensitiveHashSet extends ForwardingSet<String> {
	private final Set<String> set = new HashSet<>();

	public static Set<String> create() {
		return new CaseInsensitiveHashSet();
	}

	@Override
	public boolean contains(Object value) {
		return super.contains(sanitize(value));
	}

	@Override
	public boolean containsAll(Collection<?> values) {
		return super.containsAll(sanitize(values));
	}

	@Override
	public boolean add(String value) {
		return super.add(sanitize(value));
	}

	@Override
	public boolean addAll(Collection<? extends String> values) {
		return super.addAll(sanitizeStr(values));
	}

	@Override
	public boolean remove(Object value) {
		return super.remove(sanitize(value));
	}

	@Override
	public boolean removeAll(Collection<?> values) {
		return super.removeAll(sanitize(values));
	}

	@Override
	public boolean retainAll(Collection<?> values) {
		return super.retainAll(sanitize(values));
	}

	@Override
	protected Set<String> delegate() {
		return set;
	}

	private String sanitize(String value) {
		return value.toLowerCase();
	}

	private Object sanitize(Object value) {
		if (value instanceof String) {
			return sanitize((String) value);
		}
		return value;
	}

	private Collection<Object> sanitize(Collection<?> values) {
		return values.stream() //@formatter:off
			.map(this::sanitize)
		.collect(Collectors.toList()); //@formatter:on
	}

	private Collection<String> sanitizeStr(Collection<? extends String> values) {
		return values.stream() //@formatter:off
			.map(this::sanitize)
		.collect(Collectors.toList()); //@formatter:on
	}
}
