package emcshop;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages the application's debug log.
 * @author Michael Angstadt
 */
public class LogManager {
	private final Logger global;
	private final Path baseLogFile;
	private Level level;

	/**
	 * @param level the log level
	 * @param baseLogFile the path to the log file
	 * @throws IOException if there's a problem starting the logger
	 */
	public LogManager(Level level, File baseLogFile) throws IOException {
		this.level = level;
		this.baseLogFile = baseLogFile.toPath();

		java.util.logging.LogManager.getLogManager().reset();

		global = Logger.getLogger("");
		global.setLevel(level);

		FileHandler handler = new FileHandler(baseLogFile.getAbsolutePath(), 1_000_000, 5, true);
		handler.setFormatter(new SimpleFormatter());

		//only log messages from this app
		String appBasePackage = EMCShopkeeper.class.getPackage().getName();
		handler.setFilter(record -> record.getLoggerName().startsWith(appBasePackage));

		global.addHandler(handler);
	}

	/**
	 * For unit testing.
	 */
	LogManager(Path baseLogFile) {
		this.baseLogFile = baseLogFile;
		global = null;
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
	public Path getFile() {
		return baseLogFile;
	}

	/**
	 * Gets the contents of the entire log.
	 * @return the contents of the entire log
	 * @throws IOException if there's a problem reading the log file(s)
	 */
	public String getCombinedLogFiles() throws IOException {
		Path parent = baseLogFile.getParent();
		if (parent == null) {
			parent = baseLogFile.resolveSibling(".");
		}
		final String NL = System.lineSeparator();

		/*
		 * The logger is configured to break the log up into multiple files when
		 * the max file size has been reached. The log files are rotated so they
		 * must be sorted by date modified to determine their chronological
		 * order (not by the number at the end of their file name).
		 * 
		 * The name of each log file starts with the base file name that we
		 * passed into the logging API, followed by a dot, followed by a number
		 * (e.g. "app.log.0", "app.log.1", etc).
		 * 
		 * A lock file exists while the app is running. The lock file name is
		 * the same as the currently active log file, with ".lck" appended to
		 * the end (e.g. "app.log.0.lck").
		 */
		Predicate<String> fileNameRegex = Pattern.compile("^" + Pattern.quote(baseLogFile.getFileName().toString()) + "\\.\\d+$").asPredicate();
		Predicate<Path> isLogFile = file -> {
			String name = file.getFileName().toString();
			return fileNameRegex.test(name);
		};

		Comparator<Path> byLastModified = (a, b) -> {
			try {
				return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};

		Function<Path, String> readFileContentsToString = file -> {
			try {
				return new String(readAllBytes(file));
			} catch (IOException e) {
				return //@formatter:off
					"========================================================" + NL +
					"========================================================" + NL +
					"ERROR: Could not open log file: " + file + NL +
					e.getMessage() + NL +
					"========================================================" + NL +
					"========================================================"; //@formatter:on
			}
		};

		try {
			return Files.list(parent) //@formatter:off
				.filter(isLogFile)
				.sorted(byLastModified)
				.map(readFileContentsToString)
			.collect(Collectors.joining(NL)); //@formatter:on
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * For unit testing.
	 */
	byte[] readAllBytes(Path file) throws IOException {
		return Files.readAllBytes(file);
	}
}
