package emcshop;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

/**
 * DAO for accessing the display names, transaction page names, and image file
 * names of all Minecraft items.
 * @author Michael Angstadt
 */
public class ItemIndex {
	private static ItemIndex INSTANCE;

	private final Map<String, String> emcNameToDisplayName;
	private final Map<String, String> minecraftIdToDisplayName;
	private final Map<String, String> itemImages;
	private final Map<String, Integer> stackSizes;
	private final Multimap<String, String> itemNameToGroups;
	private final Set<String> itemGroupNames;
	private final List<String> itemNames;

	/**
	 * Gets the singleton instance of this class.
	 * @return the singleton object
	 */
	public static synchronized ItemIndex instance() {
		if (INSTANCE == null) {
			InputStream in = ItemIndex.class.getResourceAsStream("items.xml");
			try {
				INSTANCE = new ItemIndex(in);
			} catch (Throwable t) {
				//nothing should be thrown because this file is on the classpath
				throw new RuntimeException(t);
			} finally {
				IOUtils.closeQuietly(in);
			}
		}

		return INSTANCE;
	}

	/**
	 * Creates a new renamed items object.
	 * @param in the input stream to the XML document
	 * @throws SAXException if there was a problem parse the XML
	 * @throws IOException if there was a problem reading from the stream
	 */
	ItemIndex(InputStream in) throws SAXException, IOException {
		//parse XML document
		Document xml;
		try {
			DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
			fact.setIgnoringComments(true);
			fact.setIgnoringElementContentWhitespace(true);

			xml = fact.newDocumentBuilder().parse(in);
		} catch (ParserConfigurationException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		NodeList itemNodes;
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			itemNodes = (NodeList) xpath.evaluate("/Items/Item", xml, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		ImmutableMap.Builder<String, String> minecraftIdToDisplayName = ImmutableMap.builder();
		ImmutableMap.Builder<String, String> emcNameToDisplayName = ImmutableMap.builder();
		ImmutableMap.Builder<String, String> itemImages = ImmutableMap.builder();
		ImmutableMultimap.Builder<String, String> itemNameToGroups = ImmutableMultimap.builder();
		ImmutableMap.Builder<String, Integer> stackSizes = ImmutableMap.builder();
		ImmutableList.Builder<String> itemNames = ImmutableList.builder();
		ImmutableSet.Builder<String> itemGroupNames = ImmutableSet.builder();
		for (int i = 0; i < itemNodes.getLength(); i++) {
			Element itemNode = (Element) itemNodes.item(i);
			String name = itemNode.getAttribute("name");

			itemNames.add(name);

			String emcNames = itemNode.getAttribute("emcNames");
			if (!emcNames.isEmpty()) {
				for (String emcName : emcNames.split(",")) {
					emcNameToDisplayName.put(emcName, name);
				}
			}

			String ids = itemNode.getAttribute("id");
			if (!ids.isEmpty()) {
				for (String id : ids.split(",")) {
					minecraftIdToDisplayName.put(id, name);
				}
			}

			String image = itemNode.getAttribute("image");
			if (!image.isEmpty()) {
				itemImages.put(name, image);
			}

			String stackSize = itemNode.getAttribute("stack");
			if (!stackSize.isEmpty()) {
				stackSizes.put(name.toLowerCase(), Integer.valueOf(stackSize));
			}

			String groups = itemNode.getAttribute("group");
			if (!groups.isEmpty()) {
				List<String> groupsList = Arrays.asList(groups.split(","));
				itemGroupNames.addAll(groupsList);
				itemNameToGroups.putAll(name.toLowerCase(), groupsList);
			}
		}

		this.emcNameToDisplayName = emcNameToDisplayName.build();
		this.minecraftIdToDisplayName = minecraftIdToDisplayName.build();
		this.itemImages = itemImages.build();
		this.stackSizes = stackSizes.build();
		this.itemNames = itemNames.build();
		this.itemGroupNames = itemGroupNames.build();
		this.itemNameToGroups = itemNameToGroups.build();
	}

	/**
	 * Gets the display name of an item.
	 * @param emcName the name from the transaction history (e.g. "Potion:8193")
	 * @return the display name (e.g. "Potion of Regeneration") or the
	 * transaction history name if no mapping exists
	 */
	public String getDisplayName(String emcName) {
		String displayName = emcNameToDisplayName.get(emcName);
		return (displayName == null) ? emcName : displayName;
	}

	/**
	 * Gets the display name of an item, given its Minecraft item ID.
	 * @param id the Minecraft item ID
	 * @return the display name or null if the ID was not recognized
	 */
	public String getDisplayNameFromMinecraftId(String id) {
		String displayName = minecraftIdToDisplayName.get(id);
		if (displayName != null) {
			return displayName;
		}

		if (!id.contains(":")) {
			displayName = minecraftIdToDisplayName.get(id + ":0");
			if (displayName != null) {
				return displayName;
			}
		}

		if (id.endsWith(":0")) {
			displayName = minecraftIdToDisplayName.get(id.substring(0, id.length() - 2));
			if (displayName != null) {
				return displayName;
			}
		}

		return null;
	}

	/**
	 * Gets the image file name of an item
	 * @param displayName the item's display name
	 * @return the item's image file name
	 */
	public String getImageFileName(String displayName) {
		String image = itemImages.get(displayName);
		if (image == null) {
			image = displayName.toLowerCase().replace(" ", "_") + ".png";
		}
		return image;
	}

	/**
	 * Gets the stack size of an item.
	 * @param displayName the item name
	 * @return the stack size (e.g. "64")
	 */
	public Integer getStackSize(String displayName) {
		Integer stackSize = stackSizes.get(displayName.toLowerCase());
		return (stackSize == null) ? 64 : stackSize;
	}

	/**
	 * Gets the EMC-to-display name mappings (only includes the mappings that
	 * differ from the default).
	 * @return the mappings
	 */
	public Map<String, String> getEmcNameToDisplayNameMapping() {
		return emcNameToDisplayName;
	}

	/**
	 * Gets the display-to-EMC name mappings (only includes the mappings that
	 * differ from the default, reverse of
	 * {@link #getEmcNameToDisplayNameMapping()}).
	 * @return the mappings
	 */
	public Multimap<String, String> getDisplayNameToEmcNamesMapping() {
		Multimap<String, String> mappings = ArrayListMultimap.create();
		for (Map.Entry<String, String> entry : emcNameToDisplayName.entrySet()) {
			String emcName = entry.getKey();
			String displayName = entry.getValue();

			mappings.put(displayName, emcName);
		}
		return mappings;
	}

	/**
	 * Gets the item image file names (only includes the images that differ from
	 * the default).
	 * @return the image file names
	 */
	public Map<String, String> getItemImages() {
		return itemImages;
	}

	/**
	 * Gets all the item names.
	 * @return the item names
	 */
	public List<String> getItemNames() {
		return itemNames;
	}

	/**
	 * Gets all the item group names.
	 * @return the item group names
	 */
	public Set<String> getItemGroupNames() {
		return itemGroupNames;
	}

	/**
	 * Gets the groups an item belongs to.
	 * @param itemName the item name (e.g. "Oak Log")
	 * @return the groups (e.g. "Wood")
	 */
	public Collection<String> getGroups(String itemName) {
		return itemNameToGroups.get(itemName.toLowerCase());
	}
}
