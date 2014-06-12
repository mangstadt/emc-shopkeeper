package emcshop.gui;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
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

import com.google.common.collect.ListMultimap;

import emcshop.gui.images.ImageManager;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;
import emcshop.scraper.Rank;
import emcshop.util.CaseInsensitiveHashSet;
import emcshop.util.CaseInsensitiveMultimap;
import emcshop.util.HttpClientFactory;
import emcshop.util.PropertiesWrapper;

/**
 * Used for downloading player profile data.
 */
public class ProfileLoader {
	private static final Logger logger = Logger.getLogger(ProfileLoader.class.getName());

	private static final Map<Rank, Color> rankToColor = new EnumMap<Rank, Color>(Rank.class);
	{
		rankToColor.put(Rank.IRON, new Color(128, 128, 128));
		rankToColor.put(Rank.GOLD, new Color(181, 181, 0));
		rankToColor.put(Rank.DIAMOND, new Color(0, 181, 194));
		rankToColor.put(Rank.HELPER, new Color(224, 165, 0));
		rankToColor.put(Rank.MODERATOR, new Color(0, 64, 0));
		rankToColor.put(Rank.SENIOR_STAFF, new Color(0, 255, 0));
		rankToColor.put(Rank.DEVELOPER, new Color(0, 0, 128));
		rankToColor.put(Rank.ADMIN, new Color(209, 0, 195));
	}
	private static final Color noRankColor = Color.black;

	private final File cacheDir;
	private final Set<String> downloaded = CaseInsensitiveHashSet.create();
	private final ListMultimap<String, Job> waitList = CaseInsensitiveMultimap.create();
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

	/**
	 * Gets the number of threads that are used to download the profile pages.
	 * @return the number of threads
	 */
	public int getThreads() {
		return threads;
	}

	/**
	 * Sets the number of threads that are used to download the profile pages.
	 * @param threads the number of threads (defaults to 4)
	 */
	public void setThreads(int threads) {
		this.threads = threads;
	}

	/**
	 * Gets the factory used to create {@link HttpClient} objects, which are
	 * used to download the profile pages.
	 * @return the factory
	 */
	public HttpClientFactory getHttpClientFactory() {
		return clientFactory;
	}

	/**
	 * Sets the factory used to create {@link HttpClient} objects, which are
	 * used to download the profile pages.
	 * @param clientFactory the factory
	 */
	public void setHttpClientFactory(HttpClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	/**
	 * Gets the object used to download and scrape the profile pages.
	 * @return the profile page scraper
	 */
	public PlayerProfileScraper getProfilePageScraper() {
		return scraper;
	}

	/**
	 * Sets the object used to download and scrape the profile pages.
	 * @param scraper the profile page scraper
	 */
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
	 * Queues a profile for download if it has not already been downloaded.
	 * @param playerName the player name
	 * @param listener invoked when the profile has been downloaded
	 */
	public void queueProfileForDownload(String playerName, ProfileDownloadedListener listener) {
		Job job = new Job(playerName, null, listener);
		queueJob(job);
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
	public void loadPortrait(String playerName, JLabel label, int maxSize, ProfileDownloadedListener listener) {
		ImageIcon image = getPortraitFromCache(playerName);
		if (image == null) {
			image = ImageManager.getUnknown();
		}

		//assign an image to the label
		image = ImageManager.scale(image, maxSize);
		label.setIcon(image);

		//queue the image for download if necessary
		Job job = new PortraitJob(playerName, label, maxSize, listener);
		queueJob(job);
	}

	/**
	 * Loads a profile image from the cache.
	 * @param playerName the player name
	 * @return the cached image or null if no image exists in the cache
	 */
	public ImageIcon getPortraitFromCache(String playerName) {
		//attempt to load the image from the cache
		byte data[] = null;
		try {
			File file = getPortraitFile(playerName);
			if (file.exists()) {
				data = FileUtils.readFileToByteArray(file);
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem loading profile image from cache.", e);
		}

		return (data == null) ? null : new ImageIcon(data);
	}

	/**
	 * Colors a label depending on the given player's rank.
	 * @param playerName the player name
	 * @param label the label to change the color of
	 * @param listener invoked when the profile page has been scraped
	 */
	public void loadRank(String playerName, JLabel label, ProfileDownloadedListener listener) {
		Color color = getRankColor(playerName);
		if (color != null) {
			label.setForeground(color);
		}

		//queue the profile page for download if necessary
		Job job = new RankJob(playerName, label, listener);
		queueJob(job);
	}

	/**
	 * Gets the color of the player's rank.
	 * @param playerName the player name
	 * @return the color or null if the player has no rank or null if the player
	 * is unknown
	 */
	public Color getRankColor(String playerName) {
		PlayerProfileProperties props = loadProfileData(playerName);
		if (props == null) {
			return null;
		}

		Rank rank = props.getRank();
		return rankToColor.get(rank);
	}

	/**
	 * Determines if a player's profile was downloaded or not.
	 * @param playerName the player name
	 * @return true if the player's profile was downloaded, false if not
	 */
	public boolean wasDownloaded(String playerName) {
		synchronized (downloaded) {
			return downloaded.contains(playerName);
		}
	}

	/**
	 * Gets the date that a player joined EMC.
	 * @param playerName the player name
	 * @return the join date or null if not found
	 */
	public Date getJoinDate(String playerName) {
		PlayerProfileProperties props = loadProfileData(playerName);
		return (props == null) ? null : props.getJoined();
	}

	/**
	 * Gets a player's title.
	 * @param the player name
	 * @return the title (e.g. "Diamond Supporter") or null if not found
	 */
	public String getTitle(String playerName) {
		PlayerProfileProperties props = loadProfileData(playerName);
		return (props == null) ? null : props.getTitle();
	}

	/**
	 * Loads a player's profile data from the cache.
	 * @param playerName the player name
	 * @return the profile data or null if not found
	 */
	private PlayerProfileProperties loadProfileData(String playerName) {
		File file = getPropertiesFile(playerName);
		if (!file.exists()) {
			return null;
		}

		try {
			return new PlayerProfileProperties(file);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem loading profile properties from cache.", e);
			return null;
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
		props.setTitle(profile.getTitle());
		props.setJoined(profile.getJoined());

		File file = getPropertiesFile(profile.getPlayerName());
		props.store(file, "");
	}

	private void queueJob(Job job) {
		synchronized (downloaded) {
			if (wasDownloaded(job.playerName)) {
				//the image has already been downloaded, so the cached version is the most up-to-date version of the image
				return;
			}

			//see if the image is already queued for download
			if (!waitList.containsKey(job.playerName)) {
				//player name is not queued for download, so add it to the queue
				try {
					jobsBeingProcessed++;
					downloadQueue.put(job.playerName);
				} catch (InterruptedException e) {
					//should never be thrown because the queue doesn't have a max size
					logger.log(Level.SEVERE, "Queue's \"put\" operation was interrupted.", e);
				}
			}

			//add job to wait list
			waitList.put(job.playerName, job);
		}
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
					profile = scraper.downloadProfile(playerName, client);
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
					if (waiting.isEmpty()) {
						//there are no labels waiting to be updated
						//should never happen, there should always be at least 1 label waiting to be updated
						continue;
					}
				}

				//update all the labels that are waiting to be updated
				ImageIcon image = (data == null) ? null : new ImageIcon(data);
				for (Job job : waiting) {
					if (job instanceof RankJob) {
						if (profile != null) {
							Color color = rankToColor.get(profile.getRank());
							if (color == null) {
								color = noRankColor;
							}
							job.label.setForeground(color);
							if (job.listener != null) {
								job.listener.onProfileDownloaded(job.label);
							}
						}
						continue;
					}

					if (job instanceof PortraitJob) {
						if (image != null) {
							PortraitJob portraitJob = (PortraitJob) job;
							ImageIcon scaledImage = ImageManager.scale(image, portraitJob.maxSize);
							job.label.setIcon(scaledImage);
							if (job.listener != null) {
								job.listener.onProfileDownloaded(job.label);
							}
						}
						continue;
					}

					if (job instanceof Job) {
						if (job.listener != null) {
							job.listener.onProfileDownloaded(job.label);
						}
						continue;
					}
				}

				waitList.removeAll(playerName);
			}
		}
	}

	private static class Job {
		private final String playerName;
		private final JLabel label;
		private final ProfileDownloadedListener listener;

		private Job(String playerName, JLabel label, ProfileDownloadedListener listener) {
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

		private PortraitJob(String playerName, JLabel label, int maxSize, ProfileDownloadedListener listener) {
			super(playerName, label, listener);
			this.maxSize = maxSize;
		}
	}

	/**
	 * Represents a queued download request for a player rank.
	 */
	private static class RankJob extends Job {
		private RankJob(String playerName, JLabel label, ProfileDownloadedListener listener) {
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
			rankToString.put(Rank.HELPER, "helper");
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
			super();
		}

		public PlayerProfileProperties(File file) throws IOException {
			super(file);
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

		public String getTitle() {
			return get("title");
		}

		public void setTitle(String title) {
			set("title", title);
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

	public interface ProfileDownloadedListener {
		/**
		 * Called when a player's profile page is downloaded.
		 * @param label the label that was updated
		 */
		void onProfileDownloaded(JLabel label);
	}
}
