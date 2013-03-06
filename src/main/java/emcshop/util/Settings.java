package emcshop.util;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Settings {
	private static final Logger logger = Logger.getLogger(Settings.class.getName());
	private static final int CURRENT_VERSION = 1;

	private final File file;

	private Integer version;
	private Map<String, String> cookies = new HashMap<String, String>();
	private Integer windowWidth, windowHeight;
	private Date lastUpdated;

	public Settings(File file) throws IOException {
		this.file = file;

		if (file.exists()) {
			load();
		} else {
			logger.info("Creating settings file: " + file.getAbsolutePath());
			defaults();
			save();
		}
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public Map<String, String> getCookies() {
		return cookies;
	}

	public Integer getWindowWidth() {
		return windowWidth;
	}

	public void setWindowWidth(Integer windowWidth) {
		this.windowWidth = windowWidth;
	}

	public Integer getWindowHeight() {
		return windowHeight;
	}

	public void setWindowHeight(Integer windowHeight) {
		this.windowHeight = windowHeight;
	}

	public Date getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(Date lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	private void defaults() {
		version = CURRENT_VERSION;
		cookies.put("__qca", "");
		cookies.put("__gads", "");
		cookies.put("emc_user", "");
		cookies.put("emc_session", "");
		cookies.put("__utma", "");
		cookies.put("__utmb", "");
		cookies.put("__utmc", "");
		cookies.put("__utmz", "");
		windowWidth = 600;
		windowHeight = 400;
		lastUpdated = null;
	}

	public void load() throws IOException {
		PropertiesWrapper props = new PropertiesWrapper(file);

		version = props.getInteger("version", null);
		if (version != null && version < CURRENT_VERSION) {
			//migrate it
		}

		cookies = props.getMap("cookie.");
		windowWidth = props.getInteger("window.width", 600);
		windowHeight = props.getInteger("window.height", 400);
		try {
			lastUpdated = props.getDate("lastUpdated");
		} catch (ParseException e) {
			logger.log(Level.WARNING, "Problem parsing date in \"lastUpdate\" property.", e);
			lastUpdated = null;
		}
	}

	public void save() throws IOException {
		PropertiesWrapper props = new PropertiesWrapper();

		props.setInteger("version", version);
		props.setMap("cookie.", cookies);
		props.setInteger("window.width", windowWidth);
		props.setInteger("window.height", windowHeight);
		props.setDate("lastUpdated", lastUpdated);

		props.store(file, "EMC Shopkeeper settings");
	}
}
