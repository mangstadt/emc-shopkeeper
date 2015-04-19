package emcshop.chat;

import static emcshop.util.TimeUtils.zeroOutTime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;

/**
 * Parses chat logs.
 */
public class ChatLogParser {
	private final File logDir;

	/**
	 * Creates a chat log parser.
	 * @param logDir the "logs" directory
	 */
	public ChatLogParser(File logDir) {
		this.logDir = logDir;
	}

	/**
	 * Gets the chat messages from a specific date.
	 * @param date the date
	 * @return the chat messages
	 */
	public List<ChatMessage> getLog(Date date) throws IOException {
		if (!logDir.isDirectory()) {
			return Collections.emptyList();
		}

		//zero-out time values of date
		date = zeroOutTime(date);

		String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(date);
		Pattern fileNameRegex = Pattern.compile("^" + dateStr + "-(\\d+)\\.log\\.gz$");

		//get the log files for this date
		Map<Integer, File> logFiles = new TreeMap<Integer, File>();
		for (File file : logDir.listFiles()) {
			if (!file.isFile()) {
				continue;
			}

			String fileName = file.getName();
			if (fileName.equals("latest.log")) {
				//if "latest.log" was last modified at the given date, then include it
				Date lastModified = new Date(file.lastModified());
				lastModified = zeroOutTime(lastModified);
				if (lastModified.equals(date)) {
					logFiles.put(Integer.MAX_VALUE, file);
				}
				continue;
			}

			Matcher m = fileNameRegex.matcher(fileName);
			if (!m.find()) {
				//file name doesn't match pattern
				continue;
			}

			Integer num = Integer.valueOf(m.group(1));
			logFiles.put(num, file);
		}

		//parse log files
		List<ChatMessage> messages = new ArrayList<ChatMessage>();
		for (File file : logFiles.values()) {
			InputStream in = new FileInputStream(file);
			if (file.getName().endsWith(".gz")) {
				in = new GZIPInputStream(in);
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			try {
				Calendar cal = Calendar.getInstance();
				String line;
				while ((line = reader.readLine()) != null) {
					Line parsedLine = Line.parse(line);
					if (parsedLine == null) {
						continue;
					}

					cal.setTime(date);
					cal.add(Calendar.HOUR_OF_DAY, parsedLine.hour);
					cal.add(Calendar.MINUTE, parsedLine.minute);
					cal.add(Calendar.SECOND, parsedLine.second);
					Date ts = cal.getTime();

					messages.add(new ChatMessage(ts, parsedLine.message));
				}
			} finally {
				IOUtils.closeQuietly(reader);
			}
		}

		return messages;
	}

	private static class Line {
		private final int hour, minute, second;
		private final String message;

		public Line(int hour, int minute, int second, String message) {
			this.hour = hour;
			this.minute = minute;
			this.second = second;
			this.message = message;
		}

		private static final Pattern lineRegex = Pattern.compile("^\\[(\\d\\d):(\\d\\d):(\\d\\d)\\].*?\\[CHAT\\](.*)");

		public static Line parse(String line) {
			Matcher m = lineRegex.matcher(line);
			if (!m.find()) {
				return null;
			}

			int hour = Integer.parseInt(m.group(1));
			int minute = Integer.parseInt(m.group(2));
			int second = Integer.parseInt(m.group(3));
			String message = m.group(4).trim();

			return new Line(hour, minute, second, message);
		}
	}
}
