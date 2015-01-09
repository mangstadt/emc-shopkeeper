package emcshop;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import emcshop.gui.images.ImageManager;

/**
 * DAO for accessing the display names, transaction page names, and image file
 * names of all Minecraft items.
 * @author Michael Angstadt
 */
public class ItemIndex {
	private static ItemIndex INSTANCE;

	private final Map<String, ItemInfo> byName;
	private final Map<String, ItemInfo> byEmcName;
	private final Map<String, ItemInfo> byId;

	private final Set<String> groups;
	private final Set<CategoryInfo> categories;

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

		XPath xpath = XPathFactory.newInstance().newXPath();

		//parse categories
		Map<Integer, CategoryInfo> categoriesById = new HashMap<Integer, CategoryInfo>();
		{
			NodeList categoryNodes;
			try {
				categoryNodes = (NodeList) xpath.evaluate("/Items/Categories/Category", xml, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				//should never be thrown
				throw new RuntimeException(e);
			}

			ImmutableSet.Builder<CategoryInfo> categories = ImmutableSet.builder();
			for (int i = 0; i < categoryNodes.getLength(); i++) {
				Element categoryElement = (Element) categoryNodes.item(i);

				CategoryInfo category = parseCategory(categoryElement);
				categories.add(category);
				categoriesById.put(category.id, category);
			}
			this.categories = categories.build();
		}

		//parse items
		{
			NodeList itemNodes;
			try {
				itemNodes = (NodeList) xpath.evaluate("/Items/Item", xml, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				//should never be thrown
				throw new RuntimeException(e);
			}

			ImmutableMap.Builder<String, ItemInfo> byName = ImmutableMap.builder();
			ImmutableMap.Builder<String, ItemInfo> byEmcName = ImmutableMap.builder();
			ImmutableMap.Builder<String, ItemInfo> byId = ImmutableMap.builder();
			ImmutableSet.Builder<String> groups = ImmutableSet.builder();
			for (int i = 0; i < itemNodes.getLength(); i++) {
				Element itemElement = (Element) itemNodes.item(i);
				ItemInfo info = parseItem(itemElement, categoriesById);

				byName.put(info.name.toLowerCase(), info);

				for (String emcName : info.emcNames) {
					byEmcName.put(emcName.toLowerCase(), info);
				}

				for (String id : info.ids) {
					byId.put(id, info);
				}

				groups.add(info.groups);
			}

			this.byName = byName.build();
			this.byEmcName = byEmcName.build();
			this.byId = byId.build();
			this.groups = groups.build();
		}
	}

	private CategoryInfo parseCategory(Element element) {
		String name = element.getAttribute("name");
		int id = Integer.parseInt(element.getAttribute("id"));

		String iconStr = element.getAttribute("icon");
		ImageIcon icon = iconStr.isEmpty() ? null : ImageManager.getImageIcon("items/" + iconStr);

		return new CategoryInfo(id, name, icon);
	}

	private ItemInfo parseItem(Element element, Map<Integer, CategoryInfo> categoriesById) {
		String name = element.getAttribute("name");

		String value = element.getAttribute("emcNames");
		String emcNames[] = value.isEmpty() ? new String[0] : value.split("\\s*,\\s*");

		value = element.getAttribute("id");
		String ids[] = value.isEmpty() ? new String[0] : value.split("\\s*,\\s*");

		value = element.getAttribute("image");
		String image = value.isEmpty() ? name.toLowerCase().replace(' ', '_') + ".png" : value;

		value = element.getAttribute("stack");
		int stackSize = value.isEmpty() ? 64 : Integer.valueOf(value);

		value = element.getAttribute("group");
		String groups[] = value.isEmpty() ? new String[0] : value.split("\\s*,\\s*");

		value = element.getAttribute("categories");
		String categoriesStr[] = value.isEmpty() ? new String[0] : value.split("\\s*,\\s*");
		CategoryInfo[] categories = new CategoryInfo[categoriesStr.length];
		for (int i = 0; i < categoriesStr.length; i++) {
			int id = Integer.parseInt(categoriesStr[i]);
			categories[i] = categoriesById.get(id);

		}

		return new ItemInfo(name, emcNames, ids, image, stackSize, groups, categories);
	}

	/**
	 * Gets the display name of an item.
	 * @param emcName the name from the transaction history (e.g. "Potion:8193")
	 * @return the display name (e.g. "Potion of Regeneration") or the
	 * transaction history name if no mapping exists
	 */
	public String getDisplayName(String emcName) {
		ItemInfo item = byEmcName.get(emcName.toLowerCase());
		return (item == null) ? emcName : item.name;
	}

	/**
	 * Gets the display name of an item, given its Minecraft item ID.
	 * @param id the Minecraft item ID
	 * @return the display name or null if the ID was not recognized
	 */
	public String getDisplayNameFromMinecraftId(String id) {
		ItemInfo item = byId.get(id);
		if (item != null) {
			return item.name;
		}

		if (!id.contains(":")) {
			item = byId.get(id + ":0");
			if (item != null) {
				return item.name;
			}
		}

		if (id.endsWith(":0")) {
			item = byId.get(id.substring(0, id.length() - 2));
			if (item != null) {
				return item.name;
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
		ItemInfo item = byName.get(displayName.toLowerCase());
		if (item == null) {
			return displayName.toLowerCase().replace(" ", "_") + ".png";
		}
		return item.image;
	}

	/**
	 * Gets the stack size of an item.
	 * @param displayName the item name
	 * @return the stack size (e.g. "64")
	 */
	public int getStackSize(String displayName) {
		ItemInfo item = byName.get(displayName.toLowerCase());
		return (item == null) ? 64 : item.stackSize;
	}

	/**
	 * Gets the display-to-EMC name mappings (only includes the mappings that
	 * differ from the default, reverse of
	 * {@link #getEmcNameToDisplayNameMapping()}).
	 * @return the mappings
	 */
	public Multimap<String, String> getDisplayNameToEmcNamesMapping() {
		Multimap<String, String> mappings = ArrayListMultimap.create();
		for (ItemInfo item : byName.values()) {
			for (String emcName : item.emcNames) {
				mappings.put(item.name, emcName);
			}
		}
		return mappings;
	}

	/**
	 * Gets all the item names.
	 * @return the item names
	 */
	public List<String> getItemNames() {
		List<String> names = new ArrayList<String>(byName.size());
		for (ItemInfo item : byName.values()) {
			names.add(item.name);
		}

		Collections.sort(names);
		return names;
	}

	/**
	 * Gets all the item group names.
	 * @return the item group names
	 */
	public Set<String> getItemGroupNames() {
		return groups;
	}

	/**
	 * Gets all item categories.
	 * @return the item categories
	 */
	public Set<CategoryInfo> getCategories() {
		return categories;
	}

	public CategoryInfo[] getItemCategories(String itemName) {
		ItemInfo item = byName.get(itemName.toLowerCase());
		if (item == null) {
			return new CategoryInfo[0];
		}

		return item.categories;
	}

	/**
	 * Gets the groups an item belongs to.
	 * @param itemName the item name (e.g. "Oak Log")
	 * @return the groups (e.g. "Wood")
	 */
	public Collection<String> getGroups(String itemName) {
		ItemInfo item = byName.get(itemName.toLowerCase());
		return Arrays.asList(item.groups);
	}

	private static class ItemInfo {
		private final String name;
		private final String emcNames[];
		private final String ids[];
		private final String image;
		private final int stackSize;
		private final String[] groups;
		private final CategoryInfo[] categories;

		public ItemInfo(String name, String[] emcNames, String[] ids, String image, int stackSize, String[] groups, CategoryInfo[] categories) {
			this.name = name;
			this.emcNames = emcNames;
			this.ids = ids;
			this.image = image;
			this.stackSize = stackSize;
			this.groups = groups;
			this.categories = categories;
		}
	}

	public static class CategoryInfo {
		private final int id;
		private final String name;
		private final ImageIcon icon;

		public CategoryInfo(int id, String name, ImageIcon icon) {
			this.id = id;
			this.name = name;
			this.icon = icon;
		}

		public int getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public ImageIcon getIcon() {
			return icon;
		}
	}
}
