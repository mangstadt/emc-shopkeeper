package emcshop.util;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import emcshop.scraper.EmcSession;

public class Settings {
	private static final Logger logger = Logger.getLogger(Settings.class.getName());
	private static final int CURRENT_VERSION = 1;

	private final File file;

	private Integer version;
	private Integer windowWidth, windowHeight;
	private Date previousUpdate;
	private Date lastUpdated;
	private EmcSession session;
	private boolean persistSession;
	private Level logLevel;
	private Integer rupeeBalance;
	private boolean showProfilesOnStartup;
	private boolean showQuantitiesInStacks;
	private Integer backupFrequency;

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

	/**
	 * Removed from properties file in DB version 18.
	 * @return
	 */
	public Date getLastUpdated() {
		return lastUpdated;
	}

	/**
	 * Removed from properties file in DB version 18.
	 * @return
	 */
	public Date getPreviousUpdate() {
		return previousUpdate;
	}

	public EmcSession getSession() {
		return session;
	}

	public void setSession(EmcSession session) {
		this.session = session;
	}

	public boolean isPersistSession() {
		return persistSession;
	}

	public void setPersistSession(boolean persistSession) {
		this.persistSession = persistSession;
	}

	public Level getLogLevel() {
		return logLevel;
	}

	public void setLogLevel(Level logLevel) {
		this.logLevel = logLevel;
	}

	/**
	 * Removed from properties file in DB version 17.
	 * @return
	 */
	public Integer getRupeeBalance() {
		return rupeeBalance;
	}

	public boolean isShowProfilesOnStartup() {
		return showProfilesOnStartup;
	}

	public void setShowProfilesOnStartup(boolean showProfilesOnStartup) {
		this.showProfilesOnStartup = showProfilesOnStartup;
	}

	public boolean isShowQuantitiesInStacks() {
		return showQuantitiesInStacks;
	}

	public void setShowQuantitiesInStacks(boolean showQuantitiesInStacks) {
		this.showQuantitiesInStacks = showQuantitiesInStacks;
	}

	public Integer getBackupFrequency() {
		return backupFrequency;
	}

	public void setBackupFrequency(Integer backupFrequency) {
		this.backupFrequency = backupFrequency;
	}

	private void defaults() {
		version = CURRENT_VERSION;
		windowWidth = 1000;
		windowHeight = 800;
		lastUpdated = null;
		previousUpdate = null;
		session = null;
		persistSession = true;
		logLevel = Level.INFO;
		rupeeBalance = null;
		showProfilesOnStartup = false;
		showQuantitiesInStacks = false;
		backupFrequency = 7;
	}

	public void load() throws IOException {
		PropertiesWrapper props = new PropertiesWrapper(file);

		version = props.getInteger("version", null);
		if (version != null && version < CURRENT_VERSION) {
			//migrate it
		}

		try {
			windowWidth = props.getInteger("window.width", 1000);
		} catch (NumberFormatException e) {
			logger.log(Level.WARNING, "Problem parsing window.width: ", e);
			windowWidth = 1000;
		}

		try {
			windowHeight = props.getInteger("window.height", 800);
		} catch (NumberFormatException e) {
			logger.log(Level.WARNING, "Problem parsing window.height: ", e);
			windowHeight = 800;
		}

		try {
			lastUpdated = props.getDate("lastUpdated");
		} catch (ParseException e) {
			logger.log(Level.WARNING, "Problem parsing date in \"lastUpdated\" property.", e);
			lastUpdated = null;
		}

		try {
			previousUpdate = props.getDate("previousUpdate");
		} catch (ParseException e) {
			logger.log(Level.WARNING, "Problem parsing date in \"previousUpdate\" property.", e);
			previousUpdate = null;
		}

		String sessionId = props.get("session.id");
		if (sessionId != null) {
			String username = props.get("session.username");
			Date created;
			try {
				created = props.getDate("session.created");
			} catch (ParseException e) {
				created = new Date();
			}
			session = new EmcSession(username, sessionId, created);
		} else {
			session = null;
		}
		persistSession = props.getBoolean("session.remember", true);

		String logLevelStr = props.get("log.level");
		if (logLevelStr == null) {
			logLevel = Level.INFO;
		} else {
			try {
				logLevel = Level.parse(logLevelStr);
			} catch (IllegalArgumentException e) {
				logger.warning("Invalid log level \"" + logLevelStr + "\" defined in settings file.  Defaulting to INFO.");
				logLevel = Level.INFO;
			}
		}

		try {
			rupeeBalance = props.getInteger("rupeeBalance");
		} catch (NumberFormatException e) {
			logger.log(Level.WARNING, "Problem parsing rupeeBalance: ", e);
			rupeeBalance = null;
		}

		showProfilesOnStartup = props.getBoolean("showProfilesOnStartup", false);

		showQuantitiesInStacks = props.getBoolean("showQuantitiesInStacks", false);

		try {
			backupFrequency = props.getInteger("backup.frequency", 7);
		} catch (NumberFormatException e) {
			logger.log(Level.WARNING, "Problem parsing backup.frequency: ", e);
			backupFrequency = 7;
		}
	}

	public void save() {
		PropertiesWrapper props = new PropertiesWrapper();

		props.setInteger("version", version);
		props.setInteger("window.width", windowWidth);
		props.setInteger("window.height", windowHeight);
		if (session != null && persistSession) {
			props.set("session.username", session.getUsername());
			props.set("session.id", session.getSessionId());
			props.setDate("session.created", session.getCreated());
		}
		props.setBoolean("session.remember", persistSession);
		props.set("log.level", logLevel.getName());
		props.setBoolean("showProfilesOnStartup", showProfilesOnStartup);
		props.setBoolean("showQuantitiesInStacks", showQuantitiesInStacks);
		props.setInteger("backup.frequency", backupFrequency);

		try {
			props.store(file, "EMC Shopkeeper settings");
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem saving settings file.", e);
		}
	}
}
