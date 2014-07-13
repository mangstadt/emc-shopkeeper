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

import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;
import emcshop.scraper.Rank;
import emcshop.util.HttpClientFactory;

public class ProfileLoaderTest {
	private final byte[] portrait;
	{
		try {
			portrait = IOUtils.toByteArray(ProfileLoaderTest.class.getResourceAsStream("shavingfoam.jpg"));
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
	private ProfileDownloadedListener listener;
	private JLabel label;
	private PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
	private ProfileLoader profileImageLoader;

	@Rule
	public final TemporaryFolder temp = new TemporaryFolder();

	@Before
	public void before() {
		portraitFile = new File(temp.getRoot(), profile.getPlayerName());
		propertiesFile = new File(temp.getRoot(), profile.getPlayerName() + ".properties");

		listener = mock(ProfileDownloadedListener.class);
		label = mock(JLabel.class);
		scraper = mock(PlayerProfileScraper.class);
		profileImageLoader = create(temp.getRoot(), scraper);
	}

	@Test
	public void non_existent_user() throws Throwable {
		String player = profile.getPlayerName();

		when(scraper.downloadProfile(eq(player), any(HttpClient.class))).thenReturn(null);

		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener, never()).onProfileDownloaded(any(PlayerProfile.class));
		assertFalse(portraitFile.exists());
		assertFalse(propertiesFile.exists());
	}

	@Test
	public void private_profile() throws Throwable {
		String player = profile.getPlayerName();

		PlayerProfile profile = new PlayerProfile();
		profile.setPlayerName(player);
		profile.setPrivate(true);
		when(scraper.downloadProfile(eq(player), any(HttpClient.class))).thenReturn(profile);

		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener).onProfileDownloaded(profile);
		assertFalse(portraitFile.exists());

		Properties props = new Properties();
		props.load(new FileInputStream(propertiesFile));
		assertEquals(2, props.size());
		assertEquals("true", props.get("private"));
	}

	@Test
	public void image_downloaded() throws Throwable {
		String player = profile.getPlayerName();

		when(scraper.downloadProfile(eq(player), any(HttpClient.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), isNull(Date.class), any(HttpClient.class))).thenReturn(portrait);

		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener).onProfileDownloaded(profile);

		byte[] expected = portrait;
		byte[] actual = IOUtils.toByteArray(new FileInputStream(portraitFile));
		assertArrayEquals(expected, actual);

		Properties props = new Properties();
		props.load(new FileInputStream(propertiesFile));
		assertEquals(4, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("gold", props.get("rank"));
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
		verify(scraper).downloadPortrait(any(PlayerProfile.class), any(Date.class), any(HttpClient.class));
	}

	@Test
	public void image_not_modified() throws Throwable {
		String player = profile.getPlayerName();
		temp.newFile(player);
		temp.newFile(player + ".properties");
		Date lastModified = new Date();

		when(scraper.downloadProfile(eq(player), any(HttpClient.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), eq(lastModified), any(HttpClient.class))).thenReturn(null);

		profileImageLoader.getPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(any(Icon.class));
		verify(listener).onProfileDownloaded(profile);

		assertEquals(0, portraitFile.length()); //file should not have been modified

		//properties file should have been modified, though
		Properties props = new Properties();
		props.load(new FileInputStream(propertiesFile));
		assertEquals(4, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("gold", props.get("rank"));
		assertNotNull(props.get("joined"));
	}

	@Test
	public void stress_test() throws Throwable {
		String player = profile.getPlayerName();

		when(scraper.downloadProfile(eq(player), any(HttpClient.class))).thenReturn(profile);
		when(scraper.downloadPortrait(eq(profile), isNull(Date.class), any(HttpClient.class))).thenReturn(portrait);

		for (int i = 0; i < 100; i++) {
			profileImageLoader.getPortrait(player, label, 16, listener);
		}
		wait(profileImageLoader);

		verify(label, atLeastOnce()).setIcon(any(Icon.class));
		verify(listener, atLeastOnce()).onProfileDownloaded(profile);

		byte[] expected = portrait;
		byte[] actual = IOUtils.toByteArray(new FileInputStream(portraitFile));
		assertArrayEquals(expected, actual);

		Properties props = new Properties();
		props.load(new FileInputStream(propertiesFile));
		assertEquals(4, props.size());
		assertEquals("false", props.get("private"));
		assertEquals("gold", props.get("rank"));
		assertNotNull(props.get("joined"));
	}

	private static void wait(ProfileLoader profileImageLoader) throws InterruptedException {
		while (profileImageLoader.jobsBeingProcessed > 0) {
			Thread.sleep(100);
		}
	}

	private ProfileLoader create(File cacheDir, PlayerProfileScraper scraper) {
		ProfileLoader loader = new ProfileLoader(cacheDir);
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
