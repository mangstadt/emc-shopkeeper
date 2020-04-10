package emcshop.util;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class TimeUtilsTest {
	@Test
	public void parseTimeComponents() {
		Map<ChronoUnit, Long> actual = TimeUtils.parseTimeComponents(Duration.ofMillis(50));
		assertComponents(0, 0, 0, 50, actual);

		actual = TimeUtils.parseTimeComponents(Duration.ofMillis((1 * 1000) + 50));
		assertComponents(0, 0, 1, 50, actual);

		actual = TimeUtils.parseTimeComponents(Duration.ofMillis((20 * 60 * 1000) + (1 * 1000) + 50));
		assertComponents(0, 20, 1, 50, actual);

		actual = TimeUtils.parseTimeComponents(Duration.ofMillis((5 * 60 * 60 * 1000) + (20 * 60 * 1000) + (1 * 1000) + 50));
		assertComponents(5, 20, 1, 50, actual);
	}

	private static void assertComponents(long hour, long minute, long second, long millisecond, Map<ChronoUnit, Long> actual) {
		Map<ChronoUnit, Long> expected = new EnumMap<>(ChronoUnit.class);
		expected.put(ChronoUnit.HOURS, hour);
		expected.put(ChronoUnit.MINUTES, minute);
		expected.put(ChronoUnit.SECONDS, second);
		expected.put(ChronoUnit.MILLIS, millisecond);

		assertEquals(expected, actual);
	}
}
