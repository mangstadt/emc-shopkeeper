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

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ListMultimap;

import emcshop.gui.images.Images;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;
import emcshop.scraper.Rank;
import emcshop.util.CaseInsensitiveHashSet;
import emcshop.util.CaseInsensitiveMultimap;
import emcshop.util.HttpClientFactory;
import emcshop.util.ImageCache;
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

	private final File cacheDir;
	private final Set<String> downloaded = CaseInsensitiveHashSet.create();
	private final ListMultimap<String, Job> waitList = CaseInsensitiveMultimap.create();
	private final LinkedBlockingQueue<String> downloadQueue = new LinkedBlockingQueue<String>();
	private final PlayerProfileSerializer profileSerializer = new PlayerProfileSerializer();

	private final PortraitCache portraitCache = new PortraitCache();
	private final ProfileCache profileCache = new ProfileCache();

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
	 * Loads a profile image, queuing it for download if necessary.
	 * @param playerName the player name
	 * @param label the label to insert the image into
	 * @param maxSize the size to scale the image to
	 */
	public void getPortrait(String playerName, JLabel label, int maxSize) {
		getPortrait(playerName, label, maxSize, null);
	}

	/**
	 * Loads a profile image, queuing it for download if necessary.
	 * @param playerName the player name
	 * @param label the label to insert the image into
	 * @param maxSize the size to scale the image to
	 * @param listener invoked when the image has been assigned to the label
	 */
	public void getPortrait(String playerName, JLabel label, int maxSize, ProfileDownloadedListener listener) {
		ImageIcon image = getPortrait(playerName, maxSize);
		if (image == null) {
			image = portraitCache.unknown(maxSize);
		}
		label.setIcon(image);

		//queue the image for download if necessary
		Job job = new Job(playerName, listener);
		queueJob(job);
	}

	public Color getRankColor(Rank rank) {
		return rankToColor.get(rank);
	}

	/**
	 * Loads a profile image from the cache.
	 * @param playerName the player name
	 * @return the cached image or null if no image exists in the cache
	 */
	public ImageIcon getPortrait(String playerName, int maxSize) {
		ImageIcon image = portraitCache.get(playerName, maxSize);
		if (image != null) {
			return image;
		}

		File file = portraitFile(playerName);
		if (!file.exists()) {
			return null;
		}

		//load the image from the file cache
		byte data[] = null;
		try {
			data = FileUtils.readFileToByteArray(file);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem loading profile image from cache.", e);
		}
		image = new ImageIcon(data);
		image = Images.scale(image, maxSize);
		portraitCache.put(playerName, maxSize, image);
		return image;
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

	public PlayerProfile getProfile(String playerName, ProfileDownloadedListener listener) {
		PlayerProfile profile = profileCache.get(playerName);
		if (profile == null) {
			try {
				profile = profileSerializer.load(playerName);
				if (profile != null) {
					profileCache.set(playerName, profile);
				}
			} catch (IOException e) {
				//ignore
			}
		}
		if (profile != null) {
			return profile;
		}

		//queue the profile page for download if necessary
		Job job = new Job(playerName, listener);
		queueJob(job);
		return null;
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
	private File portraitFile(String playerName) {
		return new File(cacheDir, playerName.toLowerCase());
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
						profileSerializer.save(profile);
						profileCache.set(profile.getPlayerName(), profile);
					} catch (IOException e) {
						logger.log(Level.WARNING, "Problem saving player profile data.", e);
					}

					//download portrait
					if (!profile.isPrivate()) {
						//download image
						File file = portraitFile(playerName);
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

				//call the listener
				if (profile != null) {
					for (Job job : waiting) {
						if (job.listener != null) {
							job.listener.onProfileDownloaded(profile);
						}
					}
				}

				waitList.removeAll(playerName);
			}
		}
	}

	private static class Job {
		private final String playerName;
		private final ProfileDownloadedListener listener;

		private Job(String playerName, ProfileDownloadedListener listener) {
			this.playerName = playerName;
			this.listener = listener;
		}
	}

	public interface ProfileDownloadedListener {
		/**
		 * Called when a player's profile page is downloaded.
		 * @param label the label that was updated
		 */
		void onProfileDownloaded(PlayerProfile profile);
	}

	private static class PortraitCache extends ImageCache {
		public ImageIcon unknown(int maxSize) {
			ImageIcon image = get("(unknown)", maxSize);
			if (image != null) {
				return image;
			}

			image = Images.scale(Images.UNKNOWN, maxSize);
			put("(unknown)", maxSize, image);
			return image;
		}
	}

	private static class ProfileCache {
		private final Map<String, PlayerProfile> cache = new HashMap<String, PlayerProfile>();

		public synchronized PlayerProfile get(String player) {
			return cache.get(key(player));
		}

		public synchronized void set(String player, PlayerProfile profile) {
			cache.put(key(player), profile);
		}

		private String key(String player) {
			return player.toLowerCase();
		}
	}

	private class PlayerProfileSerializer {
		private final BiMap<Rank, String> rankToString;
		private final BiMap<String, Rank> stringToRank;
		{
			ImmutableBiMap.Builder<Rank, String> builder = ImmutableBiMap.builder();
			builder.put(Rank.IRON, "iron");
			builder.put(Rank.GOLD, "gold");
			builder.put(Rank.DIAMOND, "diamond");
			builder.put(Rank.HELPER, "helper");
			builder.put(Rank.MODERATOR, "moderator");
			builder.put(Rank.SENIOR_STAFF, "senior_staff");
			builder.put(Rank.DEVELOPER, "developer");
			builder.put(Rank.ADMIN, "admin");

			rankToString = builder.build();
			stringToRank = rankToString.inverse();
		}

		public PlayerProfile load(String playerName) throws IOException {
			File file = file(playerName);
			if (!file.exists()) {
				return null;
			}

			PlayerProfile profile = new PlayerProfile();
			PropertiesWrapper properties = new PropertiesWrapper(file);

			profile.setPlayerName(properties.get("name"));

			profile.setPrivate(properties.getBoolean("private", false));

			try {
				profile.setJoined(properties.getDate("joined"));
			} catch (ParseException e) {
				//ignore
			}

			String rankStr = properties.get("rank");
			if (rankStr != null) {
				profile.setRank(stringToRank.get(rankStr.toLowerCase()));
			}

			profile.setTitle(properties.get("title"));

			return profile;
		}

		public void save(PlayerProfile profile) throws IOException {
			PropertiesWrapper properties = new PropertiesWrapper();

			properties.set("name", profile.getPlayerName());
			properties.set("private", profile.isPrivate());
			properties.setDate("joined", profile.getJoined());

			Rank rank = profile.getRank();
			if (rank != null) {
				properties.set("rank", rankToString.get(rank));
			}

			properties.set("title", profile.getTitle());

			File file = file(profile.getPlayerName());
			properties.store(file, "");
		}

		private File file(String playerName) {
			return new File(cacheDir, playerName.toLowerCase() + ".properties");
		}
	}
}
