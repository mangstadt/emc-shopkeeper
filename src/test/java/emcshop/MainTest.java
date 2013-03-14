package emcshop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class MainTest {
	@Test(expected = ParseException.class)
	public void parseDateRange_bad_date() throws Exception {
		Main.parseDateRange("not-a-date");
	}

	@Test
	public void parseDateRange() throws Exception {
		Date now = new Date();
		DateFormat dfDate = new SimpleDateFormat("yyyy-MM-dd");
		DateFormat dfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		Date range[] = Main.parseDateRange("today");
		assertEquals(dfDate.format(now) + " 00:00:00", dfDateTime.format(range[0]));
		assertTrue(range[1].getTime() - range[0].getTime() < 1000 * 60 * 60 * 24);

		range = Main.parseDateRange("2013-03-07");
		assertEquals("2013-03-07 00:00:00", dfDateTime.format(range[0]));
		assertTrue(range[1].getTime() - now.getTime() < 1000);

		range = Main.parseDateRange("2013-03-07 13:21");
		assertEquals("2013-03-07 13:21:00", dfDateTime.format(range[0]));
		assertTrue(range[1].getTime() - now.getTime() < 1000);

		range = Main.parseDateRange("2013-03-07 13:21:11");
		assertEquals("2013-03-07 13:21:11", dfDateTime.format(range[0]));
		assertTrue(range[1].getTime() - now.getTime() < 1000);

		range = Main.parseDateRange("2013-03-07 to today");
		assertEquals("2013-03-07 00:00:00", dfDateTime.format(range[0]));
		assertTrue(range[1].getTime() - now.getTime() < 1000);

		range = Main.parseDateRange("2013-03-07 to 2013-03-08");
		assertEquals("2013-03-07 00:00:00", dfDateTime.format(range[0]));
		assertEquals("2013-03-09 00:00:00", dfDateTime.format(range[1]));

		range = Main.parseDateRange("2013-03-07 to 2013-03-08 13:21");
		assertEquals("2013-03-07 00:00:00", dfDateTime.format(range[0]));
		assertEquals("2013-03-08 13:21:00", dfDateTime.format(range[1]));
	}
}
