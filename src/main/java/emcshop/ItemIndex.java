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

import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import emcshop.gui.images.Images;
import emcshop.util.Leaf;

/**
 * DAO for accessing the display names, transaction page names, and image file
 * names of all Minecraft items.
 * @author Michael Angstadt
 */
public class ItemIndex {
	private static ItemIndex INSTANCE;
	private static final int DEFAULT_STACK_SIZE = 64;

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
			} catch (IOException e) {
				//the program should terminate if this file can't be read!
				throw new RuntimeException(e);
			} catch (SAXException e) {
				//the program should terminate if this file can't be parsed!
				throw new RuntimeException(e);
			} finally {
				IOUtils.closeQuietly(in);
			}
		}

		return INSTANCE;
	}

	/**
	 * Creates a new item index.
	 * @param in the input stream to the XML document
	 * @throws SAXException if there was a problem parsing the XML
	 * @throws IOException if there was a problem reading from the stream
	 */
	ItemIndex(InputStream in) throws SAXException, IOException {
		//parse XML document
		Leaf document;
		try {
			DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
			fact.setIgnoringComments(true);
			fact.setIgnoringElementContentWhitespace(true);

			document = new Leaf(fact.newDocumentBuilder().parse(in));
		} catch (ParserConfigurationException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		//parse categories
		Map<Integer, CategoryInfo> categoriesById = new HashMap<Integer, CategoryInfo>();
		{
			List<Leaf> categoryElements = document.select("/Items/Categories/Category");

			ImmutableSet.Builder<CategoryInfo> categories = ImmutableSet.builder();
			for (Leaf categoryElement : categoryElements) {
				CategoryInfo category = parseCategory(categoryElement);
				categories.add(category);
				categoriesById.put(category.id, category);
			}
			this.categories = categories.build();
		}

		//parse items
		{
			List<Leaf> itemElements = document.select("/Items/Item");

			ImmutableMap.Builder<String, ItemInfo> byName = ImmutableMap.builder();
			ImmutableMap.Builder<String, ItemInfo> byEmcName = ImmutableMap.builder();
			ImmutableMap.Builder<String, ItemInfo> byId = ImmutableMap.builder();
			ImmutableSet.Builder<String> groups = ImmutableSet.builder();
			for (Leaf itemElement : itemElements) {
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

	private static CategoryInfo parseCategory(Leaf element) {
		String name = element.attribute("name");
		int id = Integer.parseInt(element.attribute("id"));

		String iconStr = element.attribute("icon");
		ImageIcon icon = iconStr.isEmpty() ? null : Images.get("items/" + iconStr);

		return new CategoryInfo(id, name, icon);
	}

	private static ItemInfo parseItem(Leaf element, Map<Integer, CategoryInfo> categoriesById) {
		String name = element.attribute("name");

		String value = element.attribute("emcNames");
		String emcNames[] = splitValues(value);

		value = element.attribute("id");
		String ids[] = splitValues(value);

		value = element.attribute("image");
		String image = value.isEmpty() ? imageFileName(name) : value;

		value = element.attribute("stack");
		int stackSize = value.isEmpty() ? DEFAULT_STACK_SIZE : Integer.valueOf(value);

		value = element.attribute("group");
		String groups[] = splitValues(value);

		value = element.attribute("categories");
		String categoriesStr[] = splitValues(value);
		CategoryInfo[] categories = new CategoryInfo[categoriesStr.length];
		for (int i = 0; i < categoriesStr.length; i++) {
			Integer id = Integer.valueOf(categoriesStr[i]);
			categories[i] = categoriesById.get(id);
		}

		return new ItemInfo(name, emcNames, ids, image, stackSize, groups, categories);
	}

	private static String[] splitValues(String value) {
		return value.isEmpty() ? new String[0] : value.split("\\s*,\\s*");
	}

	private static String imageFileName(String itemName) {
		return itemName.toLowerCase().replace(' ', '_') + ".png";
	}

	/**
	 * Determines if an item name from the transaction history is associated
	 * with a known item.
	 * @param emcName the name from the transaction history
	 * @return true if it's in the list, false if not
	 */
	public boolean isEmcNameRecognized(String emcName) {
		emcName = emcName.toLowerCase();
		return byName.containsKey(emcName) || byEmcName.containsKey(emcName);
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

		//try adding ":0" to the end
		if (!id.contains(":")) {
			item = byId.get(id + ":0");
			if (item != null) {
				return item.name;
			}
		}

		//if the name ends with ":0", try removing that suffix
		if (id.endsWith(":0")) {
			item = byId.get(id.substring(0, id.length() - 2));
			if (item != null) {
				return item.name;
			}
		}

		return null;
	}

	/**
	 * Determines if the given item is not defined in the items list.
	 * @param displayName the item name
	 * @return true if it's not in the item list, false if it is
	 */
	public boolean isUnknownItem(String displayName) {
		return !byName.containsKey(displayName.toLowerCase());
	}

	/**
	 * Gets the image file name of an item.
	 * @param displayName the item's display name
	 * @return the item's image file name
	 */
	public String getImageFileName(String displayName) {
		ItemInfo item = byName.get(displayName.toLowerCase());
		if (item != null) {
			return item.image;
		}

		/*
		 * Display an icon for enchanted items (e.g. "Bow-b0a8").
		 */
		int dashPos = displayName.indexOf('-');
		if (dashPos > 0) {
			String beforeDash = displayName.substring(0, dashPos).trim();
			return getImageFileName(beforeDash);
		}

		return imageFileName(displayName);
	}

	/**
	 * Gets the stack size of an item.
	 * @param displayName the item name
	 * @return the stack size (e.g. "64")
	 */
	public int getStackSize(String displayName) {
		ItemInfo item = byName.get(displayName.toLowerCase());
		return (item == null) ? DEFAULT_STACK_SIZE : item.stackSize;
	}

	/**
	 * <p>
	 * Gets the names that the rupee transaction history page on the EMC website
	 * uses for each item (for example, Black Terracotta was called "Black
	 * Stclay" and "Black Hardened Clay" at various times in the past).
	 * </p>
	 * <p>
	 * Items whose names do not differ are not returned by this method (for
	 * example, both EMC Shopkeeper and the rupee transaction history page use
	 * the name "Purpur Block" to refer to that item, so this method will not
	 * include this item in its return value).
	 * </p>
	 * @return the item name mappings (key = EMC Shopkeeper display name; value
	 * = rupee transaction history name(s))
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
	 * Gets all the item display names.
	 * @return the item display names (sorted alphabetically)
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

	/**
	 * Gets the categories that are assigned to an item.
	 * @param itemName the item display name (e.g. "Oak Log")
	 * @return the item categories
	 */
	public CategoryInfo[] getItemCategories(String itemName) {
		ItemInfo item = byName.get(itemName.toLowerCase());
		return (item == null) ? new CategoryInfo[0] : item.categories;
	}

	/**
	 * Gets the groups an item belongs to.
	 * @param itemName the item name (e.g. "Oak Log")
	 * @return the groups (e.g. "Wood")
	 */
	public Collection<String> getGroups(String itemName) {
		ItemInfo item = byName.get(itemName.toLowerCase());
		return (item == null) ? Collections.<String> emptyList() : Arrays.asList(item.groups);
	}

	/**
	 * Holds the information on an item.
	 */
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

	/**
	 * Holds the information on a category.
	 */
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
