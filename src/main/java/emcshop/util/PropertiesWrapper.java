package emcshop.util;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import emcshop.gui.WindowState;

public class PropertiesWrapper implements Iterable<Map.Entry<String, String>> {
	private final Properties properties = new Properties();
	private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public PropertiesWrapper() {
		// empty
	}

	public PropertiesWrapper(File file) throws IOException {
		Reader reader = null;
		try {
			reader = new FileReader(file);
			properties.load(reader);
		} finally {
			IOUtils.closeQuietly(reader);
		}
	}

	public String get(String key) {
		return properties.getProperty(key);
	}

	public void set(String key, Object value) {
		if (value == null) {
			remove(key);
		} else {
			properties.setProperty(key, value.toString());
		}
	}

	public void remove(String key) {
		properties.remove(key);
	}

	public Integer getInteger(String key) {
		return getInteger(key, null);
	}

	public Integer getInteger(String key, Integer defaultValue) {
		String value = get(key);
		if (value == null) {
			return defaultValue;
		}
		return Integer.valueOf(value);
	}

	public void setInteger(String key, Integer value) {
		set(key, value);
	}

	public Date getDate(String key) throws ParseException {
		String value = get(key);
		if (value == null) {
			return null;
		}
		return df.parse(value);
	}

	public void setDate(String key, Date value) {
		set(key, (value == null) ? null : df.format(value));
	}

	public Boolean getBoolean(String key, Boolean defaultValue) {
		String value = get(key);
		if (value == null) {
			return defaultValue;
		}
		return Boolean.valueOf(value);
	}

	public void setBoolean(String key, Boolean value) {
		set(key, (value == null) ? null : value);
	}

	public Map<String, String> getMap(String keyStartsWith) {
		Map<String, String> map = new HashMap<String, String>();
		Pattern p = Pattern.compile("^" + Pattern.quote(keyStartsWith) + "(.*)");
		for (Map.Entry<String, String> entry : this) {
			String key = entry.getKey();
			String value = entry.getValue();
			Matcher m = p.matcher(key);
			if (m.find()) {
				map.put(m.group(1), value);
			}
		}
		return map;
	}

	public void setMap(String keyStartsWith, Map<String, String> map) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			set(keyStartsWith + entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Gets window state information.
	 * @param key the key
	 * @return the value or null if not found
	 */
	public WindowState getWindowState(String key) {
		//first, check to see if any properties exist
		boolean found = false;
		String find = key + ".";
		for (String k : keySet()) {
			if (k.startsWith(find)) {
				found = true;
				break;
			}
		}
		if (!found) {
			return null;
		}

		Map<String, Object> componentValues = new HashMap<String, Object>();
		Pattern keyRegex = Pattern.compile("^" + Pattern.quote(key) + "\\.(.*?)\\.(.*)");
		for (Map.Entry<String, String> entry : this) {
			Matcher m = keyRegex.matcher(entry.getKey());
			if (!m.find()) {
				continue;
			}

			String value = entry.getValue();
			String type = m.group(1).toLowerCase();
			Object guiValue;
			if ("boolean".equals(type)) {
				guiValue = Boolean.valueOf(value);
			} else if ("string".equals(type)) {
				guiValue = value;
			} else {
				continue;
			}

			String name = m.group(2);
			componentValues.put(name, guiValue);
		}

		Point location;
		{
			Integer x = getInteger(key + ".window.x");
			Integer y = getInteger(key + ".window.y");
			location = (x == null | y == null) ? null : new Point(x, y);
		}

		Dimension size;
		{
			Integer width = getInteger(key + ".window.width");
			Integer height = getInteger(key + ".window.height");
			size = (width == null || height == null) ? null : new Dimension(width, height);
		}

		Integer state = getInteger(key + ".window.state");

		return new WindowState(componentValues, location, size, state);
	}

	public void setWindowState(String key, WindowState state) {
		if (state == null) {
			//remove it
			String find = key + ".";
			for (String k : keySet()) {
				if (k.startsWith(find)) {
					remove(k);
				}
			}
			return;
		}

		for (Map.Entry<String, Object> entry : state.getComponentValues().entrySet()) {
			String type;
			Object value = entry.getValue();
			if (value instanceof Boolean) {
				type = "boolean";
			} else {
				type = "string";
			}

			String name = entry.getKey();
			set(key + "." + type + "." + name, value.toString());
		}

		Point location = state.getLocation();
		set(key + ".window.x", (location == null) ? null : (int) location.getX());
		set(key + ".window.y", (location == null) ? null : (int) location.getY());

		Dimension size = state.getSize();
		set(key + ".window.width", (size == null) ? null : (int) size.getWidth());
		set(key + ".window.height", (size == null) ? null : (int) size.getHeight());

		Integer state2 = state.getState();
		set(key + ".window.state", state2);
	}

	public void store(File file, String comment) throws IOException {
		Writer writer = null;
		try {
			writer = new FileWriter(file);
			properties.store(writer, comment);
		} finally {
			IOUtils.closeQuietly(writer);
		}
	}

	/**
	 * Gets all the property keys.
	 * @return the property keys
	 */
	public Set<String> keySet() {
		Set<Object> keySet = properties.keySet();
		Set<String> set = new HashSet<String>(keySet.size());
		for (Object k : keySet) {
			set.add((String) k);
		}
		return set;
	}

	@Override
	public Iterator<Entry<String, String>> iterator() {
		return new Iterator<Entry<String, String>>() {
			private final Iterator<Entry<Object, Object>> it = properties.entrySet().iterator();

			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public Entry<String, String> next() {
				Entry<Object, Object> entry = it.next();
				return new EntryImpl(entry);
			}

			@Override
			public void remove() {
				it.remove();
			}
		};
	}

	private static class EntryImpl implements Entry<String, String> {
		private final Entry<Object, Object> entry;

		public EntryImpl(Entry<Object, Object> entry) {
			this.entry = entry;
		}

		@Override
		public String getKey() {
			return (String) entry.getKey();
		}

		@Override
		public String getValue() {
			return (String) entry.getValue();
		}

		@Override
		public String setValue(String value) {
			return (String) entry.setValue(value);
		}
	}
}
