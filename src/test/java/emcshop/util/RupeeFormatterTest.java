package emcshop.util;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class RupeeFormatterTest {
	@Rule
	public final DefaultLocaleRule rule = new DefaultLocaleRule(Locale.US);

	@Test
	public void no_decimals() {
		RupeeFormatter rf = new RupeeFormatter();
		assertEquals("1r", rf.format(1));
		assertEquals("1,234r", rf.format(1234));
		assertEquals("1,234,567r", rf.format(1234567));
	}

	@Test
	public void decimals() {
		RupeeFormatter rf = new RupeeFormatter(2);

		assertEquals("1r", rf.format(1));
		assertEquals("1.5r", rf.format(1.5));
		assertEquals("1.55r", rf.format(1.55));
		assertEquals("1.56r", rf.format(1.555));

		assertEquals("1,234r", rf.format(1234));
		assertEquals("1,234.5r", rf.format(1234.5));
		assertEquals("1,234.55r", rf.format(1234.55));
		assertEquals("1,234.56r", rf.format(1234.555));

		assertEquals("1,234,567r", rf.format(1234567));
		assertEquals("1,234,567.5r", rf.format(1234567.5));
		assertEquals("1,234,567.55r", rf.format(1234567.55));
		assertEquals("1,234,567.56r", rf.format(1234567.555));
	}

	@Test
	public void plus() {
		RupeeFormatter rf = new RupeeFormatter();
		assertEquals("1r", rf.format(1));
		assertEquals("-1r", rf.format(-1));

		rf.setPlus(true);
		assertEquals("+1r", rf.format(1));
		assertEquals("-1r", rf.format(-1));

		rf.setPlus(false);
		assertEquals("1r", rf.format(1));
		assertEquals("-1r", rf.format(-1));
	}

	@Test
	public void color() {
		RupeeFormatter rf = new RupeeFormatter();
		assertEquals("1r", rf.format(1));
		assertEquals("0r", rf.format(0));
		assertEquals("-1r", rf.format(-1));

		rf.setColor(true);
		assertEquals("<font color=green>1r</font>", rf.format(1));
		assertEquals("0r", rf.format(0));
		assertEquals("<font color=red>-1r</font>", rf.format(-1));

		rf.setColor(false);
		assertEquals("1r", rf.format(1));
		assertEquals("0r", rf.format(0));
		assertEquals("-1r", rf.format(-1));
	}
}
