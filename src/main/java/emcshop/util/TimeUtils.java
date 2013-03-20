package emcshop.util;

/**
 * Contains time-related utilities.
 * @author Michael Angstadt
 */
public class TimeUtils {
	/**
	 * Splits a length of time up into minutes, seconds, and milliseconds.
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

	private TimeUtils() {
		//hide
	}
}
