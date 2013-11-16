package emcshop.gui.images;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class ImageManagerTest {
	@Test
	public void filterPotionName() {
		assertFilter("Potion of Water Breathing", "potion Water Breathing");
		assertFilter("Splash Potion of Water Breathing Extended II", "potion Water Breathing splash");
		assertFilter("Diamond", "Diamond");
	}

	private void assertFilter(String input, String expected) {
		String actual = ImageManager.filterPotionName(input);
		assertEquals(expected, actual);
	}
}
