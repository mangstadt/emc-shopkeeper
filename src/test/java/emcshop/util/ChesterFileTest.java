package emcshop.util;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ChesterFileTest {
	@Test
	public void parse() throws Throwable {
		//@formatter:off
		String text =
		"1\n" +
		"10 20 30\n" +
		"19 64\n" +
		"5:3 12\n";
		//@formatter:on

		ChesterFile file = ChesterFile.parse(new StringReader(text));
		assertEquals("1", file.getVersion());
		assertEquals(10.0, file.getPlayerX(), 0.1);
		assertEquals(20.0, file.getPlayerY(), 0.1);
		assertEquals(30.0, file.getPlayerZ(), 0.1);

		Map<String, Integer> expectedItems = new HashMap<String, Integer>();
		expectedItems.put("19", 64);
		expectedItems.put("5:3", 12);
		assertEquals(expectedItems, file.getItems());
	}

	@Test(expected = IllegalArgumentException.class)
	public void parse_invalid_player_coords() throws Throwable {
		//@formatter:off
		String text =
		"1\n" +
		"a 20 30\n" +
		"19 64\n" +
		"5:3 12\n";
		//@formatter:on

		ChesterFile.parse(new StringReader(text));
	}

	@Test(expected = IllegalArgumentException.class)
	public void parse_too_few_player_coords() throws Throwable {
		//@formatter:off
		String text =
		"1\n" +
		"20 30\n" +
		"19 64\n" +
		"5:3 12\n";
		//@formatter:on

		ChesterFile.parse(new StringReader(text));
	}

	@Test(expected = IllegalArgumentException.class)
	public void parse_invalid_item_quantity() throws Throwable {
		//@formatter:off
		String text =
		"1\n" +
		"10 20 30\n" +
		"19 two\n" +
		"5:3 12\n";
		//@formatter:on

		ChesterFile.parse(new StringReader(text));
	}

	@Test(expected = IllegalArgumentException.class)
	public void parse_missing_item_quantity() throws Throwable {
		//@formatter:off
		String text =
		"1\n" +
		"10 20 30\n" +
		"19\n" +
		"5:3 12\n";
		//@formatter:on

		ChesterFile.parse(new StringReader(text));
	}
}
