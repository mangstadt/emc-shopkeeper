package emcshop;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.io.FileUtils;

/**
 * Manages the application's debug log.
 * @author Michael Angstadt
 */
public class LogManager {
	private final Logger global;
	private Level level;
	private File logFile;

	/**
	 * @param level the log level
	 * @param logFile the path to the log file
	 * @throws IOException if there's a problem starting the logger
	 */
	public LogManager(Level level, File logFile) throws IOException {
		this.level = level;
		this.logFile = logFile;

		java.util.logging.LogManager.getLogManager().reset();

		global = Logger.getLogger("");
		global.setLevel(level);

		FileHandler handler = new FileHandler(logFile.getAbsolutePath(), 1000000, 5, true);
		handler.setFormatter(new SimpleFormatter());

		//only log messages from this app
		handler.setFilter(new Filter() {
			private final String appBasePackage = EMCShopkeeper.class.getPackage().getName();

			@Override
			public boolean isLoggable(LogRecord record) {
				return record.getLoggerName().startsWith(appBasePackage);
			}
		});

		global.addHandler(handler);
	}

	/**
	 * Gets the log level.
	 * @return the log level
	 */
	public Level getLevel() {
		return level;
	}

	/**
	 * Changes the log level.
	 * @param level the log level
	 */
	public void setLevel(Level level) {
		this.level = level;
		global.setLevel(level);
	}

	/**
	 * Gets the base name of the log file.
	 * @return the base name of the log file
	 */
	public File getFile() {
		return logFile;
	}

	/**
	 * Gets the contents of the entire log.
	 * @return the contents of the entire log
	 */
	public String getEntireLog() {
		/*
		 * Java breaks the log up into multiple files when the max file size has
		 * been reached. Each file name starts with the base file name that we
		 * passed into the logging API, followed by a dot, followed by a number.
		 * 
		 * A lock file exists while the app is running. It is empty. The lock
		 * file name also starts with the base file name that we passed into the
		 * logging API, and ends with ".lck".
		 */
		List<File> files = Arrays.asList(logFile.getParentFile().listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				String name = file.getName();
				return name.startsWith(logFile.getName()) && !name.endsWith(".lck");
			}
		}));

		//sort by last modified time ascending
		Collections.sort(files, new Comparator<File>() {
			@Override
			public int compare(File one, File two) {
				/*
				 * Do not cast the difference as an int and return that, since
				 * it's technically possible that an overflow could occur.
				 */
				long diff = one.lastModified() - two.lastModified();
				return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
			}
		});

		StringBuilder sb = new StringBuilder();
		for (File file : files) {
			try {
				sb.append(FileUtils.readFileToString(file));
			} catch (IOException e) {
				sb.append("===ERROR: Could not open log file: " + file).append(System.getProperty("line.separator"));
			}
		}
		return sb.toString();
	}
}
