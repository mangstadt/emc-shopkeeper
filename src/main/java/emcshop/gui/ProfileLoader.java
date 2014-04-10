package emcshop.gui;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import emcshop.gui.images.ImageManager;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;
import emcshop.scraper.Rank;
import emcshop.util.CaseInsensitiveHashMap;
import emcshop.util.CaseInsensitiveHashSet;
import emcshop.util.HttpClientFactory;
import emcshop.util.PropertiesWrapper;

/**
 * Used for downloading player profile pictures.
 * @author Michael Angstadt
 */
public class ProfileLoader {
	private static final Logger logger = Logger.getLogger(ProfileLoader.class.getName());

	private final Map<Rank, Color> rankToColor = new EnumMap<Rank, Color>(Rank.class);
	{
		rankToColor.put(Rank.IRON, new Color(128, 128, 128));
		rankToColor.put(Rank.GOLD, new Color(181, 181, 0));
		rankToColor.put(Rank.DIAMOND, new Color(0, 181, 194));
		rankToColor.put(Rank.MODERATOR, new Color(0, 64, 0));
		rankToColor.put(Rank.SENIOR_STAFF, new Color(0, 255, 0));
		rankToColor.put(Rank.DEVELOPER, new Color(0, 0, 128));
		rankToColor.put(Rank.ADMIN, new Color(209, 0, 195));
	}
	private final Color noRankColor = Color.black;

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
	public ProfileLoader(File cacheDir) {
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

		Job job = new PortraitJob(playerName, label, maxSize, listener);
		queueJob(job);
	}

	/**
	 * Colors a label depending on the given player's rank.
	 * @param playerName the player name
	 * @param label the label to change the color of
	 * @param listener invoked when the profile page has been scraped
	 */
	public void loadRank(String playerName, JLabel label, ImageDownloadedListener listener) {
		Color color = getRankColor(playerName);
		label.setForeground(color);

		Job job = new RankJob(playerName, label, listener);
		queueJob(job);
	}

	/**
	 * Gets the color of the player's rank.
	 * @param playerName the player name
	 * @return the color
	 */
	public Color getRankColor(String playerName) {
		File file = getPropertiesFile(playerName);
		if (!file.exists()) {
			return noRankColor;
		}

		PlayerProfileProperties props;
		try {
			props = new PlayerProfileProperties(file);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem loading profile properties from cache.", e);
			return noRankColor;
		}

		Rank rank = props.getRank();
		Color color = rankToColor.get(rank);
		return (color == null) ? noRankColor : color;
	}

	private void queueJob(Job job) {
		synchronized (downloaded) {
			if (downloaded.contains(job.playerName)) {
				//the image has already been downloaded, so the cached version is the most up-to-date version of the image
				return;
			}

			//see if the image is already queued for download
			List<Job> waiting = waitList.get(job.playerName);
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
				waitList.put(job.playerName, waiting);
			}

			//add job to wait list
			waiting.add(job);
		}
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
				HttpClient client = clientFactory.create(); //create a new client each time so the user's login session token can be used
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
						logger.log(Level.WARNING, "Problem saving player profile data.", e);
					}

					//download portrait
					if (!profile.isPrivate()) {
						//download image
						File file = getPortraitFile(playerName);
						Date lastModified = file.exists() ? new Date(file.lastModified()) : null;
						try {
							data = scraper.downloadPortrait(profile, lastModified, client);
						} catch (IOException e) {
							logger.log(Level.WARNING, "Problem downloading profile image.", e);
						}

						//save to cache
						if (data != null) {
							try {
								FileUtils.writeByteArrayToFile(file, data);
							} catch (IOException e) {
								logger.log(Level.WARNING, "Problem saving image to cache.", e);
							}
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

				ImageIcon image = (data == null) ? null : new ImageIcon(data);
				for (Job job : waiting) {
					if (job instanceof RankJob && profile != null) {
						Color color = rankToColor.get(profile.getRank());
						if (color == null) {
							color = Color.black;
						}
						job.label.setForeground(color);
						if (job.listener != null) {
							job.listener.onImageDownloaded(job.label);
						}
						continue;
					}

					if (job instanceof PortraitJob && image != null) {
						PortraitJob j = (PortraitJob) job;
						ImageIcon scaledImage = ImageManager.scale(image, j.maxSize);
						job.label.setIcon(scaledImage);
						if (job.listener != null) {
							job.listener.onImageDownloaded(job.label);
						}
						continue;
					}
				}

				waitList.remove(playerName);
			}
		}

		/**
		 * Saves a scraped profile page information to a properties file.
		 * @param profile the scraped data
		 * @throws IOException
		 */
		private void saveProfileData(PlayerProfile profile) throws IOException {
			PlayerProfileProperties props = new PlayerProfileProperties();
			props.setPrivate(profile.isPrivate());
			props.setRank(profile.getRank());
			props.setJoined(profile.getJoined());

			File file = getPropertiesFile(profile.getPlayerName());
			props.store(file, "");
		}
	}

	private static class Job {
		private final String playerName;
		private final JLabel label;
		private final ImageDownloadedListener listener;

		private Job(String playerName, JLabel label, ImageDownloadedListener listener) {
			this.playerName = playerName;
			this.label = label;
			this.listener = listener;
		}
	}

	/**
	 * Represents a queued download request for a profile image.
	 */
	private static class PortraitJob extends Job {
		private final int maxSize;

		private PortraitJob(String playerName, JLabel label, int maxSize, ImageDownloadedListener listener) {
			super(playerName, label, listener);
			this.maxSize = maxSize;
		}
	}

	/**
	 * Represents a queued download request for a player rank.
	 */
	private static class RankJob extends Job {
		private RankJob(String playerName, JLabel label, ImageDownloadedListener listener) {
			super(playerName, label, listener);
		}
	}

	/**
	 * Represents the properties file used to store the scraped player profile
	 * info.
	 */
	private static class PlayerProfileProperties extends PropertiesWrapper {
		private static final Map<Rank, String> rankToString = new EnumMap<Rank, String>(Rank.class);
		static {
			rankToString.put(Rank.IRON, "iron");
			rankToString.put(Rank.GOLD, "gold");
			rankToString.put(Rank.DIAMOND, "diamond");
			rankToString.put(Rank.MODERATOR, "moderator");
			rankToString.put(Rank.SENIOR_STAFF, "senior_staff");
			rankToString.put(Rank.DEVELOPER, "developer");
			rankToString.put(Rank.ADMIN, "admin");
		}

		private static final Map<String, Rank> stringToRank = new HashMap<String, Rank>();
		static {
			for (Map.Entry<Rank, String> entry : rankToString.entrySet()) {
				stringToRank.put(entry.getValue(), entry.getKey());
			}
		}

		public PlayerProfileProperties() {
			//empty
		}

		public PlayerProfileProperties(File file) throws IOException {
			super(file);
		}

		public boolean isPrivate() {
			return getBoolean("private", false);
		}

		public void setPrivate(boolean private_) {
			setBoolean("private", private_);
		}

		public Rank getRank() {
			String value = get("rank");
			return (value == null) ? null : stringToRank.get(value);
		}

		public void setRank(Rank rank) {
			set("rank", rankToString.get(rank));
		}

		public Date getJoined() {
			try {
				return getDate("joined");
			} catch (ParseException e) {
				return null;
			}
		}

		public void setJoined(Date date) {
			setDate("joined", date);
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
