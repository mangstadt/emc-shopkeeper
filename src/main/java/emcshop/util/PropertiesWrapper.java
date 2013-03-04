package emcshop.util;

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
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

public class PropertiesWrapper {
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

	public Map<String, String> getMap(String keyStartsWith) {
		Map<String, String> map = new HashMap<String, String>();
		Pattern p = Pattern.compile("^" + Pattern.quote(keyStartsWith) + "(.*)");
		for (Map.Entry<Object, Object> entry : properties.entrySet()) {
			String key = (String) entry.getKey();
			String value = (String) entry.getValue();
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

	public void store(File file, String comment) throws IOException {
		Writer writer = null;
		try {
			writer = new FileWriter(file);
			properties.store(writer, comment);
		} finally {
			IOUtils.closeQuietly(writer);
		}
	}
}
