package emcshop.scraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class PlayerProfileScraperTest {
	private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

	@Test
	public void scrapeProfile() throws Throwable {
		PlayerProfile profile = scrape("shavingfoam");
		assertEquals("shavingfoam", profile.getPlayerName());
		assertFalse(profile.isPrivate());
		assertEquals("http://empireminecraft.com/data/avatars/l/12/12110.jpg?1389141773", profile.getPortraitUrl());
		assertEquals(Rank.IRON, profile.getRank());
		assertEquals("Iron Supporter", profile.getTitle());
		assertEquals(df.parse("2012-02-03"), profile.getJoined());
	}

	@Test
	public void scrapeProfile_player_does_not_exist() throws Throwable {
		PlayerProfile profile = scrape("does-not-exist");
		assertNull(profile);
	}

	@Test
	public void scrapeProfile_private_profile() throws Throwable {
		PlayerProfile profile = scrape("private");
		assertEquals("private", profile.getPlayerName());
		assertTrue(profile.isPrivate());
		assertNull(profile.getPortraitUrl());
		assertNull(profile.getRank());
		assertNull(profile.getTitle());
		assertNull(profile.getJoined());
	}

	@Test
	public void scrapeProfile_ranks() throws Throwable {
		assertRank("non-supporter", null);
		assertRank("iron", Rank.IRON);
		assertRank("gold", Rank.GOLD);
		assertRank("diamond", Rank.DIAMOND);
		assertRank("contribution-team", Rank.HELPER);
		assertRank("build-team", Rank.HELPER);
		assertRank("mod", Rank.MODERATOR);
		assertRank("staff", Rank.SENIOR_STAFF);
		assertRank("developer", Rank.DEVELOPER);
		assertRank("admin1", Rank.ADMIN);
		assertRank("admin2", Rank.ADMIN);
	}

	private void assertRank(String player, Rank expected) throws IOException {
		PlayerProfile profile = scrape(player);
		Rank actual = profile.getRank();
		assertEquals(expected, actual);
	}

	@Test
	public void scrapeProfile_missing_join_date() throws Throwable {
		PlayerProfile profile = scrape("no-join-date");
		assertNull(profile.getJoined());
	}

	@Test
	public void scrapeProfile_bad_join_date() throws Throwable {
		PlayerProfile profile = scrape("bad-join-date");
		assertNull(profile.getJoined());
	}

	private PlayerProfile scrape(String prefix) throws IOException {
		Document document = load(prefix + "-profile.html");
		PlayerProfileScraper scraper = new PlayerProfileScraper();
		return scraper.scrapeProfile(prefix, document);
	}

	private Document load(String file) throws IOException {
		InputStream in = getClass().getResourceAsStream(file);
		try {
			return Jsoup.parse(in, "UTF-8", "");
		} finally {
			in.close();
		}
	}
}
