package emcshop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.w3c.dom.Document;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import emcshop.util.Leaf;

/**
 * @author Michael Angstadt
 */
public class ItemIndexTest {
	private final ItemIndex mockIndex;
	{
		//@formatter:off
		String sample =
		"<Items>" +
			"<Item name=\"Diamond\" id=\"264\" />" +
			"<Item name=\"Orange Clay\" id=\"159:1\" emcNames=\"Orange Stclay\" />" +
			"<Item name=\"Potion of Fire Resistance\" id=\"373:8195,373:8227\" emcNames=\"Potion:8195,Potion:8227\" />" +
			"<Item name=\"Zombie Potion\" image=\"water_bottle.png\" />" +
			"<Item name=\"Oak Log\" id=\"17:0\" />" +
			"<Item name=\"Ender Pearl\" id=\"368\" stack=\"16\" />" +
			"<Item name=\"Diamond Chestplate\" emcNames=\"Diamond Chest\" />" +
		"</Items>";
		//@formatter:on

		try {
			mockIndex = new ItemIndex(new ByteArrayInputStream(sample.getBytes()));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private final Leaf liveIndex;
	{
		Document document;
		InputStream in = getClass().getResourceAsStream("items.xml");
		try {
			document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.closeQuietly(in);
		}

		liveIndex = new Leaf(document);
	}

	@Test
	public void isEmcNameRecognized() {
		assertTrue(mockIndex.isEmcNameRecognized("Potion of Fire Resistance"));
		assertTrue(mockIndex.isEmcNameRecognized("Potion:8195"));
		assertFalse(mockIndex.isEmcNameRecognized("Potion:9999"));
	}

	@Test
	public void getDisplayName() {
		assertEquals("Diamond", mockIndex.getDisplayName("Diamond"));
		assertEquals("Orange Clay", mockIndex.getDisplayName("Orange Clay"));
		assertEquals("Orange Clay", mockIndex.getDisplayName("Orange Stclay"));
		assertEquals("Potion of Fire Resistance", mockIndex.getDisplayName("Potion:8195"));
		assertEquals("Potion of Fire Resistance", mockIndex.getDisplayName("Potion:8227"));
		assertEquals("Zombie Potion", mockIndex.getDisplayName("Zombie Potion"));
		assertEquals("Iron Ingot", mockIndex.getDisplayName("Iron Ingot"));
	}

	@Test
	public void getDisplayNameFromMinecraftId() {
		assertEquals("Diamond", mockIndex.getDisplayNameFromMinecraftId("264"));
		assertEquals("Diamond", mockIndex.getDisplayNameFromMinecraftId("264:0"));
		assertEquals("Oak Log", mockIndex.getDisplayNameFromMinecraftId("17"));
		assertEquals("Oak Log", mockIndex.getDisplayNameFromMinecraftId("17:0"));
		assertEquals("Orange Clay", mockIndex.getDisplayNameFromMinecraftId("159:1"));
		assertEquals("Potion of Fire Resistance", mockIndex.getDisplayNameFromMinecraftId("373:8195"));
		assertEquals("Potion of Fire Resistance", mockIndex.getDisplayNameFromMinecraftId("373:8227"));
		assertEquals(null, mockIndex.getDisplayNameFromMinecraftId("22"));
	}

	@Test
	public void getImageFileName() {
		assertEquals("diamond.png", mockIndex.getImageFileName("Diamond"));
		assertEquals("orange_clay.png", mockIndex.getImageFileName("Orange Clay"));
		assertEquals("water_bottle.png", mockIndex.getImageFileName("Zombie Potion"));
		assertEquals("bow.png", mockIndex.getImageFileName("Bow-eo0f"));
		assertEquals("diamond_chestplate.png", mockIndex.getImageFileName("Diamond Chest-eo0f"));
		assertEquals("diamond_helmet.png", mockIndex.getImageFileName("Diamond Helmet - §aBig Daddy Helmet"));
	}

	@Test
	public void getItemNames() {
		Set<String> actual = new HashSet<>(mockIndex.getItemNames());
		Set<String> expected = new HashSet<>(Arrays.asList("Diamond", "Orange Clay", "Potion of Fire Resistance", "Zombie Potion", "Oak Log", "Ender Pearl", "Diamond Chestplate"));
		assertEquals(expected, actual);
	}

	@Test
	public void getStackSize() {
		assertEquals(16, mockIndex.getStackSize("Ender Pearl"));
		assertEquals(64, mockIndex.getStackSize("Diamond"));
		assertEquals(64, mockIndex.getStackSize("unknown"));
	}

	/**
	 * Checks the live "items.xml" file for duplicate item names.
	 */
	@Test
	public void validate_no_duplicate_names() {
		Set<String> names = new HashSet<>();
		Set<String> duplicates = new HashSet<>();
		List<Leaf> itemElements = liveIndex.select("/Items/Item");
		for (Leaf itemElement : itemElements) {
			String name = itemElement.attribute("name");
			boolean duplicate = !names.add(name.toLowerCase());
			if (duplicate) {
				duplicates.add(name);
			}
		}

		assertTrue("Multiple items use the same name: " + duplicates, duplicates.isEmpty());
	}

	/**
	 * Checks the live "items.xml" file for duplicate emcNames.
	 */
	@Test
	public void validate_no_duplicate_emc_names() {
		Multimap<String, String> emcNameToDisplayNames = ArrayListMultimap.create();
		List<Leaf> itemElements = liveIndex.select("/Items/Item");
		for (Leaf itemElement : itemElements) {
			String emcNames = itemElement.attribute("emcNames");
			if (emcNames.isEmpty()) {
				continue;
			}

			String name = itemElement.attribute("name");
			for (String emcName : emcNames.split("\\s*,\\s*")) {
				emcNameToDisplayNames.put(emcName, name);
			}
		}

		List<Map.Entry<String, Collection<String>>> problemItems = new ArrayList<>();
		for (Map.Entry<String, Collection<String>> entry : emcNameToDisplayNames.asMap().entrySet()) {
			Collection<String> displayNames = entry.getValue();
			if (displayNames.size() > 1) {
				problemItems.add(entry);
			}
		}

		if (!problemItems.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, Collection<String>> item : problemItems) {
				String emcName = item.getKey();
				Collection<String> displayNames = item.getValue();
				sb.append(displayNames).append(" all use the emcName \"").append(emcName).append("\".\n");
			}

			fail(sb.toString());
		}
	}

	/**
	 * Checks the live "items.xml" file for duplicate IDs.
	 */
	@Test
	public void validate_no_duplicate_ids() {
		Multimap<String, String> idToDisplayNames = ArrayListMultimap.create();
		List<Leaf> itemElements = liveIndex.select("/Items/Item");
		for (Leaf itemElement : itemElements) {
			String id = itemElement.attribute("id");
			if (id.isEmpty()) {
				continue;
			}

			String name = itemElement.attribute("name");
			idToDisplayNames.put(id, name);
		}

		List<Map.Entry<String, Collection<String>>> problemItems = new ArrayList<>();
		for (Map.Entry<String, Collection<String>> entry : idToDisplayNames.asMap().entrySet()) {
			Collection<String> displayNames = entry.getValue();
			if (displayNames.size() > 1) {
				problemItems.add(entry);
			}
		}

		if (!problemItems.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, Collection<String>> item : problemItems) {
				String id = item.getKey();
				Collection<String> displayNames = item.getValue();
				sb.append(displayNames).append(" all use the ID \"").append(id).append("\".\n");
			}

			fail(sb.toString());
		}
	}
}
