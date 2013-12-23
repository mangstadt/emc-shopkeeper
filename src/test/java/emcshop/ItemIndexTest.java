package emcshop;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class ItemIndexTest {
	private final ItemIndex index;
	{
		//@formatter:off
		String sample =
		"<Items>" +
			"<Item name=\"Diamond\" id=\"264\" />" +
			"<Item name=\"Orange Clay\" id=\"159:1\" emcNames=\"Orange Stclay\" />" +
			"<Item name=\"Potion of Fire Resistance\" id=\"373:8195,373:8227\" emcNames=\"Potion:8195,Potion:8227\" />" +
			"<Item name=\"Zombie Potion\" image=\"water_bottle.png\" />" +
			"<Item name=\"Oak Log\" id=\"17:0\" />" +
		"</Items>";
		//@formatter:on

		try {
			index = new ItemIndex(new ByteArrayInputStream(sample.getBytes()));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void getDisplayName() {
		assertEquals("Diamond", index.getDisplayName("Diamond"));
		assertEquals("Orange Clay", index.getDisplayName("Orange Clay"));
		assertEquals("Orange Clay", index.getDisplayName("Orange Stclay"));
		assertEquals("Potion of Fire Resistance", index.getDisplayName("Potion:8195"));
		assertEquals("Potion of Fire Resistance", index.getDisplayName("Potion:8227"));
		assertEquals("Zombie Potion", index.getDisplayName("Zombie Potion"));
		assertEquals("Iron Ingot", index.getDisplayName("Iron Ingot"));
	}

	@Test
	public void getDisplayNameFromMinecraftId() {
		assertEquals("Diamond", index.getDisplayNameFromMinecraftId("264"));
		assertEquals("Diamond", index.getDisplayNameFromMinecraftId("264:0"));
		assertEquals("Oak Log", index.getDisplayNameFromMinecraftId("17"));
		assertEquals("Oak Log", index.getDisplayNameFromMinecraftId("17:0"));
		assertEquals("Orange Clay", index.getDisplayNameFromMinecraftId("159:1"));
		assertEquals("Potion of Fire Resistance", index.getDisplayNameFromMinecraftId("373:8195"));
		assertEquals("Potion of Fire Resistance", index.getDisplayNameFromMinecraftId("373:8227"));
		assertEquals(null, index.getDisplayNameFromMinecraftId("22"));
	}

	@Test
	public void getImageFileName() {
		assertEquals("diamond.png", index.getImageFileName("Diamond"));
		assertEquals("orange_clay.png", index.getImageFileName("Orange Clay"));
		assertEquals("water_bottle.png", index.getImageFileName("Zombie Potion"));
	}

	@Test
	public void getItemNames() {
		Set<String> actual = new HashSet<String>(index.getItemNames());
		Set<String> expected = new HashSet<String>(Arrays.asList("Diamond", "Orange Clay", "Potion of Fire Resistance", "Zombie Potion", "Oak Log"));
		assertEquals(expected, actual);
	}
}
