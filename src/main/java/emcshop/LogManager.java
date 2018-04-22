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

public class LogManager {
	private final Logger global;
	private Level level;
	private File logFile;

	public LogManager(Level level, File logFile) throws IOException {
		this.level = level;
		this.logFile = logFile;

		java.util.logging.LogManager.getLogManager().reset();

		global = Logger.getLogger("");
		global.setLevel(level);

		FileHandler handler = new FileHandler(logFile.getAbsolutePath(), 1000000, 5, true);
		handler.setFormatter(new SimpleFormatter());
		handler.setFilter(new Filter() {
			@Override
			public boolean isLoggable(LogRecord record) {
				//only log messages from this app
				return record.getLoggerName().startsWith(EMCShopkeeper.class.getPackage().getName());
			}
		});
		global.addHandler(handler);
	}

	public Level getLevel() {
		return level;
	}

	public void setLevel(Level level) {
		this.level = level;
		global.setLevel(level);
	}

	public File getFile() {
		return logFile;
	}

	public String getEntireLog() throws IOException {
		List<File> files = Arrays.asList(logFile.getParentFile().listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.getName().startsWith(logFile.getName());
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
			sb.append(FileUtils.readFileToString(file));
		}
		return sb.toString();
	}
}
