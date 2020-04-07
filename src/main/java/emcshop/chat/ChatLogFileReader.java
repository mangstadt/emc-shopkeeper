package emcshop.chat;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import emcshop.util.OS;

/**
 * Parses the chat messages out of a Minecraft log file.
 * @author Michael Angstadt
 */
public class ChatLogFileReader implements Closeable {
	private static final Pattern lineRegex = Pattern.compile("^\\[(\\d\\d):(\\d\\d):(\\d\\d)\\].*?\\[CHAT\\](.*)");
	private final BufferedReader reader;
	private final LocalDate date;

	/**
	 * @param file the file to read
	 * @param date the date of the log file
	 * @throws IOException if there is a problem opening the file
	 */
	public ChatLogFileReader(Path file, LocalDate date) throws IOException {
		InputStream in = Files.newInputStream(file);

		/*
		 * All log files are gzipped except for the "latest.log" file, which
		 * contains log messages from the last time Minecraft was run.
		 */
		if (file.getFileName().toString().endsWith(".gz")) {
			in = new GZIPInputStream(in);
		}

		/*
		 * On my Windows desktop, some characters do not appear when using UTF-8
		 * encoding (e.g. the dot character that appears before a player's name
		 * when they PM you and they are on your friends list, and the
		 * "double arrow" character used for "residence enter" messages)
		 */
		String charset = OS.isWindows() ? "Windows-1252" : "UTF-8";
		reader = new BufferedReader(new InputStreamReader(in, charset));

		this.date = date;
	}

	/**
	 * Reads the next chat message.
	 * @return the chat message or null if the end of the file has been reached
	 * @throws IOException if there is a problem reading the file
	 */
	public ChatMessage readNext() throws IOException {
		Matcher m;
		while (true) {
			String line = reader.readLine();
			if (line == null) {
				//EOF
				return null;
			}

			m = lineRegex.matcher(line);
			if (m.find()) {
				//found a chat message
				break;
			}
		}

		int hour = Integer.parseInt(m.group(1));
		int minute = Integer.parseInt(m.group(2));
		int second = Integer.parseInt(m.group(3));
		String message = m.group(4).trim();

		LocalTime time = LocalTime.of(hour, minute, second);
		LocalDateTime ts = LocalDateTime.of(date, time);

		return new ChatMessage(ts, message);
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}
}
