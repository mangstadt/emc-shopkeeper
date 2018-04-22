package emcshop.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Contains time-related utilities.
 * @author Michael Angstadt
 */
public final class TimeUtils {
	/**
	 * Splits a length of time up into hours, minutes, seconds, and
	 * milliseconds.
	 * @param totalMs the total number of milliseconds
	 * @return the time components, starting with milliseconds at index 0
	 */
	public static long[] parseTimeComponents(long totalMs) {
		long hours = totalMs / 1000 / 60 / 60;
		long remaining = totalMs % (1000 * 60 * 60);

		long minutes = remaining / 1000 / 60;
		remaining %= (1000 * 60);

		long seconds = remaining / 1000;
		remaining %= 1000;

		long milliseconds = remaining;

		return new long[] { milliseconds, seconds, minutes, hours };
	}

	/**
	 * Zeroes out the time components of a date.
	 * @param date the date
	 * @return the modified date
	 */
	public static Date zeroOutTime(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}

	private TimeUtils() {
		//hide
	}
}
