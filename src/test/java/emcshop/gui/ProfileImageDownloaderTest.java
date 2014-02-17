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
import java.io.InputStream;

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
import emcshop.util.Settings;

public class ProfileImageDownloaderTest {
	private static final byte[] shavingfoamImage;
	static {
		try {
			shavingfoamImage = IOUtils.toByteArray(ProfileImageDownloaderTest.class.getResourceAsStream("shavingfoam.jpg"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final StatusLine OK = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "OK");
	private static final StatusLine NOT_MODIFIED = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_MODIFIED, "Not Modified");
	private static final StatusLine NOT_FOUND = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_NOT_FOUND, "Not Found");

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void non_existent_user() throws Throwable {
		Settings settings = mock(Settings.class);
		when(settings.getSession()).thenReturn(null);

		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);

		ProfileImageLoader profileImageLoader = new MockProfileImageLoader(temp.getRoot(), settings);
		profileImageLoader.load("does-not-exist", label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);
		assertFalse(new File(temp.getRoot(), "does-not-exist").exists());
	}

	@Test
	public void private_profile() throws Throwable {
		Settings settings = mock(Settings.class);
		when(settings.getSession()).thenReturn(null);

		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);

		ProfileImageLoader profileImageLoader = new MockProfileImageLoader(temp.getRoot(), settings);
		profileImageLoader.load("private", label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);
		assertFalse(new File(temp.getRoot(), "private").exists());
	}

	@Test
	public void image_downloaded() throws Throwable {
		Settings settings = mock(Settings.class);
		when(settings.getSession()).thenReturn(null);

		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);

		final String player = "shavingfoam";

		ProfileImageLoader profileImageLoader = new MockProfileImageLoader(temp.getRoot(), settings);
		profileImageLoader.load(player, label, 16, listener);
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
		profileImageLoader.load(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);
	}

	@Test
	public void image_not_modified() throws Throwable {
		File file = temp.newFile("shavingfoam");
		FileUtils.writeByteArrayToFile(file, shavingfoamImage);

		Settings settings = mock(Settings.class);
		when(settings.getSession()).thenReturn(null);

		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);

		final String player = "shavingfoam";

		ProfileImageLoader profileImageLoader = new MockProfileImageLoader(temp.getRoot(), settings, NOT_MODIFIED);
		profileImageLoader.load(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);

		byte[] expected = shavingfoamImage;
		byte[] actual = IOUtils.toByteArray(new FileInputStream(new File(temp.getRoot(), player)));
		assertArrayEquals(expected, actual);
	}

	@Test
	public void image_not_found() throws Throwable {
		Settings settings = mock(Settings.class);
		when(settings.getSession()).thenReturn(null);

		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);

		final String player = "shavingfoam";

		ProfileImageLoader profileImageLoader = new MockProfileImageLoader(temp.getRoot(), settings, NOT_FOUND);
		profileImageLoader.load(player, label, 16, listener);
		wait(profileImageLoader);

		verify(label).setIcon(Mockito.any(Icon.class));
		verify(listener, never()).onImageDownloaded(label);

		assertFalse(new File(temp.getRoot(), "shavingfoam").exists());
	}

	@Test
	public void stress_test() throws Throwable {
		Settings settings = mock(Settings.class);
		when(settings.getSession()).thenReturn(null);

		ImageDownloadedListener listener = mock(ImageDownloadedListener.class);
		JLabel label = mock(JLabel.class);
		final String player = "shavingfoam";

		ProfileImageLoader profileImageLoader = new MockProfileImageLoader(temp.getRoot(), settings);
		for (int i = 0; i < 100; i++) {
			profileImageLoader.load(player, label, 16, listener);
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
					if (uri.startsWith("http://u.emc.gs/")) {
						String playerName = uri.substring(uri.lastIndexOf('/') + 1);
						InputStream in = getClass().getResourceAsStream(playerName + "-profile.html");
						if (in == null) {
							fail("Unexpected player name, check the unit test: " + playerName);
						}

						status = OK;
						responseData = IOUtils.toByteArray(in);
					} else if (uri.equals("http://empireminecraft.com/data/avatars/l/12/12110.jpg?1389141773")) {
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

	private static class MockProfileImageLoader extends ProfileImageLoader {
		private final StatusLine imageResponseStatus;

		public MockProfileImageLoader(File cacheDir, Settings settings) {
			this(cacheDir, settings, OK);
		}

		public MockProfileImageLoader(File cacheDir, Settings settings, StatusLine imageResponseStatus) {
			super(cacheDir, settings);
			this.imageResponseStatus = imageResponseStatus;
		}

		@Override
		HttpClient createHttpClient() {
			return createMockClient(imageResponseStatus);
		}
	}
}
