package emcshop.util;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class BaseFormatterTest {
	@Rule
	public final DefaultLocaleRule rule = new DefaultLocaleRule(Locale.US);

	@Test
	public void format() {
		BaseFormatter qf = new BaseFormatter();
		assertEquals("1", qf.format(1));
		assertEquals("1,234", qf.format(1234));
		assertEquals("1,234,567", qf.format(1234567));
	}

	@Test
	public void plus() {
		BaseFormatter qf = new BaseFormatter();
		assertEquals("1", qf.format(1));
		assertEquals("-1", qf.format(-1));

		qf.setPlus(true);
		assertEquals("+1", qf.format(1));
		assertEquals("-1", qf.format(-1));

		qf.setPlus(false);
		assertEquals("1", qf.format(1));
		assertEquals("-1", qf.format(-1));
	}

	@Test
	public void color() {
		BaseFormatter qf = new BaseFormatter();
		assertEquals("1", qf.format(1));
		assertEquals("0", qf.format(0));
		assertEquals("-1", qf.format(-1));

		qf.setColor(true);
		assertEquals("<font color=green>1</font>", qf.format(1));
		assertEquals("0", qf.format(0));
		assertEquals("<font color=red>-1</font>", qf.format(-1));

		qf.setColor(false);
		assertEquals("1", qf.format(1));
		assertEquals("0", qf.format(0));
		assertEquals("-1", qf.format(-1));
	}
}
