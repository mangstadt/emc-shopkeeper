package emcshop.scraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class PlayerProfileScraperTest {
	@Test
	public void scrapeProfile() throws Throwable {
		PlayerProfile profile = scrape("the_boulder");
		assertEquals("The_Boulder", profile.getPlayerName());
		assertFalse(profile.isPrivate());
		assertEquals("https://empireminecraft.com/data/avatars/l/10/10465.jpg?1513848967", profile.getPortraitUrl());
		assertEquals("Senior Staff", profile.getRank());
		assertEquals("#49FF40", profile.getRankColor());
		assertEquals("Revered Member", profile.getTitle());
		assertEquals(LocalDate.of(2012, 1, 26), profile.getJoined());
	}

	@Test
	public void scrapeProfile_no_rank() throws Throwable {
		PlayerProfile profile = scrape("shavingfoam");
		assertEquals("shavingfoam", profile.getPlayerName());
		assertFalse(profile.isPrivate());
		assertEquals("https://empireminecraft.com/data/avatars/l/12/12110.jpg?1393197006", profile.getPortraitUrl());
		assertNull(profile.getRank());
		assertNull(profile.getRankColor());
		assertEquals("Dedicated Member", profile.getTitle());
		assertEquals(LocalDate.of(2012, 2, 3), profile.getJoined());
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
			return Jsoup.parse(in, "UTF-8", "https://empireminecraft.com");
		} finally {
			in.close();
		}
	}
}
