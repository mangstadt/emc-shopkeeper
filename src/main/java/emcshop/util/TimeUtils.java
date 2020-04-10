package emcshop.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Contains time-related utilities.
 * @author Michael Angstadt
 */
public final class TimeUtils {
	/**
	 * Splits a duration into hours, minutes, seconds, and milliseconds.
	 * @param duration the duration
	 * @return the time components, starting with milliseconds at index 0
	 */
	public static long[] parseTimeComponents(Duration duration) {
		long hours = duration.toHours();
		duration = duration.minusHours(hours);

		long minutes = duration.toMinutes();
		duration = duration.minusMinutes(minutes);

		long seconds = duration.getSeconds();

		long milliseconds = duration.getNano() / 1_000_000;

		return new long[] { milliseconds, seconds, minutes, hours };
	}

	public static LocalDate toLocalDate(Date date) {
		return (date == null) ? null : Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
	}

	public static LocalDateTime toLocalDateTime(Date date) {
		return (date == null) ? null : Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
	}

	public static Date toDate(LocalDate localDate) {
		return (localDate == null) ? null : Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	public static Date toDate(LocalDateTime localDateTime) {
		return (localDateTime == null) ? null : Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
	}

	private TimeUtils() {
		//hide
	}
}
