package emcshop.gui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.JLabel;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;

public class ProfileLoaderTest {
	private final byte[] portrait;
	{
		try {
			portrait = IOUtils.toByteArray(getClass().getResourceAsStream("shavingfoam.jpg"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	//@formatter:off
	private final PlayerProfile profile = new PlayerProfile.Builder()
		.playerName("shavingfoam")
		.private_(false)
		.rank("Gold Supporter", null)
		.portraitUrl("http://empireminecraft.com/data/avatars/l/12/12110.jpg?1389141773")
		.joined(LocalDate.now())
	.build();
	//@formatter:on

	private Path portraitFile, propertiesFile;
	private ProfileDownloadedListener listener;
	private JLabel label;
	private PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
	private ProfileLoader profileImageLoader;

	@Rule
	public final TemporaryFolder temp = new TemporaryFolder();

	@Before
	public void before() {
		Path root = temp.getRoot().toPath();
		portraitFile = root.resolve(profile.getPlayerName());
		propertiesFile = root.resolve(profile.getPlayerName() + ".properties");

		listener = mock(ProfileDownloadedListener.class);
		label = mock(JLabel.class);
		scraper = mock(PlayerProfileScraper.class);
		profileImageLoader = create(temp.getRoot().toPath(), scraper);
	}

	@Test
	public void non_existent_user() throws Exception {
		String player = profile.getPlayerName();

		when(scraper.scrapeProfile(eq(player), any(Document.class))).thenReturn(null);

		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener, never()).onProfileDownloaded(any(PlayerProfile.class));
		assertFalse(Files.exists(portraitFile));
		assertFalse(Files.exists(propertiesFile));
	}

	@Test
	public void private_profile() throws Exception {
		String player = profile.getPlayerName();

		PlayerProfile profile = new PlayerProfile.Builder().playerName(player).private_(true).build();
		when(scraper.scrapeProfile(eq(player), any(Document.class))).thenReturn(profile);

		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener).onProfileDownloaded(profile);
		assertFalse(Files.exists(portraitFile));

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(propertiesFile)) {
			props.load(in);
		}
		assertEquals(2, props.size());
		assertEquals("true", props.get("private"));
	}

	@Test
	public void image_downloaded() throws Exception {
		String player = profile.getPlayerName();

		when(scraper.scrapeProfile(eq(player), any(Document.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), isNull(Instant.class), any(HttpClient.class))).thenReturn(portrait);

		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener).onProfileDownloaded(profile);

		byte[] expected = portrait;
		byte[] actual = Files.readAllBytes(portraitFile);
		assertArrayEquals(expected, actual);

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(propertiesFile)) {
			props.load(in);
		}
		assertEquals(4, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("Gold Supporter", props.get("rank"));
		assertNotNull(props.get("joined"));

		//loading it again should retrieve it from the cache
		//no HTTP calls should be made

		listener = mock(ProfileDownloadedListener.class);
		label = mock(JLabel.class);
		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener, never()).onProfileDownloaded(any(PlayerProfile.class));

		//the portrait should have only been downloaded once
		verify(scraper).downloadPortrait(any(PlayerProfile.class), any(Instant.class), any(HttpClient.class));
	}

	@Test
	public void image_not_modified() throws Exception {
		String player = profile.getPlayerName();
		temp.newFile(player);
		temp.newFile(player + ".properties");
		Instant lastModified = Instant.now();

		when(scraper.scrapeProfile(eq(player), any(Document.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), eq(lastModified), any(HttpClient.class))).thenReturn(null);

		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener).onProfileDownloaded(profile);

		assertEquals(0, Files.size(portraitFile)); //file should not have been modified

		//properties file should have been modified, though
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(propertiesFile)) {
			props.load(in);
		}
		assertEquals(4, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("Gold Supporter", props.get("rank"));
		assertNotNull(props.get("joined"));
	}

	@Test
	public void stress_test() throws Exception {
		String player = profile.getPlayerName();

		when(scraper.scrapeProfile(eq(player), any(Document.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), isNull(Instant.class), any(HttpClient.class))).thenReturn(portrait);

		for (int i = 0; i < 100; i++) {
			profileImageLoader.getPortrait(player, label, 16, listener);
		}
		wait(profileImageLoader);

		verify(label, atLeastOnce()).setIcon(any(Icon.class));
		verify(listener, atLeastOnce()).onProfileDownloaded(profile);

		byte[] expected = portrait;
		byte[] actual = Files.readAllBytes(portraitFile);
		assertArrayEquals(expected, actual);

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(propertiesFile)) {
			props.load(in);
		}
		assertEquals(4, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("Gold Supporter", props.get("rank"));
		assertNotNull(props.get("joined"));
	}

	private static void wait(ProfileLoader profileImageLoader) throws InterruptedException {
		while (profileImageLoader.jobsBeingProcessed.get() > 0) {
			Thread.sleep(100);
		}
	}

	private ProfileLoader create(Path cacheDir, PlayerProfileScraper scraper) {
		ProfileLoader loader = new ProfileLoader(cacheDir, () -> {
			/*
			 * Create a mock client that just returns empty HTML pages. This has
			 * to be done, otherwise Jsoup.parse() will go into an infinite loop
			 * when a mocked InputStream is passed into it.
			 */
			CloseableHttpClient client = mock(CloseableHttpClient.class, withSettings().defaultAnswer(RETURNS_MOCKS));
			CloseableHttpResponse response = mock(CloseableHttpResponse.class, withSettings().defaultAnswer(RETURNS_MOCKS));
			HttpEntity entity = mock(HttpEntity.class, withSettings().defaultAnswer(RETURNS_MOCKS));
			InputStream in = new ByteArrayInputStream("<html />".getBytes());

			try {
				when(client.execute(any(HttpUriRequest.class))).thenReturn(response);
				when(response.getEntity()).thenReturn(entity);
				when(entity.getContent()).thenReturn(in);
			} catch (Exception ignore) {
			}

			return client;
		});
		loader.setProfilePageScraper(scraper);
		loader.start();
		return loader;
	}
}
