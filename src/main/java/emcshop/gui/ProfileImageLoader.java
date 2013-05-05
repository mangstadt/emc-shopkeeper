package emcshop.gui;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 * Used for downloading player profile pictures for the "players" view.
 * @author Michael Angstadt
 */
public class ProfileImageLoader {
	private static final Logger logger = Logger.getLogger(ProfileImageLoader.class.getName());
	private final File cacheDir;
	private final int maxSize;
	private final Set<String> downloaded = new HashSet<String>();
	private final List<LoadThread> threads = new ArrayList<LoadThread>();

	/**
	 * @param cacheDir the directory where the images are cached
	 * @param maxSize the size that the images should be scaled to when
	 * displayed
	 */
	public ProfileImageLoader(File cacheDir, int maxSize) {
		this.cacheDir = cacheDir;
		this.maxSize = maxSize;
	}

	/**
	 * Loads a collection of profile images.
	 * @param imageLabels the images to load (key = player name, value = the
	 * label to insert the image into)
	 */
	public void load(Map<String, JLabel> imageLabels) {
		int numThreads = 4;
		Iterator<Map.Entry<String, JLabel>> it = imageLabels.entrySet().iterator();
		threads.clear();
		for (int i = 0; i < numThreads; i++) {
			LoadThread t = new LoadThread(it);
			threads.add(t);
			t.start();
		}
	}

	/**
	 * Cancels the current download operation.
	 */
	public void cancel() {
		for (LoadThread t : threads) {
			t.interrupt();
		}
	}

	private class LoadThread extends Thread {
		private final Iterator<Map.Entry<String, JLabel>> imageLabels;

		public LoadThread(Iterator<Map.Entry<String, JLabel>> imageLabels) {
			this.imageLabels = imageLabels;
		}

		private Map.Entry<String, JLabel> nextImage() {
			synchronized (imageLabels) {
				return imageLabels.hasNext() ? imageLabels.next() : null;
			}
		}

		@Override
		public void run() {
			Map.Entry<String, JLabel> entry;
			while (!Thread.interrupted() && (entry = nextImage()) != null) {
				String playerName = entry.getKey();
				JLabel label = entry.getValue();

				try {
					ImageIcon image = getProfileImage(playerName);
					if (image != null) {
						image = scale(image);
						if (!Thread.interrupted()) {
							label.setIcon(image);
							label.setHorizontalAlignment(SwingConstants.CENTER);
							label.setVerticalAlignment(SwingConstants.TOP);
							label.validate();
						}
					}
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Problem loading profile image.", e);
				}
			}
		}
	}

	/**
	 * Gets the profile image for a player.
	 * @param playerName the player
	 * @return the profile image or null if it couldn't be found (i.e. the
	 * player's profile is not public)
	 * @throws IOException
	 */
	public ImageIcon getProfileImage(String playerName) throws IOException {
		File cacheLocaton = new File(cacheDir, playerName);
		byte data[];
		if (downloaded.contains(playerName.toLowerCase())) {
			//we already checked for an updated version of the image, so read from cache and don't check again
			data = cacheLocaton.exists() ? FileUtils.readFileToByteArray(cacheLocaton) : null;
		} else {
			//download/update the image
			data = download(playerName, cacheLocaton);
			downloaded.add(playerName.toLowerCase());
		}
		return (data == null) ? null : new ImageIcon(data);
	}

	/**
	 * Downloads the profile image of a player.
	 * @param playerName the player name
	 * @param cachedFile the location of the cached image
	 * @return the image data
	 * @throws IOException
	 */
	private byte[] download(String playerName, File cachedFile) throws IOException {
		DefaultHttpClient client = new DefaultHttpClient();
		HttpEntity entity = null;
		Document profilePage;
		try {
			HttpPost request = new HttpPost("http://empireminecraft.com/members/");
			List<BasicNameValuePair> parameters = Arrays.asList(new BasicNameValuePair("username", playerName));
			request.setEntity(new UrlEncodedFormEntity(parameters));

			HttpResponse response = client.execute(request);
			entity = response.getEntity();
			profilePage = Jsoup.parse(entity.getContent(), "UTF-8", "");
		} finally {
			if (entity != null) {
				EntityUtils.consume(entity);
			}
		}
		entity = null;

		String imageUrl = null;
		Elements elements = profilePage.select(".avatarScaler img");
		if (!elements.isEmpty()) { //players can choose to make their profile private
			String src = elements.first().attr("src");
			if (!src.isEmpty()) {
				imageUrl = "http://empireminecraft.com/" + src;
			}
		}
		if (imageUrl == null) {
			return cachedFile.exists() ? FileUtils.readFileToByteArray(cachedFile) : null;
		}

		try {
			HttpGet request = new HttpGet(imageUrl);
			if (cachedFile.exists()) {
				DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");
				df.setTimeZone(TimeZone.getTimeZone("GMT"));
				request.addHeader("If-Modified-Since", df.format(new Date(cachedFile.lastModified())) + " GMT");
			}

			HttpResponse response = client.execute(request);
			if (response.getStatusLine().getStatusCode() == 304) {
				//"not modified" response
				return FileUtils.readFileToByteArray(cachedFile);
			}
			byte data[] = IOUtils.toByteArray(response.getEntity().getContent());

			//save to cache
			FileUtils.writeByteArrayToFile(cachedFile, data);

			return data;
		} finally {
			if (entity != null) {
				EntityUtils.consume(entity);
			}
		}
	}

	/**
	 * Scales an image.
	 * @param image the image to scale
	 * @return the scaled image
	 */
	private ImageIcon scale(ImageIcon image) {
		int height = image.getIconHeight();
		int width = image.getIconWidth();

		if (height <= maxSize && width <= maxSize) {
			return image;
		}

		int scaledHeight, scaledWidth;
		if (height > width) {
			double ratio = (double) maxSize / height;
			scaledHeight = maxSize;
			scaledWidth = (int) (width * ratio);
		} else {
			double ratio = (double) maxSize / width;
			scaledWidth = maxSize;
			scaledHeight = (int) (height * ratio);
		}

		return new ImageIcon(image.getImage().getScaledInstance(scaledWidth, scaledHeight, java.awt.Image.SCALE_SMOOTH));
	}
}
