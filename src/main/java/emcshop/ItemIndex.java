package emcshop;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import emcshop.gui.images.Images;
import emcshop.util.Leaf;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DAO for accessing the display names, transaction page names, and image file
 * names of all Minecraft items.
 * @author Michael Angstadt
 */
public class ItemIndex {
	private static ItemIndex INSTANCE;
	private static final int DEFAULT_STACK_SIZE = 64;

	/*
	 * Items indexed by name. Key is in lowercase.
	 */
	private final Map<String, Item> byName;

	/**
	 * Items indexed by emcName (alias). Key is in lowercase.
	 * This is a multimap because the same alias can be used by multiple items at different points in time.
	 */
	private final Multimap<String, Item> byEmcName;

	/**
	 * Items indexed by Minecraft ID.
	 */
	private final Map<String, Item> byId;

	private final Set<String> groups;
	private final Set<Category> categories;

	/**
	 * Gets the singleton instance of this class.
	 * @return the singleton object
	 */
	public static synchronized ItemIndex instance() {
		if (INSTANCE == null) {
			try (InputStream in = ItemIndex.class.getResourceAsStream("items.xml")) {
				INSTANCE = ItemIndex.fromXml(in);
			} catch (IOException | SAXException e) {
				//the program should terminate if this file can't be read!
				throw new RuntimeException(e);
			}
		}

		return INSTANCE;
	}

	/**
	 * Creates an item index from XML data.
	 * @param in the input stream to the XML data
	 * @return the item index
	 * @throws IllegalArgumentException if there are data validation errors
	 * @throws DateTimeParseException if there's a problem parsing dates in the data
	 * @throws SAXException if there's a problem parsing the XML
	 * @throws IOException if there's a problem reading the data
	 */
	public static ItemIndex fromXml(InputStream in) throws SAXException, IOException {
		return new XmlParser(in).parse();
	}

	/**
	 * @param items the items to include in the item index
	 * @throws IllegalArgumentException if there are data validation errors
	 */
	public ItemIndex(Collection<Item> items) {
		Map<String, Item> byName = new HashMap<>(items.size());
		Multimap<String, Item> byEmcName = ArrayListMultimap.create();
		Map<String, Item> byId = new HashMap<>();
		ImmutableSet.Builder<String> groups = ImmutableSet.builder();
		ImmutableSet.Builder<Category> categories = ImmutableSet.builder();

		for (Item item : items) {
			String itemNameLower = item.name.toLowerCase();
			if (byName.containsKey(itemNameLower)) {
				throw new IllegalArgumentException("Duplicate item name: " + item.name);
			}
			byName.put(itemNameLower, item);

			for (EmcName emcName : item.emcNames) {
				String aliasLower = emcName.alias.toLowerCase();

				//check to see if the emcName overlaps with the emcName of another item
				for (Item processedItem : byEmcName.get(aliasLower)) {
					for (EmcName processedEmcName : processedItem.emcNames) {
						if (processedEmcName.alias.equalsIgnoreCase(emcName.alias)) {
							if (emcNamesOverlap(processedEmcName, emcName)) {
								throw new IllegalArgumentException("emcName \"" + emcName.alias + "\" for items \"" + item.name + "\" and \"" + processedItem.name + "\" have overlapping time boundaries.");
							}
						}
					}
				}

				byEmcName.put(aliasLower, item);
			}

			for (String id : item.ids) {
				if (byId.containsKey(id)) {
					throw new IllegalArgumentException("Duplicate ID: " + id);
				}
				byId.put(id, item);
			}

			groups.addAll(item.groups);
			categories.addAll(item.categories);
		}

		//make sure that the emcNames which are not time-bound do not match any of the item names
		for (Item item : items) {
			for (EmcName emcName : item.emcNames) {
				if (emcName.getTimeFrom() == LocalDateTime.MIN) {
					if (emcName.getTimeTo() == LocalDateTime.MAX) {
						if (byName.containsKey(emcName.alias.toLowerCase())) {
							throw new IllegalArgumentException("An emcName of item \"" + item.name + "\" matches the name of an existing item: \"" + emcName.alias + "\"");
						}
					}
				}
			}
		}

		this.byName = Collections.unmodifiableMap(byName);
		this.byEmcName = ImmutableMultimap.copyOf(byEmcName);
		this.byId = Collections.unmodifiableMap(byId);
		this.groups = groups.build();
		this.categories = categories.build();
	}

	private boolean emcNamesOverlap(EmcName name1, EmcName name2) {
		if (name1.timeFrom.compareTo(name2.timeFrom) <= 0 && name1.timeTo.compareTo(name2.timeFrom) > 0) {
			return true;
		}

		if (name1.timeTo.compareTo(name2.timeTo) >= 0 && name1.timeFrom.compareTo(name2.timeTo) < 0) {
			return true;
		}

		if (name1.timeFrom.compareTo(name2.timeFrom) >= 0 && name1.timeTo.compareTo(name2.timeTo) < 0) {
			return true;
		}

		return false;
	}

	/**
	 * Gets the information on an item.
	 * @param displayName the item's display name (case insensitive, e.g. "Diamond")
	 * @return the item information or null if no item exists with that name
	 */
	public Item getItemByDisplayName(String displayName) {
		return byName.get(displayName.toLowerCase());
	}

	private Collection<Item> getItemsByEmcName(String emcName) {
		return byEmcName.get(emcName.toLowerCase());
	}

	/**
	 * Determines if an item name from the transaction history is associated
	 * with a known item.
	 * @param itemName the name from the transaction history (e.g. "Pine Log")
	 * @return true if it's recognized, false if not
	 */
	public boolean isItemNameRecognized(String itemName) {
		return getItemByDisplayName(itemName) != null || !getItemsByEmcName(itemName).isEmpty();
	}

	/**
	 * Gets the display name of an item (also used when saving item names to the database).
	 * @param emcName the name from the transaction history (e.g. "Potion:8193")
	 * @param transactionTs the timestamp of the transaction
	 * @return the display name (e.g. "Potion of Regeneration") or the transaction history name if no mapping exists
	 */
	public String getDisplayName(String emcName, LocalDateTime transactionTs) {
		Collection<Item> possibleItemsWithThatAlias = getItemsByEmcName(emcName);
		for (Item item : possibleItemsWithThatAlias) {
			for (EmcName alias : item.emcNames) {
				if (transactionTs.isBefore(alias.timeTo) && transactionTs.compareTo(alias.timeFrom) >= 0) {
					return item.name;
				}
			}
		}

		return emcName;
	}

	/**
	 * Gets the display name of an item, given its Minecraft item ID.
	 * @param id the Minecraft item ID
	 * @return the display name or null if the ID was not recognized
	 */
	public String getDisplayNameFromMinecraftId(String id) {
		Item item = byId.get(shortenItemId(id));
		return (item == null) ? null : item.name;
	}

	/**
	 * Gets the item's name with HTML formatting.
	 * @param itemName the item name
	 * @param includeColor true to include color formatting, false not to
	 * @return the formatted name or the passed-in itemName string if the item was not recognized
	 */
	public String getItemNameFormatted(String itemName, boolean includeColor) {
		Item item = getItemByDisplayName(itemName);
		return (item == null) ? itemName : ColorFormatter.toHtml(item.nameColored, includeColor);
	}

	/**
	 * Determines if the given item is not defined in the items list.
	 * @param displayName the item name
	 * @return true if it's not in the item list, false if it is
	 */
	public boolean isUnknownItem(String displayName) {
		return getItemByDisplayName(displayName) == null;
	}

	/**
	 * Gets the image of an item.
	 * @param displayName the item's display name
	 * @return the item's image or null if not found
	 */
	public URL getImage(String displayName) {
		Item item = getItemByDisplayName(displayName);
		if (item != null) {
			return item.image;
		}

		/*
		 * Display an icon for enchanted items (e.g. "Bow-b0a8").
		 */
		int dashPos = displayName.indexOf('-');
		if (dashPos > 0) {
			String beforeDash = displayName.substring(0, dashPos).trim();
			String displayNameBeforeDash = getDisplayName(beforeDash, LocalDateTime.now());
			return getImage(displayNameBeforeDash);
		}

		/*
		 * Image is undefined, try to find a matching image.
		 */
		return imageFromFileName(defaultImageFileName(displayName));
	}

	/**
	 * Gets the stack size of an item.
	 * @param displayName the item name
	 * @return the stack size (e.g. "64")
	 */
	public int getStackSize(String displayName) {
		Item item = getItemByDisplayName(displayName);
		return (item == null) ? DEFAULT_STACK_SIZE : item.stackSize;
	}

	/**
	 * <p>
	 * Gets the names that the rupee transaction history page on the EMC website
	 * uses for each item. For example, Black Terracotta was called "Black
	 * Stclay" and "Black Hardened Clay" at various times in the past.
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
	public Multimap<String, EmcName> getDisplayNameToEmcNamesMapping() {
		ImmutableMultimap.Builder<String, EmcName> mappings = new ImmutableMultimap.Builder<>();

		for (Item item : byName.values()) {
			mappings.putAll(item.name, item.emcNames);
		}

		return mappings.build();
	}

	/**
	 * Gets all the item display names.
	 * @return the item display names (sorted alphabetically)
	 */
	public List<String> getItemNames() {
		return byName.values().stream() //@formatter:off
			.map(item -> item.name)
			.sorted()
		.collect(Collectors.toList()); //@formatter:on
	}

	/**
	 * Gets all the item group names.
	 * @return the item group names
	 */
	public Collection<String> getAllGroups() {
		return groups;
	}

	/**
	 * Gets all item categories.
	 * @return the item categories
	 */
	public Collection<Category> getAllCategories() {
		return categories;
	}

	/**
	 * Gets the categories an item belongs to.
	 * @param itemName the item name (e.g. "Oak Log")
	 * @return the item categories
	 */
	public Collection<Category> getCategories(String itemName) {
		Item item = getItemByDisplayName(itemName);
		return (item == null) ? Collections.emptyList() : item.categories;
	}

	/**
	 * Gets the groups an item belongs to.
	 * @param itemName the item name (e.g. "Oak Log")
	 * @return the groups (e.g. "Wood")
	 */
	public Collection<String> getGroups(String itemName) {
		Item item = getItemByDisplayName(itemName);
		return (item == null) ? Collections.emptyList() : item.groups;
	}

	/**
	 * Gets the default image file name for a given item.
	 * @param itemName the item name
	 * @return the default image file name
	 */
	private static String defaultImageFileName(String itemName) {
		return itemName.toLowerCase().replace(' ', '_') + ".png";
	}

	/**
	 * Gets a URL to an existing item image.
	 * @param fileName the file name
	 * @return the URL to the image or null if not found
	 */
	private static URL imageFromFileName(String fileName) {
		return Images.class.getResource("items/" + fileName);
	}

	/**
	 * Removes ":0" from the end of the given item ID.
	 * @param id the item ID (e.g. "264:0")
	 * @return the shortened ID
	 */
	private static String shortenItemId(String id) {
		String suffix = ":0";
		return id.endsWith(suffix) ? id.substring(0, id.length() - suffix.length()) : id;
	}

	/**
	 * Holds the information on a Minecraft item.
	 * @author Michael Angstadt
	 */
	public static class Item {
		private final String name;
		private final String nameColored;
		private final List<EmcName> emcNames;
		private final List<String> ids;
		private final URL image;
		private final int stackSize;
		private final List<String> groups;
		private final List<Category> categories;

		private Item(Builder builder) {
			this.name = builder.name;
			this.nameColored = (builder.nameColored == null) ? builder.name : builder.nameColored;
			this.emcNames = Collections.unmodifiableList(builder.emcNames);
			this.ids = Collections.unmodifiableList(builder.ids);
			this.image = builder.image;
			this.stackSize = (builder.stackSize == 0) ? DEFAULT_STACK_SIZE : builder.stackSize;
			this.groups = Collections.unmodifiableList(builder.groups);
			this.categories = Collections.unmodifiableList(builder.categories);
		}

		public String getName() {
			return name;
		}

		public String getNameColored() {
			return nameColored;
		}

		public List<EmcName> getEmcNames() {
			return emcNames;
		}

		public List<String> getIds() {
			return ids;
		}

		public URL getImage() {
			return image;
		}

		public int getStackSize() {
			return stackSize;
		}

		public List<String> getGroups() {
			return groups;
		}

		public List<Category> getCategories() {
			return categories;
		}

		public static class Builder {
			private String name;
			private String nameColored;
			private final List<EmcName> emcNames = new ArrayList<>();
			private final List<String> ids = new ArrayList<>();
			private URL image;
			private int stackSize;
			private final List<String> groups = new ArrayList<>();
			private final List<Category> categories = new ArrayList<>();

			public Builder setName(String name) {
				Objects.requireNonNull(name);
				this.name = name;
				return this;
			}

			public Builder setNameColored(String nameColored) {
				this.nameColored = nameColored;
				return this;
			}

			public Builder addEmcName(EmcName emcName) {
				Objects.requireNonNull(emcName);
				emcNames.add(emcName);
				return this;
			}

			public Builder setEmcNames(List<EmcName> emcNames) {
				this.emcNames.clear();
				emcNames.forEach(this::addEmcName);
				return this;
			}

			public Builder addId(String id) {
				id = shortenItemId(id);
				ids.add(id);
				return this;
			}

			public Builder setIds(List<String> ids) {
				this.ids.clear();
				ids.forEach(this::addId);
				return this;
			}

			public Builder setImage(URL image) {
				this.image = image;
				return this;
			}

			public Builder setStackSize(int stackSize) {
				if (stackSize <= 0) {
					throw new IllegalArgumentException("Invalid stack size: " + stackSize);
				}
				this.stackSize = stackSize;
				return this;
			}

			public Builder addGroup(String group) {
				Objects.requireNonNull(group);
				groups.add(group);
				return this;
			}

			public Builder setGroups(List<String> groups) {
				this.groups.clear();
				groups.forEach(this::addGroup);
				return this;
			}

			public Builder addCategory(Category category) {
				Objects.requireNonNull(category);
				categories.add(category);
				return this;
			}

			public Builder setCategories(Collection<Category> categories) {
				this.categories.clear();
				categories.forEach(this::addCategory);
				return this;
			}

			public Item build() {
				if (name == null) {
					throw new IllegalArgumentException("Item name required.");
				}

				return new Item(this);
			}
		}
	}

	/**
	 * <p>
	 * Represents an item name that is used in the EMC rupee transaction history which is different from the item name that EMC Shopkeeper uses for that particular item.
	 * This is also used when I decide to change one of EMC Shopkeeper's item names.
	 * </p>
	 * <p>
	 * For example, "Black Terracotta" used to be referred to as "Black Clay" in the rupee transaction history.
	 * </p>
	 * @author Michael Angstadt
	 */
	public static class EmcName {
		private final String alias;
		private final LocalDateTime timeFrom, timeTo;

		/**
		 * @param alias the item name used in the rupee transaction history
		 */
		public EmcName(String alias) {
			this(alias, LocalDateTime.MIN, LocalDateTime.MAX);
		}

		/**
		 * @param alias the item name used in the rupee transaction history
		 * @param timeFrom when the rupee transaction history started using this name (inclusive)
		 * @param timeTo when the rupee transaction history stopped using this name (exclusive)
		 */
		public EmcName(String alias, LocalDateTime timeFrom, LocalDateTime timeTo) {
			if (!timeFrom.isBefore(timeTo)) {
				throw new IllegalArgumentException("Start time in time-bounded emcName \"" + alias + "\" does not come before the end time.\nStart time: " + timeFrom + "\nEnd time: " + timeTo);
			}

			this.alias = alias;
			this.timeFrom = timeFrom;
			this.timeTo = timeTo;
		}

		/**
		 * Gets the item name used in the rupee transaction history.
		 * @return the item name
		 */
		public String getAlias() {
			return alias;
		}

		/**
		 * Gets the point in time when the rupee transaction history started using this name (inclusive).
		 * @return the start time
		 */
		public LocalDateTime getTimeFrom() {
			return timeFrom;
		}

		/**
		 * Gets the point in time when the rupee transaction history stopped using this name (inclusive).
		 * @return the stop time
		 */
		public LocalDateTime getTimeTo() {
			return timeTo;
		}
	}

	/**
	 * Holds the information on a category.
	 */
	public static class Category {
		private final int id;
		private final String name;
		private final URL icon;

		public Category(int id, String name, URL icon) {
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

		public URL getIcon() {
			return icon;
		}
	}

	private static class ColorFormatter {
		private static final char FORMAT_ESCAPE_CHAR = '§';

		/**
		 * @see "https://empireminecraft.com/wiki/formatted-signs/"
		 */
		private static final Map<String, String> COLOR_CODE_TO_HEX = ImmutableMap.<String, String>builder() //@formatter:off
			.put("0", "000000")
			.put("1", "0000BF")
			.put("2", "00BF00")
			.put("3", "00BFBF")
			.put("4", "BF0000")
			.put("5", "BF00BF")
			.put("6", "BFBF00")
			.put("7", "BFBFBF")
			.put("8", "404040")
			.put("9", "4040FF")
			.put("a", "49FF40")
			.put("b", "40FFFF")
			.put("c", "FF4040")
			.put("d", "FF40FF")
			.put("e", "BFBF00") //bright yellow, too hard to see, real color code: FFFF40
			.put("f", "000000") //white, can't see, real color code: FFFFFF
		.build(); //@formatter:on

		/**
		 * @see "https://empireminecraft.com/wiki/formatted-signs/"
		 */
		private static final Map<String, String> FORMAT_CODE_TO_TAG = ImmutableMap.<String, String>builder() //@formatter:off
			.put("l", "b")
			.put("m", "s")
			.put("n", "u")
			.put("o", "i")
		.build(); //@formatter:on

		/**
		 * Converts the EMC formatting codes to HTML.
		 * @param nameFormatted the item name with formatting codes
		 * @param includeColor true to include color, false not to
		 * @return the HTML-formatted item name
		 * @see "https://empireminecraft.com/wiki/formatted-signs/"
		 */
		public static String toHtml(String nameFormatted, boolean includeColor) {
			StringBuilder html = new StringBuilder();

			boolean code = false;
			List<String> openTags = new ArrayList<>();
			for (int i = 0; i < nameFormatted.length(); i++) {
				char c = nameFormatted.charAt(i);

				if (code) {
					code = false;

					//color
					if (includeColor) {
						String hex = COLOR_CODE_TO_HEX.get(c + "");
						if (hex != null) {
							html.append("<font color=\"#").append(hex).append("\">");
							openTags.add("font");
							continue;
						}
					}

					//bold, italic, underline, strikethrough
					String tag = FORMAT_CODE_TO_TAG.get(c + "");
					if (tag != null) {
						html.append("<").append(tag).append(">");
						openTags.add(tag);
						continue;
					}

					//reset
					if (c == 'r') {
						closeOpenTags(html, openTags);
						continue;
					}

					//code not recognized, ignore it
					continue;
				}

				if (c == FORMAT_ESCAPE_CHAR) {
					code = true;
					continue;
				}

				html.append(c);
			}

			closeOpenTags(html, openTags);

			return html.toString();
		}

		private static void closeOpenTags(StringBuilder sb, List<String> openTags) {
			Collections.reverse(openTags);
			for (String openTag : openTags) {
				sb.append("</").append(openTag).append(">");
			}
			openTags.clear();
		}
	}

	private static class XmlParser {
		private static final String TIME_RANGE_SEPARATOR = "_";
		private static final Pattern emcNameRegex = Pattern.compile("^(\\[(.*?)])?(.*)$");
		private final Leaf document;

		public XmlParser(InputStream in) throws IOException, SAXException {
			try {
				DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
				fact.setIgnoringComments(true);
				fact.setIgnoringElementContentWhitespace(true);

				document = new Leaf(fact.newDocumentBuilder().parse(in));
			} catch (ParserConfigurationException e) {
				//should never be thrown
				throw new RuntimeException(e);
			}
		}

		public ItemIndex parse() {
			Map<Integer, Category> categoriesById = parseCategories();
			Map<String, LocalDateTime> serverUpdates = parseServerUpdates();
			List<Item> items = parseItems(categoriesById, serverUpdates);

			return new ItemIndex(items);
		}

		private Map<String, LocalDateTime> parseServerUpdates() {
			Map<String, LocalDateTime> serverUpdates = new HashMap<>();
			List<Leaf> elements = document.select("/ItemIndex/ServerUpdates/Update");

			for (Leaf element : elements) {
				String version = element.attribute("version");
				if (version.contains(TIME_RANGE_SEPARATOR) || version.contains(",") || version.contains("[") || version.contains("]")) {
					throw new IllegalArgumentException("\"version\" attribute of \"Update\" element cannot contain underscores, commas, or brackets.");
				}

				LocalDateTime ts = parseISOInstant(element.attribute("ts"));

				serverUpdates.put(version, ts);
			}

			return serverUpdates;
		}

		private Map<Integer, Category> parseCategories() {
			Map<Integer, Category> categories = new HashMap<>();
			List<Leaf> elements = document.select("/ItemIndex/Categories/Category");

			for (Leaf element : elements) {
				Category category = parseCategory(element);
				if (categories.containsKey(category.id)) {
					throw new IllegalArgumentException("Multiple categories share the same id: " + category.id);
				}

				categories.put(category.id, category);
			}

			return categories;
		}

		private Category parseCategory(Leaf element) {
			String name = element.attribute("name");
			if (name.isEmpty()) {
				throw new IllegalArgumentException("Category is missing a name.");
			}

			String idStr = element.attribute("id");
			if (idStr.isEmpty()) {
				throw new IllegalArgumentException("ID missing for category \"" + name + "\".");
			}

			int id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("ID for category \"" + name + "\" is not a number: " + idStr, e);
			}

			URL icon = null;
			String iconStr = element.attribute("icon");
			if (!iconStr.isEmpty()) {
				icon = imageFromFileName(iconStr);
				if (icon == null) {
					throw new IllegalArgumentException("Icon not found for category \"" + name + "\": " + iconStr);
				}
			}

			return new Category(id, name, icon);
		}

		private List<Item> parseItems(Map<Integer, Category> categoriesById, Map<String, LocalDateTime> serverUpdates) {
			List<Item> items = new ArrayList<>();
			List<Leaf> itemElements = document.select("/ItemIndex/Items/Item");

			for (Leaf itemElement : itemElements) {
				Item item = parseItem(itemElement, categoriesById, serverUpdates);
				items.add(item);
			}

			return items;
		}

		private Item parseItem(Leaf element, Map<Integer, Category> categoriesById, Map<String, LocalDateTime> serverUpdates) {
			Item.Builder builder = new Item.Builder();

			String name = element.attribute("name");
			builder.setName(name);

			String nameColored = element.attribute("nameColored");
			if (!nameColored.isEmpty()) {
				builder.setNameColored(nameColored);
			}

			String emcNamesStr = element.attribute("emcNames");
			if (!emcNamesStr.isEmpty()) {
				splitValues(emcNamesStr).stream()
					.map(emcNameStr -> parseEmcName(emcNameStr, serverUpdates))
				.forEach(builder::addEmcName);
			}

			String idsStr = element.attribute("id");
			builder.setIds(splitValues(idsStr));

			String image = element.attribute("image");
			if (image.isEmpty()) {
				image = defaultImageFileName(name);
			}
			URL imageUrl = imageFromFileName(image);
			if (imageUrl == null) {
				throw new IllegalArgumentException("Image not found for item \"" + name + "\": " + image);
			}
			builder.setImage(imageUrl);

			String stackSize = element.attribute("stack");
			if (!stackSize.isEmpty()) {
				builder.setStackSize(Integer.parseInt(stackSize));
			}

			String groups = element.attribute("group");
			builder.setGroups(splitValues(groups));

			String categories = element.attribute("categories");
			splitValues(categories).stream() //@formatter:off
				.map(Integer::valueOf)
				.map(id -> {
					Category category = categoriesById.get(id);
					if (category == null) {
						throw new IllegalArgumentException("Unknown category ID \"" + id + "\" for item \"" + name + "\".");
					}
					return category;
				})
			.forEach(builder::addCategory); //@formatter:on

			return builder.build();
		}

		private EmcName parseEmcName(String aliasStr, Map<String, LocalDateTime> serverUpdates) {
			Matcher m = emcNameRegex.matcher(aliasStr);
			if (!m.find()) {
				//should never happen due to how the regex is written
				throw new AssertionError("Regex \"" + emcNameRegex.pattern() + "\" failed to match on emcName value \"" + aliasStr + "\".");
			}

			LocalDateTime timeFrom = LocalDateTime.MIN;
			LocalDateTime timeTo = LocalDateTime.MAX;
			String timeRange = m.group(2);
			if (timeRange != null) {
				int separatorPos = indexOfRequireSingleMatch(timeRange, TIME_RANGE_SEPARATOR);
				if (separatorPos < 0) {
					throw new IllegalArgumentException("Time range in emcName must contain exactly one underscore: " + aliasStr);
				}

				String timeFromStr = timeRange.substring(0, separatorPos);
				if (!timeFromStr.isEmpty()) {
					timeFrom = parseAliasTime(timeFromStr, serverUpdates);
				}

				String timeToStr = timeRange.substring(separatorPos+TIME_RANGE_SEPARATOR.length());
				if (!timeToStr.isEmpty()) {
					timeTo = parseAliasTime(timeToStr, serverUpdates);
				}
			}

			String alias = m.group(3);

			return new EmcName(alias, timeFrom, timeTo);
		}

		/**
		 * Returns the index within the given string of the first occurrence of the given substring,
		 * but only if there is exactly one occurrence of the substring in the haystack.
		 * @param haystack the string
		 * @param needle the substring
		 * @return the index of the first occurrence or -1 if the substring can't be found
		 * or -1 if there are multiple occurrences of the substring
		 */
		@SuppressWarnings("SameParameterValue")
		private int indexOfRequireSingleMatch(String haystack, String needle) {
			int pos1 = haystack.indexOf(needle);
			boolean firstMatchFound = (pos1 >= 0);
			if (!firstMatchFound) {
				return -1;
			}

			int pos2 = haystack.indexOf(needle, pos1 + needle.length());
			boolean secondMatchFound = (pos2 >= 0);
			return secondMatchFound ? -1 : pos1;
		}

		private LocalDateTime parseAliasTime(String timeStr, Map<String, LocalDateTime> serverUpdates) {
			timeStr = timeStr.trim();
			LocalDateTime ts = serverUpdates.get(timeStr);
			if (ts == null) {
				ts = parseISOInstant(timeStr);
			}
			return ts;
		}

		private LocalDateTime parseISOInstant(String timeStr) {
			return Instant.parse(timeStr).atZone(ZoneId.systemDefault()).toLocalDateTime();
		}

		private List<String> splitValues(String value) {
			return value.isEmpty() ? Collections.emptyList() : Arrays.asList(value.split("\\s*,\\s*"));
		}
	}
}
