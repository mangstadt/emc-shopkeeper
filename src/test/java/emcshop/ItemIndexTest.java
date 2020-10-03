package emcshop;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMultimap;
import emcshop.util.DefaultTimezoneRule;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.Multimap;

import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;

/**
 * @author Michael Angstadt
 */
public class ItemIndexTest {
	@Rule
	public final DefaultTimezoneRule defaultTimeZoneRule = new DefaultTimezoneRule("UTC");

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Test
	public void parse_serverupdates_underscore_in_version() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("\"version\" attribute of \"Update\" element cannot contain underscores, commas, or brackets.");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<ServerUpdates>" +
				"<Update version=\"1_13\" ts=\"2019-03-15T17:30:00Z\" />" +
			"</ServerUpdates>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_serverupdates_comma_in_version() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("\"version\" attribute of \"Update\" element cannot contain underscores, commas, or brackets.");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<ServerUpdates>" +
				"<Update version=\"1,13\" ts=\"2019-03-15T17:30:00Z\" />" +
			"</ServerUpdates>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_serverupdates_brackets_in_version() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("\"version\" attribute of \"Update\" element cannot contain underscores, commas, or brackets.");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<ServerUpdates>" +
				"<Update version=\"[1.13]\" ts=\"2019-03-15T17:30:00Z\" />" +
			"</ServerUpdates>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_serverupdates_bad_timestamp() throws Exception {
		thrown.expect(DateTimeParseException.class);

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<ServerUpdates>" +
				"<Update version=\"1.13\" ts=\"2019-03-15T17:30:00\" />" + //missing Z
			"</ServerUpdates>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_categories_missing_id() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("ID missing for category \"Animals\".");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Categories>" +
				"<Category name=\"Animals\" />" +
			"</Categories>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_categories_missing_name() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Category is missing a name.");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Categories>" +
				"<Category id=\"0\" />" +
			"</Categories>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_categories_bad_id() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("ID for category \"Animals\" is not a number: text");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Categories>" +
				"<Category id=\"text\" name=\"Animals\" />" +
			"</Categories>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_categories_duplicate_id() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Multiple categories share the same id: 0");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Categories>" +
				"<Category id=\"0\" name=\"Animals\" />" +
				"<Category id=\"0\" name=\"Clay &amp; Terracotta\" />" +
			"</Categories>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_categories_image_file_not_found() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Icon not found for category \"Animals\": does_not_exist.png");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Categories>" +
				"<Category id=\"0\" name=\"Animals\" icon=\"does_not_exist.png\" />" +
			"</Categories>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_items_unknown_category_id() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Unknown category ID \"1\" for item \"Diamond\"");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Categories>" +
				"<Category id=\"0\" name=\"Animals\" />" +
			"</Categories>" +
			"<Items>" +
				"<Item name=\"Diamond\" categories=\"1\"/>" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_items_missing_optional_attributes() throws Exception {
		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Items>" +
				"<Item name=\"Diamond\" />" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex index = ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

		ItemIndex.Item item = index.getItemByDisplayName("Diamond");
		assertEquals(Collections.emptyList(), item.getCategories());
		assertEquals(Collections.emptyList(), item.getEmcNames());
		assertEquals(Collections.emptyList(), item.getGroups());
		assertEquals(Collections.emptyList(), item.getIds());
		assertItemImageUrl("diamond.png", item.getImage());
		assertEquals("Diamond", item.getName());
		assertEquals("Diamond", item.getNameColored());
		assertEquals(64, item.getStackSize());
	}

	@Test
	public void parse_items_all_attributes_defined() throws Exception {
		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Categories>" +
				"<Category id=\"0\" name=\"Wood\" />" +
				"<Category id=\"1\" name=\"Blocks\" />" +
			"</Categories>" +
			"<Items>" +
				"<Item name=\"Spruce Log\" nameColored=\"§aSpruce Log\" stack=\"16\" id=\"17:1,111\" categories=\"0,1\" group=\"one,two\" image=\"oak_log.png\" emcNames=\"Pine Log,Pine Wood\" />" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex index = ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

		ItemIndex.Item item = index.getItemByDisplayName("Spruce Log");
		assertEquals(Arrays.asList(0, 1), item.getCategories().stream().map(ItemIndex.Category::getId).collect(Collectors.toList()));
		assertEquals(Arrays.asList("Pine Log", "Pine Wood"), item.getEmcNames().stream().map(ItemIndex.EmcName::getAlias).collect(Collectors.toList()));
		assertEquals(Arrays.asList("one", "two"), item.getGroups());
		assertEquals(Arrays.asList("17:1", "111"), item.getIds());
		assertItemImageUrl("oak_log.png", item.getImage());
		assertEquals("Spruce Log", item.getName());
		assertEquals("§aSpruce Log", item.getNameColored());
		assertEquals(16, item.getStackSize());
	}

	@Test
	public void parse_items_time_bounded_emcName_bad_ts() throws Exception {
		thrown.expect(DateTimeParseException.class);

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Items>" +
				"<Item name=\"Oak Log\" emcNames=\"[_2019-03-15T17:30:00]Oak Wood\" />" + //missing Z
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_items_time_bounded_emcName_missing_underscore() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Time range in emcName must contain exactly one underscore: [2019-01-15T17:30:00Z]Oak Wood");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Items>" +
				"<Item name=\"Oak Log\" emcNames=\"[2019-01-15T17:30:00Z]Oak Wood\" />" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_items_time_bounded_emcName_too_many_underscores() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Time range in emcName must contain exactly one underscore: [2019-01-15T17:30:00Z_2019-03-15T17:30:00Z_]Oak Wood");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Items>" +
				"<Item name=\"Oak Log\" emcNames=\"[2019-01-15T17:30:00Z_2019-03-15T17:30:00Z_]Oak Wood\" />" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_items_time_bounded_emcName_wrong_ts_order() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Start time in time-bounded emcName \"Oak Wood\" does not come before the end time.\n" +
			"Start time: 2020-03-15T17:30\n" +
			"End time: 2019-03-15T17:30");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Items>" +
				"<Item name=\"Oak Log\" emcNames=\"[2020-03-15T17:30:00Z_2019-03-15T17:30:00Z]Oak Wood\" />" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_items_time_bounded_emcName_unknown_version() throws Exception {
		thrown.expect(DateTimeParseException.class);

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Items>" +
				"<Item name=\"Oak Log\" emcNames=\"[_1.13]Oak Wood\" />" + //<ServerUpdates> definition for "1.13" not supplied, so it tries to parse "1.13" as a timestamp
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_items_time_bounded_emcNames() throws Exception {
		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<ServerUpdates>" +
				"<Update version=\"1.13\" ts=\"2019-03-15T17:00:00Z\" />" +
			"</ServerUpdates>" +
			"<Items>" +
				"<Item name=\"Acacia Log\" emcNames=\"[_1.13]Acacia Wood\" />" +
				"<Item name=\"Birch Log\" emcNames=\"[_2020-02-01T00:00:00Z]Birch Wood\" />" +
				"<Item name=\"Dark Oak Log\" emcNames=\"[2020-02-01T00:00:00Z_]Dark Oak Wood\" />" +
				"<Item name=\"Oak Log\" emcNames=\"[2020-02-01T00:00:00Z_2020-03-01T00:00:00Z]Oak Wood\" />" +
				"<Item name=\"Spruce Log\" emcNames=\"[_]Pine Log\" />" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex index = ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

		assertEquals("Acacia Log", index.getDisplayName("Acacia Wood", LocalDateTime.of(2019,1,1,0,0,0)));
		assertEquals("Acacia Wood", index.getDisplayName("Acacia Wood", LocalDateTime.of(2019,3,15,17,0,0)));
		assertEquals("Acacia Wood", index.getDisplayName("Acacia Wood", LocalDateTime.of(2019,4,1,0,0,0)));

		assertEquals("Birch Log", index.getDisplayName("Birch Wood", LocalDateTime.of(2020,1,1,0,0,0)));
		assertEquals("Birch Wood", index.getDisplayName("Birch Wood", LocalDateTime.of(2020,2,1,0,0,0)));
		assertEquals("Birch Wood", index.getDisplayName("Birch Wood", LocalDateTime.of(2020,3,1,0,0,0)));

		assertEquals("Dark Oak Wood", index.getDisplayName("Dark Oak Wood", LocalDateTime.of(2020,1,1,0,0,0)));
		assertEquals("Dark Oak Log", index.getDisplayName("Dark Oak Wood", LocalDateTime.of(2020,2,1,0,0,0)));
		assertEquals("Dark Oak Log", index.getDisplayName("Dark Oak Wood", LocalDateTime.of(2020,3,1,0,0,0)));

		assertEquals("Oak Wood", index.getDisplayName("Oak Wood", LocalDateTime.of(2020,1,1,0,0,0)));
		assertEquals("Oak Log", index.getDisplayName("Oak Wood", LocalDateTime.of(2020,2,1,0,0,0)));
		assertEquals("Oak Log", index.getDisplayName("Oak Wood", LocalDateTime.of(2020,2,15,0,0,0)));
		assertEquals("Oak Wood", index.getDisplayName("Oak Wood", LocalDateTime.of(2020,3,1,0,0,0)));
		assertEquals("Oak Wood", index.getDisplayName("Oak Wood", LocalDateTime.of(2020,4,1,0,0,0)));

		assertEquals("Spruce Log", index.getDisplayName("Pine Log", LocalDateTime.now()));
	}

	@Test
	public void parse_items_default_image_not_found() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Image not found for item \"Foo Bar\": foo_bar.png");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Items>" +
				"<Item name=\"Foo Bar\" />" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void parse_items_image_not_found() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Image not found for item \"Foo Bar\": foo.png");

		String xml = //@formatter:off
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<ItemIndex>" +
			"<Items>" +
				"<Item name=\"Foo Bar\" image=\"foo.png\" />" +
			"</Items>" +
		"</ItemIndex>"; //@formatter:on

		ItemIndex.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void constructor_duplicate_item_name() {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Duplicate item name: Diamond");

		new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.build()
		)); //@formatter:on
	}

	@Test
	public void constructor_emcName_conflicts_with_item_name() {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("An emcName of item \"Oak Log\" matches the name of an existing item: \"Oak Wood\"");

		new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Oak Log")
				.addEmcName(new ItemIndex.EmcName("Oak Wood"))
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Oak Wood")
				.build()
		)); //@formatter:on
	}

	@Test
	public void constructor_duplicate_item_id() {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Duplicate ID: 264");

		new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.addId("264")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Diamond Block")
				.addId("264")
				.build()
		)); //@formatter:on
	}

	@Test
	public void constructor_duplicate_emcName() {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("emcName \"Blue Shiny\" for items \"Diamond Block\" and \"Diamond\" have overlapping time boundaries.");

		new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.addEmcName(new ItemIndex.EmcName("Blue Shiny"))
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Diamond Block")
				.addEmcName(new ItemIndex.EmcName("Blue Shiny"))
				.build()
		)); //@formatter:on
	}

	@Test
	public void constructor_duplicate_emcName_time_ranges_overlapping() {
		LocalDateTime[][] testCases = { //@formatter:off
			//partially overlapping
			{
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 3, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0),
				LocalDateTime.of(2020, 4, 1, 0, 0, 0)
			},

			//one is inside of the other
			{
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 4, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0),
				LocalDateTime.of(2020, 3, 1, 0, 0, 0)
			},

			//identical
			{
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0),
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0)
			},

			//identical start date
			{
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 3, 1, 0, 0, 0),
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0)
			},

			//identical end date
			{
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 3, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0),
				LocalDateTime.of(2020, 3, 1, 0, 0, 0)
			}
		}; //@formatter:on

		for (LocalDateTime[] testCase : testCases) {
			//test both combinations of the date ranges
			LocalDateTime[][] combinations = { //@formatter:off
				{ testCase[0], testCase[1], testCase[2], testCase[3] },
				{ testCase[2], testCase[3], testCase[0], testCase[1] }
			}; //@formatter:on

			for (LocalDateTime[] combination : combinations) {
				try {
					new ItemIndex(Arrays.asList( //@formatter:off
						new ItemIndex.Item.Builder()
							.setName("Diamond")
							.addEmcName(new ItemIndex.EmcName("Blue Shiny", combination[0], combination[1]))
							.build(),
						new ItemIndex.Item.Builder()
							.setName("Diamond Block")
							.addEmcName(new ItemIndex.EmcName("Blue Shiny", combination[2], combination[3]))
							.build()
					)); //@formatter:on

					fail("Expected IllegalArgumentException to be thrown.");
				} catch (IllegalArgumentException expected) {
					assertEquals("emcName \"Blue Shiny\" for items \"Diamond Block\" and \"Diamond\" have overlapping time boundaries.", expected.getMessage());
				}
			}
		}
	}

	@Test
	public void constructor_duplicate_emcName_time_ranges_not_overlapping() {
		LocalDateTime[][] testCases = { //@formatter:off
			//one ends when the other begins
			{
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0),
				LocalDateTime.of(2020, 3, 1, 0, 0, 0)
			},

			//completely separate
			{
				LocalDateTime.of(2020, 1, 1, 0, 0, 0),
				LocalDateTime.of(2020, 2, 1, 0, 0, 0),
				LocalDateTime.of(2020, 3, 1, 0, 0, 0),
				LocalDateTime.of(2020, 4, 1, 0, 0, 0)
			}
		}; //@formatter:on

		for (LocalDateTime[] testCase : testCases) {
			//test both combinations of the date ranges
			LocalDateTime[][] combinations = { //@formatter:off
				{ testCase[0], testCase[1], testCase[2], testCase[3] },
				{ testCase[2], testCase[3], testCase[0], testCase[1] }
			}; //@formatter:on

			for (LocalDateTime[] combination : combinations) {
				new ItemIndex(Arrays.asList( //@formatter:off
					new ItemIndex.Item.Builder()
						.setName("Diamond")
						.addEmcName(new ItemIndex.EmcName("Blue Shiny", combination[0], combination[1]))
						.build(),
					new ItemIndex.Item.Builder()
						.setName("Diamond Block")
						.addEmcName(new ItemIndex.EmcName("Blue Shiny", combination[2], combination[3]))
						.build()
				)); //@formatter:on
			}
		}
	}

	@Test
	public void isItemNameRecognized() {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Potion of Fire Resistance")
				.addEmcName(new ItemIndex.EmcName("Potion:8195"))
				.build()
		)); //@formatter:on

		assertTrue(index.isItemNameRecognized("Potion of Fire Resistance"));
		assertTrue(index.isItemNameRecognized("Potion:8195"));
		assertFalse(index.isItemNameRecognized("Potion:9999"));
	}

	@Test
	public void isUnknownItem() {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.addEmcName(new ItemIndex.EmcName("Blue Shiny"))
				.build()
		)); //@formatter:on

		assertFalse(index.isUnknownItem("Diamond"));
		assertTrue(index.isUnknownItem("Blue Shiny")); //does not check emcNames
		assertTrue(index.isUnknownItem("Unknown"));
	}

	@Test
	public void getAllGroups() {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Apple")
				.addGroup("One")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Birch Log")
				.addGroup("One")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Cobblestone")
				.addGroup("Two")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.addGroup("one")
				.addGroup("two")
				.build()
		)); //@formatter:on

		assertContainsInAnyOrder(Arrays.asList("One", "one", "Two", "two"), index.getAllGroups());
	}

	@Test
	public void getGroups() {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Apple")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Birch Log")
				.addGroup("One")
				.addEmcName(new ItemIndex.EmcName("Birch Wood"))
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Cobblestone")
				.addGroup("Two")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.addGroup("One")
				.addGroup("Two")
				.build()
		)); //@formatter:on

		assertContainsInAnyOrder(Arrays.asList(), index.getGroups("Apple"));
		assertContainsInAnyOrder(Arrays.asList("One"), index.getGroups("Birch Log"));
		assertContainsInAnyOrder(Arrays.asList(), index.getGroups("Birch Wood")); //emcNames not accepted
		assertContainsInAnyOrder(Arrays.asList("Two"), index.getGroups("Cobblestone"));
		assertContainsInAnyOrder(Arrays.asList("One", "Two"), index.getGroups("Diamond"));
	}

	@Test
	public void getAllCategories() {
		ItemIndex.Category c1 = new ItemIndex.Category(1, "One", null);
		ItemIndex.Category c2 = new ItemIndex.Category(2, "Two", null);

		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Apple")
				.addCategory(c1)
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Birch Log")
				.addCategory(c1)
				.addCategory(c2)
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Cobblestone")
				.addCategory(c2)
				.build()
		)); //@formatter:on

		assertContainsInAnyOrder(Arrays.asList(c1, c2), index.getAllCategories());
	}

	@Test
	public void getCategories() {
		ItemIndex.Category c1 = new ItemIndex.Category(1, "One", null);
		ItemIndex.Category c2 = new ItemIndex.Category(2, "Two", null);

		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Apple")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Birch Log")
				.addCategory(c1)
				.addEmcName(new ItemIndex.EmcName("Birch Wood"))
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Cobblestone")
				.addCategory(c2)
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.addCategory(c1)
				.addCategory(c2)
				.build()
		)); //@formatter:on

		assertContainsInAnyOrder(Arrays.asList(), index.getCategories("Apple"));
		assertContainsInAnyOrder(Arrays.asList(c1), index.getCategories("Birch Log"));
		assertContainsInAnyOrder(Arrays.asList(), index.getCategories("Birch Wood")); //emcNames not accepted
		assertContainsInAnyOrder(Arrays.asList(c2), index.getCategories("Cobblestone"));
		assertContainsInAnyOrder(Arrays.asList(c1, c2), index.getCategories("Diamond"));
	}

	@Test
	public void getDisplayNameFromMinecraftId() {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.addId("264")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Red Wool")
				.addId("35:14")
				.build()
		)); //@formatter:on

		assertEquals("Diamond", index.getDisplayNameFromMinecraftId("264"));
		assertEquals("Diamond", index.getDisplayNameFromMinecraftId("264:0"));
		assertEquals("Red Wool", index.getDisplayNameFromMinecraftId("35:14"));
		assertNull(index.getDisplayNameFromMinecraftId("22"));
	}

	@Test
	public void getDisplayNameToEmcNamesMapping() {
		ItemIndex.EmcName e1 = new ItemIndex.EmcName("One");
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Apple")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Birch Wood")
				.addEmcName(e1)
				.build()
		)); //@formatter:on

		Multimap<String, ItemIndex.EmcName> expected = new ImmutableMultimap.Builder<String, ItemIndex.EmcName>() //@formatter:off
			.put("Birch Wood", e1)
			.build(); //@formatter:on

		assertEquals(expected, index.getDisplayNameToEmcNamesMapping());
	}

	@Test
	public void getImage() throws Exception {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Zombie Potion")
				.addEmcName(new ItemIndex.EmcName("Zomb Pot"))
				.setImage(new URL("file:///water_bottle.png"))
				.build()
		)); //@formatter:on

		assertNull(index.getImage("Diamond"));
		assertItemImageUrl("water_bottle.png", index.getImage("Zombie Potion"));
		assertNull(index.getImage("Zomb Pot")); //method does not accept emcNames
		assertItemImageUrl("bow.png", index.getImage("Bow-eo0f"));
		assertNull(index.getImage("Item Name"));
	}

	@Test
	public void getItemNames() {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Spruce Log")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.build()
		)); //@formatter:on

		List<String> actual = index.getItemNames();
		List<String> expected = Arrays.asList("Diamond", "Spruce Log");
		assertEquals(expected, actual);
	}

	@Test
	public void getStackSize() {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Stone Sword")
				.setStackSize(1)
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Ender Pearl")
				.addEmcName(new ItemIndex.EmcName("Ender"))
				.setStackSize(16)
				.build()
		)); //@formatter:on

		assertEquals(64, index.getStackSize("Diamond"));
		assertEquals(1, index.getStackSize("Stone Sword"));
		assertEquals(64, index.getStackSize("Ender")); //method does not accept emcNames
		assertEquals(64, index.getStackSize("unknown"));
	}

	@Test
	public void getItemNameFormatted_without_color() {
		ItemIndex index = new ItemIndex(Arrays.asList( //@formatter:off
			new ItemIndex.Item.Builder()
				.setName("Mineral Mincer")
				.setNameColored("§6Mineral§r §lMincer")
				.addEmcName(new ItemIndex.EmcName("Mincy"))
				.build(),
			new ItemIndex.Item.Builder()
				.setName("Diamond")
				.build()
		)); //@formatter:on

		assertEquals("Mineral <b>Mincer</b>", index.getItemNameFormatted("Mineral Mincer", false));
		assertEquals("<font color=\"#BFBF00\">Mineral</font> <b>Mincer</b>", index.getItemNameFormatted("Mineral Mincer", true));
		assertEquals("Mincy", index.getItemNameFormatted("Mincy", false)); //method does not accept emcNames
		assertEquals("Mincy", index.getItemNameFormatted("Mincy", true)); //method does not accept emcNames
		assertEquals("Diamond", index.getItemNameFormatted("Diamond", false));
		assertEquals("Diamond", index.getItemNameFormatted("Diamond", true));
	}

	@Test
	public void Item_Builder_addId_null() {
		thrown.expect(NullPointerException.class);

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.addId(null)
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_addId_chop_zero() {
		ItemIndex.Item item = new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.addId("264:0")
			.build(); //@formatter:on

		assertEquals(Arrays.asList("264"), item.getIds());
	}

	@Test
	public void Item_Builder_setIds_chop_zero() {
		ItemIndex.Item item = new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setIds(Arrays.asList("264:0"))
			.build(); //@formatter:on

		assertEquals(Arrays.asList("264"), item.getIds());
	}

	@Test
	public void Item_Builder_addEmcName_null() {
		thrown.expect(NullPointerException.class);

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.addEmcName(null)
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_setEmcNames_null() {
		thrown.expect(NullPointerException.class);

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setEmcNames(Arrays.asList(null))
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_setEmcNames() {
		ItemIndex.EmcName e1 = new ItemIndex.EmcName("One");
		ItemIndex.EmcName e2 = new ItemIndex.EmcName("Two");

		ItemIndex.Item item = new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setEmcNames(Arrays.asList(e1, e2))
			.build(); //@formatter:on

		assertContainsInAnyOrder(Arrays.asList(e1, e2), item.getEmcNames());
	}

	@Test
	public void Item_Builder_addGroup_null() {
		thrown.expect(NullPointerException.class);

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.addGroup(null)
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_setGroups_null() {
		thrown.expect(NullPointerException.class);

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setGroups(Arrays.asList(null))
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_setGroups() {
		ItemIndex.Item item = new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setGroups(Arrays.asList("One", "Two"))
			.build(); //@formatter:on

		assertContainsInAnyOrder(Arrays.asList("One", "Two"), item.getGroups());
	}

	@Test
	public void Item_Builder_addCategory_null() {
		thrown.expect(NullPointerException.class);

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.addCategory(null)
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_setCategories() {
		ItemIndex.Category c1 = new ItemIndex.Category(1, "One", null);
		ItemIndex.Category c2 = new ItemIndex.Category(2, "Two", null);

		ItemIndex.Item item = new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setCategories(Arrays.asList(c1, c2))
			.build(); //@formatter:on

		assertContainsInAnyOrder(Arrays.asList(c1, c2), item.getCategories());
	}

	@Test
	public void Item_Builder_setCategories_null() {
		thrown.expect(NullPointerException.class);

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setCategories(Arrays.asList(null))
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_setName_null() {
		thrown.expect(NullPointerException.class);

		new ItemIndex.Item.Builder() //@formatter:off
			.setName(null)
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_setStackSize_zero() {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Invalid stack size: 0");

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setStackSize(0)
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_setStackSize_negative() {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Invalid stack size: -1");

		new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond")
			.setStackSize(-1)
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_build_name_required() {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Item name required.");

		new ItemIndex.Item.Builder() //@formatter:off
			.build(); //@formatter:on
	}

	@Test
	public void Item_Builder_build_defaults() {
		ItemIndex.Item item = new ItemIndex.Item.Builder() //@formatter:off
			.setName("Diamond Block")
			.build(); //@formatter:on

		assertEquals(Collections.emptyList(), item.getCategories());
		assertEquals(Collections.emptyList(), item.getEmcNames());
		assertEquals(Collections.emptyList(), item.getGroups());
		assertEquals(Collections.emptyList(), item.getIds());
		assertNull(item.getImage());
		assertEquals("Diamond Block", item.getName());
		assertEquals("Diamond Block", item.getNameColored());
		assertEquals(64, item.getStackSize());
	}

	@Test
	public void instance() {
		ItemIndex index1 = ItemIndex.instance();
		ItemIndex index2 = ItemIndex.instance();
		assertSame(index1, index2);
	}

	private static void assertItemImageUrl(String expectedFileName, URL actual) {
		String file = actual.getFile();
		String actualFileName = file.substring(file.lastIndexOf('/') + 1);
		assertEquals(actualFileName, expectedFileName);
	}

	private static <T> void assertContainsInAnyOrder(Collection<T> expected, Collection<T> actual) {
		boolean failed;
		if (expected.size() != actual.size()) {
			failed = true;
		} else {
			Collection<T> actualCopy = new LinkedList<>(actual);
			failed = expected.stream().filter(t -> !actualCopy.remove(t)).findFirst().isPresent();
		}

		if (failed) {
			throw new AssertionError("Collections contain different values:\n" + expected + "\n" + actual);
		}
	}
}
