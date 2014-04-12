package emcshop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.intThat;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class TestUtils {
	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public static Date date(String date) {
		if (date.length() == 10) {
			date += " 00:00:00";
		}
		try {
			return df.parse(date);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	public static void assertIntEquals(Integer expected, int actual) {
		assertNotNull(expected);
		assertEquals(expected.intValue(), actual);
	}

	public static void assertIntEquals(int expected, Integer actual) {
		assertNotNull(actual);
		assertEquals(Integer.valueOf(expected), actual);
	}

	public static Timestamp timestamp(Date date) {
		return (date == null) ? null : new Timestamp(date.getTime());
	}

	public static Date date(Timestamp timestamp) {
		return (timestamp == null) ? null : new Date(timestamp.getTime());
	}

	/**
	 * Used in Mockito expressions to perform an operation if an "int" argument
	 * is greater than or equal to the given value.
	 * @param value the value
	 * @return
	 */
	public static int gte(final int value) {
		return intThat(new BaseMatcher<Integer>() {
			@Override
			public void describeTo(Description description) {
				//empty
			}

			@Override
			public boolean matches(Object obj) {
				Integer i = (Integer) obj;
				return i >= value;
			}
		});
	}
}
