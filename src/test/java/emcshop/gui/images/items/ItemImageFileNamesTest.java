package emcshop.gui.images.items;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class ItemImageFileNamesTest {
	@Test
	public void getFileName() throws Throwable {
		//@formatter:off
		String sample =
		"<Images>" +
			"<Image name=\"dirt.png\">" +
				"<Item name=\"My Dirt\" />" +
				"<Item name=\"Your Dirt\" />" +
			"</Image>" +
		"</Images>";
		//@formatter:on

		ItemImageFileNames names = new ItemImageFileNames(new ByteArrayInputStream(sample.getBytes()));
		assertEquals("dirt.png", names.getFileName("My Dirt"));
		assertEquals("dirt.png", names.getFileName("Your Dirt"));
		assertEquals("her_dirt.png", names.getFileName("Her Dirt"));
	}
}
