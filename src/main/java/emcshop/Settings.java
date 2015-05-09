package emcshop;

import static emcshop.util.MinecraftUtils.getDefaultMinecraftFolder;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;

import emcshop.gui.WindowState;
import emcshop.util.PropertiesWrapper;

public class Settings {
	private static final Logger logger = Logger.getLogger(Settings.class.getName());
	private static final int CURRENT_VERSION = 1;

	private final File file;

	private Integer version;
	private WindowState windowState;
	private Date previousUpdate;
	private Date lastUpdated;
	private String username;
	private String password;
	private Level logLevel;
	private Integer rupeeBalance;
	private boolean showProfilesOnStartup;
	private boolean showQuantitiesInStacks;
	private boolean backupsEnabled;
	private Integer backupFrequency;
	private Integer maxBackups;
	private File chatLogDir;

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

	public WindowState getWindowState() {
		return windowState;
	}

	public void setWindowState(WindowState windowState) {
		this.windowState = windowState;
	}

	//Removed from properties file in DB version 18.
	public Date getLastUpdated() {
		return lastUpdated;
	}

	//Removed from properties file in DB version 18.
	public Date getPreviousUpdate() {
		return previousUpdate;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Level getLogLevel() {
		return logLevel;
	}

	public void setLogLevel(Level logLevel) {
		this.logLevel = logLevel;
	}

	//Removed from properties file in DB version 17.
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

	public boolean getBackupsEnabled() {
		return backupsEnabled;
	}

	public void setBackupsEnabled(boolean enabled) {
		this.backupsEnabled = enabled;
	}

	public Integer getMaxBackups() {
		return maxBackups;
	}

	public void setMaxBackups(Integer maxBackups) {
		this.maxBackups = maxBackups;
	}

	public File getChatLogDir() {
		return chatLogDir;
	}

	public void setChatLogDir(File chatLogDir) {
		this.chatLogDir = chatLogDir;
	}

	private void defaults() {
		version = CURRENT_VERSION;
		windowState = null;
		lastUpdated = null;
		previousUpdate = null;
		username = null;
		password = null;
		logLevel = Level.INFO;
		rupeeBalance = null;
		showProfilesOnStartup = false;
		showQuantitiesInStacks = false;

		backupsEnabled = true;
		backupFrequency = 7;
		maxBackups = 10;

		File minecraft = getDefaultMinecraftFolder();
		if (minecraft == null) {
			minecraft = new File("");
		}
		chatLogDir = new File(minecraft, "logs");
	}

	public void load() throws IOException {
		PropertiesWrapper props = new PropertiesWrapper(file);

		version = props.getInteger("version", null);
		if (version != null && version < CURRENT_VERSION) {
			//migrate it
		}

		//migrate old window state settings
		{
			try {
				Integer width = props.getInteger("window.width");
				if (width != null) {
					props.setInteger("gui.window.width", width);
				}
			} catch (NumberFormatException e) {
				//ignore
			}

			try {
				Integer height = props.getInteger("window.height");
				if (height != null) {
					props.setInteger("gui.window.height", height);
				}
			} catch (NumberFormatException e) {
				//ignore
			}
		}

		try {
			windowState = props.getWindowState("gui");
		} catch (NumberFormatException e) {
			logger.log(Level.WARNING, "Problem parsing window state information: ", e);
			windowState = null;
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

		username = props.get("session.username");

		password = props.get("session.password");
		if (password != null) {
			password = new String(Base64.decodeBase64(password));
		}

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

		backupsEnabled = props.getBoolean("backup.enabled", true);

		try {
			backupFrequency = props.getInteger("backup.frequency", 7);
		} catch (NumberFormatException e) {
			logger.log(Level.WARNING, "Problem parsing backup.frequency: ", e);
			backupFrequency = 7;
		}

		try {
			maxBackups = props.getInteger("backup.max", 10);
		} catch (NumberFormatException e) {
			logger.log(Level.WARNING, "Problem parsing backup.max: ", e);
			maxBackups = 10;
		}

		String value = props.get("chatLogDir");
		if (value == null) {
			File minecraft = getDefaultMinecraftFolder();
			if (minecraft == null) {
				minecraft = new File("");
			}
			chatLogDir = new File(minecraft, "logs");
		} else {
			chatLogDir = new File(value);
		}
	}

	public void save() {
		PropertiesWrapper props = new PropertiesWrapper();

		props.setInteger("version", version);
		props.setWindowState("gui", windowState);
		props.set("session.username", username);
		props.set("session.password", (password == null) ? null : Base64.encodeBase64String(password.getBytes()));
		props.set("log.level", logLevel.getName());
		props.setBoolean("showProfilesOnStartup", showProfilesOnStartup);
		props.setBoolean("showQuantitiesInStacks", showQuantitiesInStacks);
		props.setBoolean("backup.enabled", backupsEnabled);
		props.setInteger("backup.frequency", backupFrequency);
		props.setInteger("backup.max", maxBackups);

		try {
			props.store(file, "EMC Shopkeeper settings");
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem saving settings file.", e);
		}

		if (chatLogDir != null) {
			props.set("chatLogDir", chatLogDir.getAbsolutePath());
		}
	}
}
