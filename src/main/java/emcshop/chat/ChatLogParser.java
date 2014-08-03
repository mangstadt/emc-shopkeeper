package emcshop.chat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
	 */
	public List<ChatMessage> getLog(Date date) throws IOException {
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
			if (m.find()) {
				Integer num = Integer.valueOf(m.group(1));
				logFiles.put(num, file);
				continue;
			}
		}

		//parse log files
		List<ChatMessage> messages = new ArrayList<ChatMessage>();
		for (File file : logFiles.values()) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), "UTF-8"));
			try {
				Calendar cal = Calendar.getInstance();
				Pattern lineRegex = Pattern.compile("^\\[(\\d\\d):(\\d\\d):(\\d\\d)\\].*?\\[CHAT\\](.*)");
				String line;
				while ((line = reader.readLine()) != null) {
					Matcher m = lineRegex.matcher(line);
					if (!m.find()) {
						continue;
					}

					int hour = Integer.parseInt(m.group(1));
					int minute = Integer.parseInt(m.group(2));
					int second = Integer.parseInt(m.group(3));

					cal.setTime(date);
					cal.add(Calendar.HOUR_OF_DAY, hour);
					cal.add(Calendar.MINUTE, minute);
					cal.add(Calendar.SECOND, second);
					Date ts = cal.getTime();

					String message = m.group(4).trim();

					messages.add(new ChatMessage(ts, message));
				}
			} finally {
				IOUtils.closeQuietly(reader);
			}
		}

		return messages;
	}

	private Date zeroOutTime(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}
}
