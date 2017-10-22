package emcshop.util;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ForwardingListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

/**
 * A multimap that has case-insensitive strings as its key (they are converted
 * to lower case).
 *
 * @param <V> the value class
 */
public class CaseInsensitiveMultimap<V> extends ForwardingListMultimap<String, V> {
    private final ListMultimap<String, V> map = ArrayListMultimap.create();

    public static <V> ListMultimap<String, V> create() {
        return new CaseInsensitiveMultimap<V>();
    }

    @Override
    public List<V> get(String key) {
        return super.get(sanitize(key));
    }

    @Override
    public boolean put(String key, V value) {
        return super.put(sanitize(key), value);
    }

    @Override
    public boolean putAll(Multimap<? extends String, ? extends V> values) {
        boolean changed = false;
        for (Map.Entry<? extends String, ? extends V> entry : values.entries()) {
            String key = entry.getKey();
            V value = entry.getValue();

            if (put(key, value)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean putAll(String key, Iterable<? extends V> values) {
        return super.putAll(sanitize(key), values);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return super.remove(sanitize(key), value);
    }

    @Override
    public List<V> removeAll(Object key) {
        return super.removeAll(sanitize(key));
    }

    @Override
    public List<V> replaceValues(String key, Iterable<? extends V> values) {
        return super.replaceValues(sanitize(key), values);
    }

    @Override
    public boolean containsEntry(Object key, Object value) {
        return super.containsEntry(sanitize(key), value);
    }

    @Override
    public boolean containsKey(Object key) {
        return super.containsKey(sanitize(key));
    }

    @Override
    protected ListMultimap<String, V> delegate() {
        return map;
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
}
