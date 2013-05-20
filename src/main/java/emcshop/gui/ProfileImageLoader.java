package emcshop.gui;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

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

import emcshop.gui.images.ImageManager;

/**
 * Used for downloading player profile pictures for the "players" view.
 * @author Michael Angstadt
 */
public class ProfileImageLoader {
	private static final Logger logger = Logger.getLogger(ProfileImageLoader.class.getName());
	private final File cacheDir;
	private final Set<String> downloaded = Collections.synchronizedSet(new HashSet<String>());
	private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<Job>();

	/**
	 * Creates a profile image loader with 4 threads.
	 * @param cacheDir the directory where the images are cached
	 */
	public ProfileImageLoader(File cacheDir) {
		this(cacheDir, 4);
	}

	/**
	 * Creates a profile image loader.
	 * @param cacheDir the directory where the images are cached
	 * @param numThreads the number of threads to add to the thread pool
	 */
	public ProfileImageLoader(File cacheDir, int numThreads) {
		this.cacheDir = cacheDir;

		//initialize the thread pool
		for (int i = 0; i < numThreads; i++) {
			LoadThread t = new LoadThread();
			t.setDaemon(true); //terminate the thread if the program is exited
			t.start();
		}
	}

	/**
	 * Loads a profile image, queuing it for download if necessary.
	 * @param playerName the player name
	 * @param label the label to insert the image into
	 * @param maxSize the size to scale the image to
	 */
	public void load(String playerName, JLabel label, int maxSize) {
		if (downloaded.contains(playerName.toLowerCase())) {
			//it was already downloaded, so read the image from the cache
			try {
				byte[] data = loadFromCache(playerName);
				ImageIcon image = (data == null) ? ImageManager.getUnknown() : new ImageIcon(data);
				image = scale(image, maxSize);
				label.setIcon(image);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Problem loading profile image from cache.", e);
			}
		} else {
			//queue the image for download
			Job job = new Job(playerName, label, maxSize);
			try {
				queue.put(job);
			} catch (InterruptedException e) {
				//should never be thrown because the queue doesn't have a max size
				logger.log(Level.SEVERE, "Queue's \"put\" operation was interrupted.", e);
			}
		}
	}

	/**
	 * Downloads the image and saves it to the cache.
	 * @param playerName the player name
	 * @return the image data or null the image could not be fetched (either
	 * from the Web or from the cache)
	 * @throws IOException
	 */
	private byte[] fetchImage(String playerName) throws IOException {
		DefaultHttpClient client = new DefaultHttpClient();

		//download image and save to cache
		HttpEntity entity = null;
		File cachedFile = getCacheFile(playerName);
		try {
			//get the URL of the user's profile image
			String imageUrl = scrapeProfileImageUrl(client, playerName);

			//return the cached image if the profile image URL can't be found
			if (imageUrl == null) {
				return loadFromCache(playerName);
			}

			HttpGet request = new HttpGet(imageUrl);
			if (cachedFile.exists()) {
				DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");
				df.setTimeZone(TimeZone.getTimeZone("GMT"));
				request.addHeader("If-Modified-Since", df.format(new Date(cachedFile.lastModified())) + " GMT");
			}

			HttpResponse response = client.execute(request);
			if (response.getStatusLine().getStatusCode() == 304) {
				//"not modified" response
				return loadFromCache(cachedFile);
			}

			entity = response.getEntity();
			byte data[] = IOUtils.toByteArray(entity.getContent());

			//save to cache
			//synchronize in-case two threads download the same profile image and try to save it at the same time 
			synchronized (this) {
				FileUtils.writeByteArrayToFile(cachedFile, data);
			}

			return data;
		} catch (IOException e) {
			logger.log(Level.WARNING, "Problem downloading profile image.  Attempting to retrieve from cache.", e);
			return loadFromCache(cachedFile);
		} finally {
			if (entity != null) {
				EntityUtils.consume(entity);
			}
		}
	}

	/**
	 * Loads a profile image from the cache.
	 * @param playerName the player name
	 * @return the image data or null if not found
	 * @throws IOException
	 */
	private byte[] loadFromCache(String playerName) throws IOException {
		File cacheLocation = getCacheFile(playerName);
		return loadFromCache(cacheLocation);
	}

	/**
	 * Loads a profile image from the cache.
	 * @param file the cached image
	 * @return the image data or null if the file doesn't exist
	 * @throws IOException
	 */
	private byte[] loadFromCache(File file) throws IOException {
		return file.exists() ? FileUtils.readFileToByteArray(file) : null;
	}

	/**
	 * Gets the path to a player's cached profile image.
	 * @param playerName the player name
	 * @return the path to the cached file (the file may or may not exist)
	 */
	private File getCacheFile(String playerName) {
		return new File(cacheDir, playerName.toLowerCase());
	}

	/**
	 * Scrapes a player's profile image URL from their profile page.
	 * @param client the HTTP client
	 * @param playerName the player name
	 * @return the URL or null if not found
	 * @throws IOException
	 */
	private String scrapeProfileImageUrl(DefaultHttpClient client, String playerName) throws IOException {
		//load the user's profile page
		Document profilePage;
		HttpEntity entity = null;
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

		//get the image URL
		Elements elements = profilePage.select(".avatarScaler img");
		if (!elements.isEmpty()) { //players can choose to make their profile private
			String src = elements.first().attr("src");
			if (!src.isEmpty()) {
				if (src.startsWith("http")) {
					return src;
				} else {
					return "http://empireminecraft.com/" + src;
				}
			}
		}
		return null;
	}

	/**
	 * Scales an image.
	 * @param image the image to scale
	 * @param maxSize the max size in pixels
	 * @return the scaled image
	 */
	private static ImageIcon scale(ImageIcon image, int maxSize) {
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

	/**
	 * Monitors the job queue for new images to download
	 */
	private class LoadThread extends Thread {
		@Override
		public void run() {
			while (true) {
				//get the next job
				Job job;
				try {
					job = queue.take();
				} catch (InterruptedException e1) {
					break;
				}

				byte[] data = null;
				try {
					data = fetchImage(job.playerName);
				} catch (IOException e) {
					//image could not be fetched, either from the Internet or from cache
				}

				downloaded.add(job.playerName.toLowerCase());

				ImageIcon image = (data == null) ? ImageManager.getUnknown() : new ImageIcon(data);
				image = scale(image, job.maxSize);
				job.label.setIcon(image);
			}
		}
	}

	/**
	 * Represents a queued download request for a profile image.
	 */
	private class Job {
		final String playerName;
		final JLabel label;
		final int maxSize;

		Job(String playerName, JLabel label, int maxSize) {
			this.playerName = playerName;
			this.label = label;
			this.maxSize = maxSize;
		}
	}
}
