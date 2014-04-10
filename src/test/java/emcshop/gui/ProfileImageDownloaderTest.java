package emcshop.gui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.JLabel;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import emcshop.gui.ProfileImageLoader.ImageDownloadedListener;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;
import emcshop.scraper.Rank;
import emcshop.util.HttpClientFactory;

public class ProfileImageDownloaderTest {
	private final byte[] portrait;
	{
		try {
			portrait = IOUtils.toByteArray(ProfileImageDownloaderTest.class.getResourceAsStream("shavingfoam.jpg"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private final PlayerProfile profile = new PlayerProfile();
	{
		profile.setPlayerName("shavingfoam");
		profile.setPrivate(false);
		profile.setRank(Rank.GOLD);
		profile.setPortraitUrl("http://empireminecraft.com/data/avatars/l/12/12110.jpg?1389141773");
		profile.setJoined(new Date());
	}

	private File portraitFile, propertiesFile;

	@Rule
	public final TemporaryFolder temp = new TemporaryFolder();

	@Before
	public void before() {
		portraitFile = new File(temp.getRoot(), profile.getPlayerName());
		propertiesFile = new File(temp.getRoot(), profile.getPlayerName() + ".properties");
	}

	@Test
	public void non_existent_user() throws Throwable {
		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = profile.getPlayerName();

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		when(scraper.scrapeProfile(eq(player), any(HttpClient.class))).thenReturn(null);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);
		assertFalse(portraitFile.exists());
		assertFalse(propertiesFile.exists());
	}

	@Test
	public void private_profile() throws Throwable {
		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = profile.getPlayerName();

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		PlayerProfile profile = new PlayerProfile();
		profile.setPlayerName(player);
		profile.setPrivate(true);
		when(scraper.scrapeProfile(eq(player), any(HttpClient.class))).thenReturn(profile);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);
		assertFalse(portraitFile.exists());

		Properties props = new Properties();
		props.load(new FileInputStream(propertiesFile));
		assertEquals(1, props.size());
		assertEquals("true", props.get("private"));
	}

	@Test
	public void image_downloaded() throws Throwable {
		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = profile.getPlayerName();

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		when(scraper.scrapeProfile(eq(player), any(HttpClient.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), isNull(Date.class), any(HttpClient.class))).thenReturn(portrait);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label, times(2)).setIcon(any(Icon.class));
		verify(listener).onImageDownloaded(label);

		byte[] expected = portrait;
		byte[] actual = IOUtils.toByteArray(new FileInputStream(portraitFile));
		assertArrayEquals(expected, actual);

		Properties props = new Properties();
		props.load(new FileInputStream(propertiesFile));
		assertEquals(3, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("gold", props.get("rank"));
		assertNotNull(props.get("joined"));

		//loading it again should retrieve it from the cache
		//no HTTP calls should be made

		listener = mock(ImageDownloadedListener.class);
		label = mock(JLabel.class);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);

		//the portrait should have only been downloaded once
		verify(scraper).downloadPortrait(any(PlayerProfile.class), any(Date.class), any(HttpClient.class));
	}

	@Test
	public void image_not_modified() throws Throwable {
		String player = profile.getPlayerName();
		temp.newFile(player);
		temp.newFile(player + ".properties");
		Date lastModified = new Date();

		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		when(scraper.scrapeProfile(eq(player), any(HttpClient.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), eq(lastModified), any(HttpClient.class))).thenReturn(null);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);

		assertEquals(0, portraitFile.length()); //file should not have been modified

		//properties file should have been modified, though
		Properties props = new Properties();
		props.load(new FileInputStream(propertiesFile));
		assertEquals(3, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("gold", props.get("rank"));
		assertNotNull(props.get("joined"));
	}

	@Test
	public void stress_test() throws Throwable {
		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = profile.getPlayerName();

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		when(scraper.scrapeProfile(eq(player), any(HttpClient.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), isNull(Date.class), any(HttpClient.class))).thenReturn(portrait);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper);
		for (int i = 0; i < 100; i++) {
			profileImageLoader.loadPortrait(player, label, 16, listener);
		}
		wait(profileImageLoader);

		verify(label, atLeastOnce()).setIcon(any(Icon.class));
		verify(listener, atLeastOnce()).onImageDownloaded(label);

		byte[] expected = portrait;
		byte[] actual = IOUtils.toByteArray(new FileInputStream(portraitFile));
		assertArrayEquals(expected, actual);

		Properties props = new Properties();
		props.load(new FileInputStream(propertiesFile));
		assertEquals(3, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("gold", props.get("rank"));
		assertNotNull(props.get("joined"));
	}

	private static void wait(ProfileImageLoader profileImageLoader) throws InterruptedException {
		while (profileImageLoader.jobsBeingProcessed > 0) {
			Thread.sleep(100);
		}
	}

	private ProfileImageLoader create(File cacheDir, PlayerProfileScraper scraper) {
		ProfileImageLoader loader = new ProfileImageLoader(cacheDir);
		loader.setHttpClientFactory(new HttpClientFactory() {
			@Override
			public HttpClient create() {
				return mock(HttpClient.class);
			}
		});
		loader.setProfilePageScraper(scraper);
		loader.start();
		return loader;
	}
}
