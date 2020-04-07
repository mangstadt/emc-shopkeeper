package emcshop.chat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts chat messages from the Minecraft log files.
 * @author Michael Angstadt
 */
public class ChatLogParser {
	private final Path logDir;

	/**
	 * @param logDir the path to the directory that contains the log files
	 * @throws IllegalArgumentException if the given path is not a directory
	 */
	public ChatLogParser(File logDir) {
		if (!logDir.isDirectory()) {
			throw new IllegalArgumentException("The specified path is not a directory: " + logDir);
		}
		this.logDir = logDir.toPath();
	}

	/**
	 * Gets the chat messages from a specific date.
	 * @param date the date
	 * @return the chat messages
	 */
	public List<ChatMessage> getLog(LocalDate date) throws IOException {
		String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		Pattern fileNameRegex = Pattern.compile("^" + dateStr + "-(\\d+)\\.log\\.gz$");

		/*
		 * Find all the log files that are named after the given date, and keep
		 * them in the correct order.
		 * 
		 * The file paths are stored in a TreeMap because sorting by filename
		 * can result in incorrect ordering (e.g. "2020-04-01-10" would come
		 * before "2020-04-01-2")
		 */
		Map<Integer, Path> logFilesMap = new TreeMap<>();
		Files.walk(logDir, 1).forEach(file -> {
			Matcher m = fileNameRegex.matcher(file.getFileName().toString());
			if (m.find()) {
				Integer num = Integer.valueOf(m.group(1));
				logFilesMap.put(num, file);
			}
		});
		List<Path> logFiles = new ArrayList<>(logFilesMap.values());

		//should the "latest.log" file be parsed as well?
		Path latest = logDir.resolve("latest.log");
		if (lastModifiedTimeMatches(latest, date)) {
			logFiles.add(latest);
		}

		//parse each log file
		List<ChatMessage> messages = new ArrayList<>();
		for (Path file : logFiles) {
			try (ChatLogFileReader reader = new ChatLogFileReader(file, date)) {
				ChatMessage message;
				while ((message = reader.readNext()) != null) {
					messages.add(message);
				}
			}
		}

		return messages;
	}

	private boolean lastModifiedTimeMatches(Path file, LocalDate date) throws IOException {
		if (!Files.exists(file)) {
			return false;
		}
		FileTime modified = Files.getLastModifiedTime(file);
		LocalDate modifiedLocalDate = modified.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return modifiedLocalDate.equals(date);
	}
}
