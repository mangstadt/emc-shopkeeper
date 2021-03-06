package emcshop.util;

import java.awt.Dimension;
import java.awt.Point;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import emcshop.gui.WindowState;

public class PropertiesWrapper implements Iterable<Map.Entry<String, String>> {
	private final Properties properties = new Properties();
	private final DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public PropertiesWrapper() {
		// empty
	}

	public PropertiesWrapper(Path file) throws IOException {
		this(file, Charset.defaultCharset());
	}

	public PropertiesWrapper(Path file, Charset charset) throws IOException {
		try (Reader reader = Files.newBufferedReader(file, charset)) {
			properties.load(reader);
		}
	}

	public String get(String key) {
		return properties.getProperty(key);
	}

	public String get(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}

	public List<String> list(String key) {
		String value = get(key);
		String split[] = (value == null || value.isEmpty()) ? new String[0] : value.split("\\s*,\\s*");
		return new ArrayList<>(Arrays.asList(split));
	}

	public void list(String key, List<String> list) {
		set(key, String.join(", ", list));
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
		return (value == null) ? defaultValue : Integer.valueOf(value);
	}

	public void setInteger(String key, Integer value) {
		set(key, value);
	}

	public LocalDateTime getDate(String key) throws DateTimeException {
		String value = get(key);
		return (value == null) ? null : LocalDateTime.from(df.parse(value));
	}

	public void setDate(String key, LocalDateTime value) {
		set(key, (value == null) ? null : df.format(value));
	}

	public Boolean getBoolean(String key, Boolean defaultValue) {
		String value = get(key);
		return (value == null) ? defaultValue : Boolean.valueOf(value);
	}

	public void setBoolean(String key, Boolean value) {
		set(key, value);
	}

	public Map<String, String> getMap(String keyStartsWith) {
		Map<String, String> map = new HashMap<>();
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
		String find = key + '.';
		boolean found = keySet().stream().anyMatch(k -> k.startsWith(find));
		if (!found) {
			return null;
		}

		Map<String, Object> componentValues = new HashMap<>();
		Pattern keyRegex = Pattern.compile("^" + Pattern.quote(key) + "\\.(.*?)\\.(.*)");
		for (Map.Entry<String, String> entry : this) {
			Matcher m = keyRegex.matcher(entry.getKey());
			if (!m.find()) {
				continue;
			}

			String value = entry.getValue();
			String type = m.group(1).toLowerCase();
			Object guiValue;
			switch (type) {
			case "boolean":
				guiValue = Boolean.valueOf(value);
				break;
			case "date":
				//the date picker controls use java.util.Date
				try {
					guiValue = TimeUtils.toDate(LocalDateTime.from(df.parse(value)));
				} catch (DateTimeException e) {
					continue;
				}
				break;
			case "string":
				guiValue = value;
				break;
			default:
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
			keySet().stream().filter(k -> k.startsWith(find)).forEach(this::remove);
			return;
		}

		for (Map.Entry<String, Object> entry : state.getComponentValues().entrySet()) {
			String type, strValue;
			Object value = entry.getValue();
			if (value instanceof Boolean) {
				type = "boolean";
				strValue = value.toString();
			} else if (value instanceof Date) {
				//the date picker controls use java.util.Date
				type = "date";
				LocalDateTime date = TimeUtils.toLocalDateTime((Date) value);
				strValue = df.format(date);
			} else {
				type = "string";
				strValue = value.toString();
			}

			String name = entry.getKey();
			set(key + "." + type + "." + name, strValue);
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

	public void store(Path file, String comment) throws IOException {
		try (Writer writer = Files.newBufferedWriter(file, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
			properties.store(writer, comment);
		}
	}

	/**
	 * Gets all the property keys.
	 * @return the property keys
	 */
	public Set<String> keySet() {
		Set<Object> keySet = properties.keySet();
		return keySet.stream().map(k -> (String)k).collect(Collectors.toSet());
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
				return new AbstractMap.SimpleEntry(entry.getKey(), entry.getValue());
			}

			@Override
			public void remove() {
				it.remove();
			}
		};
	}
}
