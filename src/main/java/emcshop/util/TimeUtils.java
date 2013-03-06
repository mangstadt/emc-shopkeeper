package emcshop.util;

public class TimeUtils {
	/**
	 * Splits a length of time up into minutes, seconds, and milliseconds.
	 * @param totalMs the total number of milliseconds
	 * @return the time components, starting with milliseconds at index 0
	 */
	public static long[] parseTimeComponents(long totalMs) {
		long milliseconds = totalMs % 1000;
		totalMs /= 1000;

		long seconds = totalMs % 60;

		long minutes = totalMs / 60;

		return new long[] { milliseconds, seconds, minutes };
	}

	private TimeUtils() {
		//hide
	}
}
