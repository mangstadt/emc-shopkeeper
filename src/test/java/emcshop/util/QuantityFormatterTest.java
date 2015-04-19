package emcshop.util;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class QuantityFormatterTest {
	@Rule
	public final DefaultLocaleRule rule = new DefaultLocaleRule(Locale.US);

	@Test
	public void format() {
		QuantityFormatter sf = new QuantityFormatter();
		assertEquals("1", sf.format(1, 64));
		assertEquals("-1", sf.format(-1, 64));
		assertEquals("1/0", sf.format(64, 64));
		assertEquals("-1/0", sf.format(-64, 64));
		assertEquals("1/6", sf.format(70, 64));
		assertEquals("7,000", sf.format(7000, 1));
	}

	@Test
	public void plus() {
		QuantityFormatter sf = new QuantityFormatter();
		assertEquals("1/0", sf.format(64, 64));
		sf.setPlus(true);
		assertEquals("+1/0", sf.format(64, 64));
		sf.setPlus(false);
		assertEquals("1/0", sf.format(64, 64));
	}

	@Test
	public void color() {
		QuantityFormatter sf = new QuantityFormatter();
		assertEquals("1/0", sf.format(64, 64));
		assertEquals("0", sf.format(0, 64));
		assertEquals("-1/0", sf.format(-64, 64));

		sf.setColor(true);
		assertEquals("<font color=green>1/0</font>", sf.format(64, 64));
		assertEquals("0", sf.format(0, 64));
		assertEquals("<font color=red>-1/0</font>", sf.format(-64, 64));

		sf.setColor(false);
		assertEquals("1/0", sf.format(64, 64));
		assertEquals("0", sf.format(0, 64));
		assertEquals("-1/0", sf.format(-64, 64));
	}
}
