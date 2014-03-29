package emcshop.util;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.junit.Assert;
import org.junit.Test;

public class RelativeDateFormatTest {
	private final RelativeDateFormat relativeDf = new RelativeDateFormat();

	@Test
	public void format() {
		Calendar c = Calendar.getInstance();
		assertEquals("A moment ago", relativeDf.format(c.getTime()));

		c.add(Calendar.MINUTE, -30);
		Assert.assertTrue(relativeDf.format(c.getTime()).matches("\\d+ minutes ago"));

		c.add(Calendar.HOUR, -1);
		Assert.assertTrue(relativeDf.format(c.getTime()).matches("Today at .*"));

		c.add(Calendar.DATE, -1);
		Assert.assertTrue(relativeDf.format(c.getTime()).matches("Yesterday at .*"));
	}
}
