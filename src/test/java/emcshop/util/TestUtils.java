package emcshop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

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
}
