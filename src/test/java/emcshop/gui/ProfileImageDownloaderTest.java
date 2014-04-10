package emcshop.gui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.JLabel;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicStatusLine;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import emcshop.gui.ProfileImageLoader.ImageDownloadedListener;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;
import emcshop.util.HttpClientFactory;

public class ProfileImageDownloaderTest {
	private static final byte[] shavingfoamImage;
	static {
		try {
			shavingfoamImage = IOUtils.toByteArray(ProfileImageDownloaderTest.class.getResourceAsStream("shavingfoam.jpg"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	private static final String shavingfoamImageUrl = "http://empireminecraft.com/data/avatars/l/12/12110.jpg?1389141773";

	private static final StatusLine OK = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "OK");
	private static final StatusLine NOT_MODIFIED = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_MODIFIED, "Not Modified");
	private static final StatusLine NOT_FOUND = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_FOUND, "Not Found");

	@Rule
	public final TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void non_existent_user_or_private_profile() throws Throwable {
		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = "does-not-exist";

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		PlayerProfile profile = new PlayerProfile();
		profile.setPlayerName(player);
		when(scraper.scrapeProfile(Mockito.eq(player), Mockito.any(HttpClient.class))).thenReturn(profile);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);
		assertFalse(new File(temp.getRoot(), "does-not-exist").exists());
	}

	@Test
	public void image_downloaded() throws Throwable {
		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = "shavingfoam";

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		PlayerProfile profile = new PlayerProfile();
		profile.setPlayerName(player);
		profile.setPortraitUrl(shavingfoamImageUrl);
		when(scraper.scrapeProfile(Mockito.eq(player), Mockito.any(HttpClient.class))).thenReturn(profile);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label, times(2)).setIcon(Mockito.any(Icon.class));
		verify(listener).onImageDownloaded(label);

		byte[] expected = shavingfoamImage;
		byte[] actual = IOUtils.toByteArray(new FileInputStream(new File(temp.getRoot(), player)));
		assertArrayEquals(expected, actual);

		//loading it again should retrieve it from the cache
		//no HTTP calls should be made

		listener = mock(ImageDownloadedListener.class);
		label = mock(JLabel.class);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);
	}

	@Test
	public void image_not_modified() throws Throwable {
		File file = temp.newFile("shavingfoam");
		FileUtils.writeByteArrayToFile(file, shavingfoamImage);

		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = "shavingfoam";

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		PlayerProfile profile = new PlayerProfile();
		profile.setPlayerName(player);
		profile.setPortraitUrl(shavingfoamImageUrl);
		when(scraper.scrapeProfile(Mockito.eq(player), Mockito.any(HttpClient.class))).thenReturn(profile);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper, NOT_MODIFIED);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);

		byte[] expected = shavingfoamImage;
		byte[] actual = IOUtils.toByteArray(new FileInputStream(new File(temp.getRoot(), player)));
		assertArrayEquals(expected, actual);
	}

	@Test
	public void image_not_found() throws Throwable {
		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = "shavingfoam";

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		PlayerProfile profile = new PlayerProfile();
		profile.setPlayerName(player);
		profile.setPortraitUrl(shavingfoamImageUrl);
		when(scraper.scrapeProfile(Mockito.eq(player), Mockito.any(HttpClient.class))).thenReturn(profile);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper, NOT_FOUND);
		profileImageLoader.loadPortrait(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);

		assertFalse(new File(temp.getRoot(), "shavingfoam").exists());
	}

	@Test
	public void stress_test() throws Throwable {
		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		String player = "shavingfoam";

		PlayerProfileScraper scraper = mock(PlayerProfileScraper.class);
		PlayerProfile profile = new PlayerProfile();
		profile.setPlayerName(player);
		profile.setPortraitUrl(shavingfoamImageUrl);
		when(scraper.scrapeProfile(Mockito.eq(player), Mockito.any(HttpClient.class))).thenReturn(profile);

		ProfileImageLoader profileImageLoader = create(temp.getRoot(), scraper);
		for (int i = 0; i < 100; i++) {
			profileImageLoader.loadPortrait(player, label, 16, listener);
		}
		wait(profileImageLoader);

		verify(label, atLeastOnce()).setIcon(Mockito.any(Icon.class));
		verify(listener, atLeastOnce()).onImageDownloaded(label);

		byte[] expected = shavingfoamImage;
		byte[] actual = IOUtils.toByteArray(new FileInputStream(new File(temp.getRoot(), player)));
		assertArrayEquals(expected, actual);
	}

	private static void wait(ProfileImageLoader profileImageLoader) throws InterruptedException {
		while (profileImageLoader.jobsBeingProcessed > 0) {
			Thread.sleep(100);
		}
	}

	private static HttpClient createMockClient(final StatusLine imageResponseStatus) {
		try {
			HttpClient client = mock(HttpClient.class);
			when(client.execute(Mockito.any(HttpGet.class))).then(new Answer<HttpResponse>() {
				@Override
				public HttpResponse answer(InvocationOnMock invocation) throws Throwable {
					HttpGet request = (HttpGet) invocation.getArguments()[0];
					String uri = request.getURI().toString();

					byte[] responseData = null;
					StatusLine status = null;
					if (uri.equals(shavingfoamImageUrl)) {
						status = imageResponseStatus;
						responseData = shavingfoamImage;
					} else {
						fail("Unexpected URI, check the unit test: " + uri);
					}

					HttpResponse response = mock(HttpResponse.class);
					when(response.getStatusLine()).thenReturn(status);

					HttpEntity entity = mock(HttpEntity.class);
					when(entity.getContent()).thenReturn(new ByteArrayInputStream(responseData));
					when(entity.getContentLength()).thenReturn((long) responseData.length);
					when(response.getEntity()).thenReturn(entity);

					return response;
				}
			});
			return client;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private ProfileImageLoader create(File cacheDir, PlayerProfileScraper scraper) {
		return create(cacheDir, scraper, OK);
	}

	private ProfileImageLoader create(File cacheDir, PlayerProfileScraper scraper, final StatusLine imageResponseStatus) {
		ProfileImageLoader loader = new ProfileImageLoader(cacheDir);
		loader.setHttpClientFactory(new HttpClientFactory() {
			@Override
			public HttpClient create() {
				return createMockClient(imageResponseStatus);
			}
		});
		loader.setProfilePageScraper(scraper);
		loader.start();
		return loader;
	}
}
