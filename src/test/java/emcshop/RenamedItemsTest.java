package emcshop;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class RenamedItemsTest {
	@Test
	public void getSanitizedName() throws Throwable {
		//@formatter:off
		String sample =
		"<Mappings>" +
			"<Mapping from=\"foo\" to=\"bar\" />" +
		"</Mappings>";
		//@formatter:on

		RenamedItems names = new RenamedItems(new ByteArrayInputStream(sample.getBytes()));
		assertEquals("bar", names.getSanitizedName("foo"));
		assertEquals("Diamond", names.getSanitizedName("Diamond"));
	}
}
