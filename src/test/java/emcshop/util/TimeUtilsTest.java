package emcshop.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Test;

public class TimeUtilsTest {
	@Test
	public void parseTimeComponents() {
		long[] actual = TimeUtils.parseTimeComponents(50);
		long[] expected = new long[] { 50, 0, 0, 0 };
		assertArrayEquals(expected, actual);

		actual = TimeUtils.parseTimeComponents((1 * 1000) + 50);
		expected = new long[] { 50, 1, 0, 0 };
		assertArrayEquals(expected, actual);

		actual = TimeUtils.parseTimeComponents((20 * 60 * 1000) + (1 * 1000) + 50);
		expected = new long[] { 50, 1, 20, 0 };
		assertArrayEquals(expected, actual);

		actual = TimeUtils.parseTimeComponents((5 * 60 * 60 * 1000) + (20 * 60 * 1000) + (1 * 1000) + 50);
		expected = new long[] { 50, 1, 20, 5 };
		assertArrayEquals(expected, actual);
	}

	@Test
	public void zeroOutTime() throws Exception {
		assertZeroOutTime("2014-01-11 12:00:00", "2014-01-11");
		assertZeroOutTime("2014-01-11 00:00:00", "2014-01-11");
	}

	private void assertZeroOutTime(String input, String expected) throws ParseException {
		expected += " 00:00:00";

		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date inputDate = df.parse(input);
		Date expectedDate = df.parse(expected);
		Date actualDate = TimeUtils.zeroOutTime(inputDate);

		assertEquals(expectedDate, actualDate);
	}
}
