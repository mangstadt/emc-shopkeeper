package emcshop.scraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class PlayerProfileScraperTest {
	private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

	@Test
	public void scrapeProfile() throws Throwable {
		HttpClient client = createMockClient("SHAVINGFOAM");
		PlayerProfileScraper scraper = new PlayerProfileScraper();
		PlayerProfile profile = scraper.downloadProfile("SHAVINGFOAM", client);

		assertEquals("shavingfoam", profile.getPlayerName());
		assertFalse(profile.isPrivate());
		assertEquals("http://empireminecraft.com/data/avatars/l/12/12110.jpg?1389141773", profile.getPortraitUrl());
		assertEquals(Rank.IRON, profile.getRank());
		assertEquals(df.parse("2012-02-03"), profile.getJoined());
	}

	@Test
	public void scrapeProfile_player_does_not_exist() throws Throwable {
		HttpClient client = createMockClient("does-not-exist");
		PlayerProfileScraper scraper = new PlayerProfileScraper();
		PlayerProfile profile = scraper.downloadProfile("does-not-exist", client);

		assertNull(profile);
	}

	@Test
	public void scrapeProfile_private_profile() throws Throwable {
		HttpClient client = createMockClient("private");
		PlayerProfileScraper scraper = new PlayerProfileScraper();
		PlayerProfile profile = scraper.downloadProfile("private", client);

		assertEquals("private", profile.getPlayerName());
		assertTrue(profile.isPrivate());
		assertNull(profile.getPortraitUrl());
		assertNull(profile.getRank());
		assertNull(profile.getJoined());
	}

	@Test
	public void scrapeProfile_ranks() throws Throwable {
		assertRank("non-supporter", null);
		assertRank("contribution-team", null);
		assertRank("iron", Rank.IRON);
		assertRank("gold", Rank.GOLD);
		assertRank("diamond", Rank.DIAMOND);
		assertRank("mod", Rank.MODERATOR);
		assertRank("staff", Rank.SENIOR_STAFF);
		assertRank("developer", Rank.DEVELOPER);
		assertRank("admin1", Rank.ADMIN);
		assertRank("admin2", Rank.ADMIN);
	}

	private void assertRank(String player, Rank expected) throws IOException {
		HttpClient client = createMockClient(player);
		PlayerProfileScraper scraper = new PlayerProfileScraper();
		PlayerProfile profile = scraper.downloadProfile(player, client);

		Rank actual = profile.getRank();
		assertEquals(expected, actual);
	}

	@Test
	public void scrapeProfile_missing_join_date() throws Throwable {
		String player = "no-join-date";
		HttpClient client = createMockClient(player);
		PlayerProfileScraper scraper = new PlayerProfileScraper();
		PlayerProfile profile = scraper.downloadProfile(player, client);

		assertNull(profile.getJoined());
	}

	@Test
	public void scrapeProfile_bad_join_date() throws Throwable {
		String player = "bad-join-date";
		HttpClient client = createMockClient(player);
		PlayerProfileScraper scraper = new PlayerProfileScraper();
		PlayerProfile profile = scraper.downloadProfile(player, client);

		assertNull(profile.getJoined());
	}

	private static HttpClient createMockClient(final String expectedPlayerName) throws IOException {
		HttpClient client = mock(HttpClient.class);
		when(client.execute(Mockito.any(HttpGet.class))).then(new Answer<HttpResponse>() {
			@Override
			public HttpResponse answer(InvocationOnMock invocation) throws Throwable {
				HttpGet request = (HttpGet) invocation.getArguments()[0];
				String uri = request.getURI().toString();
				assertEquals("http://u.emc.gs/" + expectedPlayerName, uri);

				InputStream in = getClass().getResourceAsStream(expectedPlayerName.toLowerCase() + "-profile.html");
				if (in == null) {
					fail("Unexpected URI, check the unit test: " + uri);
				}

				HttpResponse response = mock(HttpResponse.class);

				HttpEntity entity = mock(HttpEntity.class);
				when(entity.getContent()).thenReturn(in);
				when(response.getEntity()).thenReturn(entity);

				return response;
			}
		});
		return client;
	}
}
