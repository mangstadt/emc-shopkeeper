package emcshop.gui;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import emcshop.gui.images.ImageManager;
import emcshop.scraper.EmcSession;
import emcshop.util.CaseInsensitiveHashMap;
import emcshop.util.CaseInsensitiveHashSet;
import emcshop.util.Settings;

/**
 * Used for downloading player profile pictures.
 * @author Michael Angstadt
 */
public class ProfileImageLoader {
	private static final Logger logger = Logger.getLogger(ProfileImageLoader.class.getName());

	private final File cacheDir;
	private final Settings settings;
	private final Set<String> downloaded = new CaseInsensitiveHashSet();
	private final Map<String, List<Job>> waitList = new CaseInsensitiveHashMap<List<Job>>();
	private final LinkedBlockingQueue<String> downloadQueue = new LinkedBlockingQueue<String>();

	/**
	 * The number of items on the queue, plus the number of jobs currently being
	 * processed by the threads.
	 */
	volatile int jobsBeingProcessed = 0;

	/**
	 * Creates a profile image loader with 4 threads.
	 * @param cacheDir the directory where the images are cached
	 */
	public ProfileImageLoader(File cacheDir, Settings settings) {
		this(cacheDir, settings, 4);
	}

	/**
	 * Creates a profile image loader.
	 * @param cacheDir the directory where the images are cached
	 * @param numThreads the number of threads to add to the thread pool
	 */
	public ProfileImageLoader(File cacheDir, Settings settings, int numThreads) {
		this.cacheDir = cacheDir;
		this.settings = settings;

		//initialize the thread pool
		for (int i = 0; i < numThreads; i++) {
			LoadThread t = new LoadThread();
			t.setDaemon(true); //terminate the thread when the program exits
			t.setPriority(Thread.MIN_PRIORITY);
			t.setName(getClass().getSimpleName() + "-" + i);
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
		load(playerName, label, maxSize, null);
	}

	/**
	 * Loads a profile image, queuing it for download if necessary.
	 * @param playerName the player name
	 * @param label the label to insert the image into
	 * @param maxSize the size to scale the image to
	 * @param listener invoked when the image has been assigned to the label
	 */
	public void load(String playerName, JLabel label, int maxSize, ImageDownloadedListener listener) {
		//attempt to load the image from the cache
		byte data[] = null;
		try {
			data = loadFromCache(playerName);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem loading profile image from cache.", e);
		}

		//assign an image to the label
		ImageIcon image = (data == null) ? ImageManager.getUnknown() : new ImageIcon(data);
		image = ImageManager.scale(image, maxSize);
		label.setIcon(image);

		synchronized (downloaded) {
			if (downloaded.contains(playerName)) {
				//the image has already been downloaded, so the cached version is the most up-to-date version of the image
				return;
			}

			Job job = new Job(playerName, label, maxSize, listener);

			//see if the image is already queued for download
			List<Job> waiting = waitList.get(playerName);
			if (waiting == null) {
				//player name is not queued for download, so add it to the queue
				try {
					jobsBeingProcessed++;
					downloadQueue.put(job.playerName);
				} catch (InterruptedException e) {
					//should never be thrown because the queue doesn't have a max size
					logger.log(Level.SEVERE, "Queue's \"put\" operation was interrupted.", e);
				}

				waiting = new ArrayList<Job>();
				waitList.put(playerName, waiting);
			}

			//add job to wait list
			waiting.add(job);
		}
	}

	HttpClient createHttpClient() {
		EmcSession session = settings.getSession();
		return (session == null) ? new DefaultHttpClient() : session.createHttpClient();
	}

	/**
	 * Downloads the image and saves it to the cache.
	 * @param playerName the player name
	 * @return the image data, null if the image hasn't changed, or null the
	 * image could not be fetched
	 * @throws IOException
	 */
	private byte[] downloadImage(String playerName) throws IOException {
		HttpClient client = createHttpClient();

		//get the URL of the user's profile image
		String imageUrl = scrapeProfileImageUrl(client, playerName);
		if (imageUrl == null) {
			//no image found
			return null;
		}

		//download image
		HttpEntity entity = null;
		File cachedFile = getCacheFile(playerName);
		byte[] data;
		try {
			HttpGet request = new HttpGet(imageUrl);
			if (cachedFile.exists()) {
				DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
				df.setTimeZone(TimeZone.getTimeZone("GMT"));
				Date date = new Date(cachedFile.lastModified());

				request.addHeader("If-Modified-Since", df.format(date));
			}

			HttpResponse response = client.execute(request);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_NOT_FOUND) {
				//image not modified or not found
				return null;
			}

			entity = response.getEntity();
			data = EntityUtils.toByteArray(entity);
		} finally {
			if (entity != null) {
				EntityUtils.consumeQuietly(entity);
			}
		}

		//save to cache
		try {
			FileUtils.writeByteArrayToFile(cachedFile, data);
		} catch (IOException e) {
			logger.log(Level.WARNING, "Problem saving image to cache.", e);
		}

		return data;
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
	private String scrapeProfileImageUrl(HttpClient client, String playerName) throws IOException {
		//load the user's profile page
		Document profilePage;
		HttpGet request = new HttpGet("http://u.emc.gs/" + playerName);
		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		try {
			profilePage = Jsoup.parse(entity.getContent(), "UTF-8", "");
		} finally {
			EntityUtils.consume(entity);
		}

		Elements elements = profilePage.select(".avatarScaler img");
		if (elements.isEmpty()) { //players can choose to make their profile private
			return null;
		}

		String src = elements.first().attr("src");
		if (src.isEmpty()) {
			return null;
		}

		if (!src.startsWith("http")) {
			src = "http://empireminecraft.com/" + src;
		}
		return src;
	}

	/**
	 * Monitors the job queue for new images to download
	 */
	private class LoadThread extends Thread {
		@Override
		public void run() {
			boolean first = true;
			while (true) {
				if (first) {
					first = false;
				} else {
					jobsBeingProcessed--;
				}

				//get the next player name
				String playerName;
				try {
					playerName = downloadQueue.take();
				} catch (InterruptedException e) {
					break;
				}

				//download the image
				byte[] data = null;
				try {
					data = downloadImage(playerName);
				} catch (IOException e) {
					logger.log(Level.WARNING, "Problem downloading profile image.", e);
				}

				List<Job> waiting;
				synchronized (downloaded) {
					downloaded.add(playerName);

					waiting = waitList.get(playerName);
					if (waiting == null) {
						//there are no labels waiting to be updated
						//should never happen, there should always be at least 1 label waiting to be updated
						continue;
					}
				}

				if (data == null) {
					//the image hasn't changed (or there was an error downloading the image), so the labels don't need to be updated
					waitList.remove(playerName);
					continue;
				}

				//update all the labels with the new image
				ImageIcon image = new ImageIcon(data);
				for (Job job : waiting) {
					ImageIcon scaledImage = ImageManager.scale(image, job.maxSize);
					job.label.setIcon(scaledImage);
					if (job.listener != null) {
						job.listener.onImageDownloaded(job.label);
					}
				}
				waitList.remove(playerName);
			}
		}
	}

	/**
	 * Represents a queued download request for a profile image.
	 */
	private static class Job {
		private final String playerName;
		private final JLabel label;
		private final int maxSize;
		private final ImageDownloadedListener listener;

		private Job(String playerName, JLabel label, int maxSize, ImageDownloadedListener listener) {
			this.playerName = playerName;
			this.label = label;
			this.maxSize = maxSize;
			this.listener = listener;
		}
	}

	public interface ImageDownloadedListener {
		/**
		 * Called when a new, downloaded image is assigned to the label.
		 * @param label the label
		 */
		void onImageDownloaded(JLabel label);
	}
}
