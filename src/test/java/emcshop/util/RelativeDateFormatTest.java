package emcshop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class RelativeDateFormatTest {
	@Test
	public void format() {
		RelativeDateFormat df = new RelativeDateFormat();

		LocalDateTime date = LocalDateTime.now();
		assertEquals("A moment ago", df.format(date));

		date = date.minusMinutes(30);
		assertTrue(df.format(date).matches("\\d+ minutes ago"));

		date = date.minusHours(1);
		assertTrue(df.format(date).matches("Today at .*"));

		date = date.minusDays(1);
		assertTrue(df.format(date).matches("Yesterday at .*"));
	}
}
