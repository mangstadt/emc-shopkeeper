package emcshop.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/**
 * Formats dates relative to the current time.
 * @author Michael Angstadt
 */
public class RelativeDateFormat {
	private static final RelativeDateFormat INSTANCE = new RelativeDateFormat(LocalDateTime::now);

	private final Supplier<LocalDateTime> now;
	private final DateTimeFormatter df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
	private final DateTimeFormatter tf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

	/**
	 * Gets the singleton instance of this class. This class is thread-safe.
	 * @return the instance
	 */
	public static RelativeDateFormat instance() {
		return INSTANCE;
	}

	/**
	 * This constructor is here for unit testing.
	 */
	RelativeDateFormat(Supplier<LocalDateTime> now) {
		this.now = now;
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
		LocalDateTime now = this.now.get();

		long minutesAgo = date.until(now, ChronoUnit.MINUTES);
		if (minutesAgo <= 1) {
			return "A moment ago";
		}
		if (minutesAgo <= 60) {
			return minutesAgo + " minutes ago";
		}

		/*
		 * Convert to LocalDate instances so that only the date components are
		 * taken into consideration.
		 * 
		 * For example, if it's 4/5/2020 12PM now, we want 4/4/2020 6PM to be
		 * considered "yesterday", even though it is less than 24 hours ago.
		 */
		long daysAgo = date.toLocalDate().until(now.toLocalDate(), ChronoUnit.DAYS);

		if (daysAgo == 0) {
			return "Today at " + tf.format(date);
		}
		if (daysAgo == 1) {
			return "Yesterday at " + tf.format(date);
		}
		return df.format(date);
	}
}
