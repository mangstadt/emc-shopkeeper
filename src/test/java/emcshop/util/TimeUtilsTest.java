package emcshop.util;

import static org.junit.Assert.assertArrayEquals;

import java.time.Duration;

import org.junit.Test;

public class TimeUtilsTest {
	@Test
	public void parseTimeComponents() {
		long[] actual = TimeUtils.parseTimeComponents(Duration.ofMillis(50));
		long[] expected = new long[] { 50, 0, 0, 0 };
		assertArrayEquals(expected, actual);

		actual = TimeUtils.parseTimeComponents(Duration.ofMillis((1 * 1000) + 50));
		expected = new long[] { 50, 1, 0, 0 };
		assertArrayEquals(expected, actual);

		actual = TimeUtils.parseTimeComponents(Duration.ofMillis((20 * 60 * 1000) + (1 * 1000) + 50));
		expected = new long[] { 50, 1, 20, 0 };
		assertArrayEquals(expected, actual);

		actual = TimeUtils.parseTimeComponents(Duration.ofMillis((5 * 60 * 60 * 1000) + (20 * 60 * 1000) + (1 * 1000) + 50));
		expected = new long[] { 50, 1, 20, 5 };
		assertArrayEquals(expected, actual);
	}
}
