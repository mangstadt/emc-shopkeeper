package emcshop.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

/**
 * Contains time-related utilities.
 * @author Michael Angstadt
 */
public final class TimeUtils {
	/**
	 * <p>
	 * Splits a duration into hours, minutes, seconds, and milliseconds.
	 * </p>
	 * <p>
	 * For example, a duration of 50.5 minutes would return a map containing 0
	 * hours, 50 minutes, 30 seconds and 0 milliseconds.
	 * </p>
	 * @param duration the duration
	 * @return the time components (hours, minutes, seconds, milliseconds)
	 */
	public static Map<ChronoUnit, Long> parseTimeComponents(Duration duration) {
		long hours = duration.toHours();
		duration = duration.minusHours(hours);

		long minutes = duration.toMinutes();
		duration = duration.minusMinutes(minutes);

		long seconds = duration.getSeconds();

		long milliseconds = duration.getNano() / 1_000_000;

		Map<ChronoUnit, Long> components = new EnumMap<>(ChronoUnit.class);
		components.put(ChronoUnit.HOURS, hours);
		components.put(ChronoUnit.MINUTES, minutes);
		components.put(ChronoUnit.SECONDS, seconds);
		components.put(ChronoUnit.MILLIS, milliseconds);
		return components;
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
