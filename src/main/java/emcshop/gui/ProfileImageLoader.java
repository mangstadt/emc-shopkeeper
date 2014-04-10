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

import emcshop.gui.images.ImageManager;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;
import emcshop.util.CaseInsensitiveHashMap;
import emcshop.util.CaseInsensitiveHashSet;
import emcshop.util.HttpClientFactory;
import emcshop.util.PropertiesWrapper;

/**
 * Used for downloading player profile pictures.
 * @author Michael Angstadt
 */
public class ProfileImageLoader {
	private static final Logger logger = Logger.getLogger(ProfileImageLoader.class.getName());

	private final File cacheDir;
	private final Set<String> downloaded = new CaseInsensitiveHashSet();
	private final Map<String, List<Job>> waitList = new CaseInsensitiveHashMap<List<Job>>();
	private final LinkedBlockingQueue<String> downloadQueue = new LinkedBlockingQueue<String>();

	private int threads = 4;
	private HttpClientFactory clientFactory = new HttpClientFactory() {
		@Override
		public HttpClient create() {
			return new DefaultHttpClient();
		}
	};
	private PlayerProfileScraper scraper = new PlayerProfileScraper();

	/**
	 * The number of items on the queue, plus the number of jobs currently being
	 * processed by the threads (for unit testing purposes).
	 */
	volatile int jobsBeingProcessed = 0;

	/**
	 * Creates a profile image loader.
	 * @param cacheDir the directory where the images are cached
	 */
	public ProfileImageLoader(File cacheDir) {
		this.cacheDir = cacheDir;
	}

	public int getThreads() {
		return threads;
	}

	public void setThreads(int threads) {
		this.threads = threads;
	}

	public HttpClientFactory getHttpClientFactory() {
		return clientFactory;
	}

	public void setHttpClientFactory(HttpClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	public PlayerProfileScraper getProfilePageScraper() {
		return scraper;
	}

	public void setProfilePageScraper(PlayerProfileScraper scraper) {
		this.scraper = scraper;
	}

	/**
	 * Starts the downloader threads.
	 */
	public void start() {
		for (int i = 0; i < threads; i++) {
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
	public void loadPortrait(String playerName, JLabel label, int maxSize) {
		loadPortrait(playerName, label, maxSize, null);
	}

	/**
	 * Loads a profile image, queuing it for download if necessary.
	 * @param playerName the player name
	 * @param label the label to insert the image into
	 * @param maxSize the size to scale the image to
	 * @param listener invoked when the image has been assigned to the label
	 */
	public void loadPortrait(String playerName, JLabel label, int maxSize, ImageDownloadedListener listener) {
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

	/**
	 * Saves a scraped profile page to a properties file.
	 * @param profile the scraped data
	 * @throws IOException
	 */
	private void saveProfileData(PlayerProfile profile) throws IOException {
		PropertiesWrapper props = new PropertiesWrapper();
		props.set("rank", profile.getRank());
		props.setDate("joined", profile.getJoined());

		File file = getPropertiesFile(profile.getPlayerName());
		props.store(file, "");
	}

	/**
	 * Loads a profile image from the cache.
	 * @param playerName the player name
	 * @return the image data or null if not found
	 * @throws IOException
	 */
	private byte[] loadFromCache(String playerName) throws IOException {
		File cacheLocation = getPortraitFile(playerName);
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
	 * @return the file path
	 */
	private File getPortraitFile(String playerName) {
		return new File(cacheDir, playerName.toLowerCase());
	}

	/**
	 * Gets the path to a player's properties file
	 * @param playerName the player name
	 * @return the file path
	 */
	private File getPropertiesFile(String playerName) {
		return new File(cacheDir, playerName.toLowerCase() + ".properties");
	}

	/**
	 * Monitors the job queue for new images to download
	 */
	private class LoadThread extends Thread {
		private final HttpClient client = clientFactory.create();
		private final DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
		{
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
		}

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

				//scrape the profile page
				PlayerProfile profile = null;
				try {
					profile = scraper.scrapeProfile(playerName, client);
				} catch (IOException e) {
					logger.log(Level.WARNING, "Problem downloading player profile page.", e);
				}

				byte[] data = null;
				if (profile != null) {
					//save profile data
					try {
						saveProfileData(profile);
					} catch (IOException e) {
						logger.log(Level.WARNING, "Problem save profile data.", e);
					}

					//download the image
					String portraitUrl = profile.getPortraitUrl();
					if (portraitUrl != null) {
						try {
							data = downloadAndSaveImage(profile);
						} catch (IOException e) {
							logger.log(Level.WARNING, "Problem downloading profile image.", e);
						}
					}
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

				//update all the labels with the new image
				if (data != null) {
					ImageIcon image = new ImageIcon(data);
					for (Job job : waiting) {
						ImageIcon scaledImage = ImageManager.scale(image, job.maxSize);
						job.label.setIcon(scaledImage);
						if (job.listener != null) {
							job.listener.onImageDownloaded(job.label);
						}
					}
				}

				waitList.remove(playerName);
			}
		}

		/**
		 * Downloads the image and saves it to the cache.
		 * @param playerName the player name
		 * @return the image data, null if the image hasn't changed, or null the
		 * image could not be fetched
		 * @throws IOException
		 */
		private byte[] downloadAndSaveImage(PlayerProfile profile) throws IOException {
			//download image
			HttpEntity entity = null;
			File cachedFile = getPortraitFile(profile.getPlayerName());
			byte[] data;
			try {
				HttpGet request = new HttpGet(profile.getPortraitUrl());
				if (cachedFile.exists()) {
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
