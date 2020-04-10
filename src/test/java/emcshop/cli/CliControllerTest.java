package emcshop.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class CliControllerTest {
	@Test(expected = DateTimeException.class)
	public void parseDateRange_bad_date() throws Exception {
		CliController.parseDateRange("not-a-date");
	}

	@Test
	public void parseDateRange() throws Exception {
		LocalDateTime now = LocalDateTime.now();

		LocalDateTime range[] = CliController.parseDateRange("today");
		assertEquals(now.truncatedTo(ChronoUnit.DAYS), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07");
		assertEquals(LocalDateTime.of(2013, 3, 7, 0, 0, 0), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07 13:21");
		assertEquals(LocalDateTime.of(2013, 3, 7, 13, 21, 0), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07 13:21:11");
		assertEquals(LocalDateTime.of(2013, 3, 7, 13, 21, 11), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07 to today");
		assertEquals(LocalDateTime.of(2013, 3, 7, 0, 0, 0), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07 to 2013-03-08");
		assertEquals(LocalDateTime.of(2013, 3, 7, 0, 0, 0), range[0]);
		assertEquals(LocalDateTime.of(2013, 3, 9, 0, 0, 0), range[1]);

		range = CliController.parseDateRange("2013-03-07 to 2013-03-08 13:21");
		assertEquals(LocalDateTime.of(2013, 3, 7, 0, 0, 0), range[0]);
		assertEquals(LocalDateTime.of(2013, 3, 8, 13, 21, 0), range[1]);
	}

	private static void assertNow(LocalDateTime actual) {
		LocalDateTime now = LocalDateTime.now();
		assertTrue(Duration.between(now, actual).getSeconds() < 1);
	}
}
