package emcshop.util;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class RelativeDateFormatTest {
	@Rule
	public final DefaultLocaleRule rule = new DefaultLocaleRule(Locale.US);

	@Test
	public void format() {
		LocalDateTime now = LocalDateTime.of(2020, 4, 8, 12, 0, 0);
		RelativeDateFormat df = new RelativeDateFormat() {
			@Override
			LocalDateTime now() {
				return now;
			}
		};

		LocalDateTime ts = now;
		assertEquals("A moment ago", df.format(ts));

		ts = ts.minusMinutes(30);
		assertEquals(df.format(ts), "30 minutes ago");

		ts = ts.minusHours(1);
		assertEquals(df.format(ts), "Today at 10:30 AM");

		ts = ts.minusDays(1).withHour(23);
		assertEquals(df.format(ts), "Yesterday at 11:30 PM");

		ts = ts.withHour(1);
		assertEquals(df.format(ts), "Yesterday at 1:30 AM");

		ts = ts.minusDays(1);
		assertEquals(df.format(ts), "Apr 6, 2020 1:30 AM");
	}
}
