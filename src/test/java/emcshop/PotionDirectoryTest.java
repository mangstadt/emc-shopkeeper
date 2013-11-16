package emcshop;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class PotionDirectoryTest {
	@Test
	public void getName() throws Throwable {
		//@formatter:off
		String sample =
		"<Potions>" +
			"<Foo-Bar>" +
				"<Potion id=\"1\" />" +
				"<Potion id=\"2\" ext=\"true\" />" +
				"<Potion id=\"3\" splash=\"true\"/>" +
				"<Potion id=\"4\" level=\"2\"/>" +
				"<Potion id=\"5\" ext=\"true\" splash=\"true\" />" +
				"<Potion id=\"6\" ext=\"true\" level=\"2\" />" +
				"<Potion id=\"7\" splash=\"true\" level=\"2\" />" +
				"<Potion id=\"8\" ext=\"true\" splash=\"true\" level=\"2\" />" +
				"<Potion id=\"9\" ext=\"false\" splash=\"false\" level=\"1\" />" +
			"</Foo-Bar>" +
		"</Potions>";
		//@formatter:on

		PotionDirectory potions = new PotionDirectory(new ByteArrayInputStream(sample.getBytes()));

		assertName("1", "Potion of Foo Bar", potions);
		assertName("2", "Potion of Foo Bar Extended", potions);
		assertName("3", "Splash Potion of Foo Bar", potions);
		assertName("4", "Potion of Foo Bar II", potions);
		assertName("5", "Splash Potion of Foo Bar Extended", potions);
		assertName("6", "Potion of Foo Bar II Extended", potions);
		assertName("7", "Splash Potion of Foo Bar II", potions);
		assertName("8", "Splash Potion of Foo Bar II Extended", potions);
		assertName("9", "Potion of Foo Bar", potions);
		assertName("999", null, potions);
	}

	private void assertName(String id, String expected, PotionDirectory potions) {
		String actual = potions.getName(id);
		assertEquals(expected, actual);
	}

	@Test
	public void getAllNames() throws Throwable {
		//@formatter:off
		String sample =
		"<Potions>" +
			"<Foo-Bar>" +
				"<Potion id=\"1\" />" +
				"<Potion id=\"2\" ext=\"true\" />" +
			"</Foo-Bar>" +
			"<Bar-Foo>" +
				"<Potion id=\"3\" />" +
			"</Bar-Foo>" +
		"</Potions>";
		//@formatter:on

		PotionDirectory potions = new PotionDirectory(new ByteArrayInputStream(sample.getBytes()));

		Map<String, String> expected = new HashMap<String, String>();
		expected.put("1", "Potion of Foo Bar");
		expected.put("2", "Potion of Foo Bar Extended");
		expected.put("3", "Potion of Bar Foo");

		Map<String, String> actual = potions.getAllNames();

		assertEquals(expected, actual);
	}
}
