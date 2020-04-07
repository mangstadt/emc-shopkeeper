package emcshop.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Formats dates relative to the current time.
 * @author Michael Angstadt
 */
public class RelativeDateFormat {
	private final DateTimeFormatter df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
	private final DateTimeFormatter tf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

	/**
	 * Formats a date.
	 * @param date the date to format (this method assumes that this date is
	 * less than or equal to the current date)
	 * @return the formatted date (e.g. "Today at 1:00 PM")
	 */
	public String format(Date date) {
		return format(TimeUtils.toLocalDateTime(date));
	}

	/**
	 * Formats a date.
	 * @param date the date to format (this method assumes that this date is
	 * less than or equal to the current date)
	 * @return the formatted date (e.g. "Today at 1:00 PM")
	 */
	public String format(LocalDate date) {
		return format(date.atStartOfDay());
	}

	/**
	 * Formats a date.
	 * @param date the date to format (this method assumes that this date is
	 * less than or equal to the current date)
	 * @return the formatted date (e.g. "Today at 1:00 PM")
	 */
	public String format(LocalDateTime date) {
		LocalDateTime now = LocalDateTime.now();

		long minutesAgo = date.until(now, ChronoUnit.MINUTES);
		if (minutesAgo <= 1) {
			return "A moment ago";
		}
		if (minutesAgo <= 60) {
			return minutesAgo + " minutes ago";
		}

		long daysAgo = date.until(now, ChronoUnit.DAYS);
		if (daysAgo == 0) {
			return "Today at " + tf.format(date);
		}
		if (daysAgo == 1) {
			return "Yesterday at " + tf.format(date);
		}
		return df.format(date);
	}
}
