package emcshop.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import emcshop.db.DbDao;

/**
 * @author Michael Angstadt
 */
public class CliControllerTest {
	@Test(expected = DateTimeException.class)
	public void parseDateRange_bad_date() throws Exception {
		DbDao dao = mock(DbDao.class);

		CliController.parseDateRange("not-a-date", dao);
	}

	@Test
	public void parseDateRange_today() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		DbDao dao = mock(DbDao.class);

		LocalDateTime range[] = CliController.parseDateRange("today", dao);
		assertEquals(now.truncatedTo(ChronoUnit.DAYS), range[0]);
		assertNow(range[1]);
	}

	@Test
	public void parseDateRange_since_last_update() throws Exception {
		DbDao dao = mock(DbDao.class);

		LocalDateTime latestUpdate = LocalDateTime.of(2020, 4, 13, 12, 0, 0);
		when(dao.getLatestUpdateDate()).thenReturn(latestUpdate);

		LocalDateTime secondLatestUpdate = LocalDateTime.of(2020, 4, 10, 12, 0, 0);
		when(dao.getSecondLatestUpdateDate()).thenReturn(secondLatestUpdate);

		LocalDateTime range[] = CliController.parseDateRange("since last update", dao);
		assertEquals(secondLatestUpdate, range[0]);
		assertEquals(latestUpdate, range[1]);
	}

	@Test
	public void parseDateRange_dates() throws Exception {
		DbDao dao = mock(DbDao.class);

		LocalDateTime[] range = CliController.parseDateRange("2013-03-07", dao);
		assertEquals(LocalDateTime.of(2013, 3, 7, 0, 0, 0), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07 13:21", dao);
		assertEquals(LocalDateTime.of(2013, 3, 7, 13, 21, 0), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07 13:21:11", dao);
		assertEquals(LocalDateTime.of(2013, 3, 7, 13, 21, 11), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07 to today", dao);
		assertEquals(LocalDateTime.of(2013, 3, 7, 0, 0, 0), range[0]);
		assertNow(range[1]);

		range = CliController.parseDateRange("2013-03-07 to 2013-03-08", dao);
		assertEquals(LocalDateTime.of(2013, 3, 7, 0, 0, 0), range[0]);
		assertEquals(LocalDateTime.of(2013, 3, 9, 0, 0, 0), range[1]);

		range = CliController.parseDateRange("2013-03-07 to 2013-03-08 13:21", dao);
		assertEquals(LocalDateTime.of(2013, 3, 7, 0, 0, 0), range[0]);
		assertEquals(LocalDateTime.of(2013, 3, 8, 13, 21, 0), range[1]);
	}

	private static void assertNow(LocalDateTime actual) {
		LocalDateTime now = LocalDateTime.now();
		assertTrue(Duration.between(now, actual).getSeconds() < 1);
	}
}
