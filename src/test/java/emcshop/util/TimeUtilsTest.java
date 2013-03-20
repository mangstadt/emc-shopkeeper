package emcshop.util;

import static org.junit.Assert.assertArrayEquals;

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
}
