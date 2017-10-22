package emcshop.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ForwardingSet;

/**
 * A set of strings where case is ignored (all values are converted to lower
 * case).
 */
public class CaseInsensitiveHashSet extends ForwardingSet<String> {
    private final Set<String> set = new HashSet<String>();

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
        Collection<Object> sanitized = new ArrayList<Object>(values.size());
        for (Object value : values) {
            Object s = sanitize(value);
            sanitized.add(s);
        }
        return sanitized;
    }

    private Collection<String> sanitizeStr(Collection<? extends String> values) {
        Collection<String> sanitized = new ArrayList<String>(values.size());
        for (String value : values) {
            String s = sanitize(value);
            sanitized.add(s);
        }
        return sanitized;
    }
}
