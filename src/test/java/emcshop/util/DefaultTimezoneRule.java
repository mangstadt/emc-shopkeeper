package emcshop.util;

import java.util.SimpleTimeZone;
import java.util.TimeZone;

import org.junit.rules.ExternalResource;

/**
 * Changes the JVM's default timezone temporarily for all of the tests in a test
 * class.
 * @author Michael Angstadt
 */
public class DefaultTimezoneRule extends ExternalResource {
	private final TimeZone newTz;
	private TimeZone defaultTz;

	/**
	 * @param hour the hour component of the UTC offset
	 * @param minute the minute component of the UTC offset
	 */
	public DefaultTimezoneRule(int hour, int minute) {
		int hourMillis = 1000 * 60 * 60 * hour;
		int minuteMillis = 1000 * 60 * minute;
		if (hour < 0) {
			minuteMillis *= -1;
		}

		newTz = new SimpleTimeZone(hourMillis + minuteMillis, "");
	}
	
	/**
	 * @param tzid the timezone ID (e.g. "America/New_York")
	 */
	public DefaultTimezoneRule(String tzid) {
		newTz = TimeZone.getTimeZone(tzid);
	}

	@Override
	protected void before() {
		defaultTz = TimeZone.getDefault();
		TimeZone.setDefault(newTz);
	}

	@Override
	protected void after() {
		TimeZone.setDefault(defaultTz);
	}
}
