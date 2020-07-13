package emcshop.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.common.collect.ListMultimap;
import com.google.common.net.UrlEscapers;

import emcshop.gui.images.Images;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.PlayerProfileScraper;
import emcshop.util.CaseInsensitiveHashSet;
import emcshop.util.CaseInsensitiveMultimap;
import emcshop.util.ImageCache;
import emcshop.util.PropertiesWrapper;

/**
 * Used for downloading player profile data.
 */
public class ProfileLoader {
	private static final Logger logger = Logger.getLogger(ProfileLoader.class.getName());

	private final Path cacheDir;
	private final EmcWebsiteSessionFactory sessionFactory;
	private final Set<String> downloaded = CaseInsensitiveHashSet.create();
	private final ListMultimap<String, Job> waitList = CaseInsensitiveMultimap.create();
	private final LinkedBlockingQueue<String> downloadQueue = new LinkedBlockingQueue<>();
	private final PlayerProfileSerializer profileSerializer = new PlayerProfileSerializer();

	private final PortraitCache portraitCache = new PortraitCache();
	private final ProfileCache profileCache = new ProfileCache();

	private int threads = 4;

	private PlayerProfileScraper scraper = new PlayerProfileScraper();

	/**
	 * The number of items on the queue, plus the number of jobs currently being
	 * processed by the threads (for unit testing purposes).
	 */
	volatile int jobsBeingProcessed = 0;

	/**
	 * Creates a profile image loader.
	 * @param cacheDir the directory where the images are cached
	 * @param sessionFactory creates an HTTP connection that is used to download
	 * profiles from the EMC website
	 */
	public ProfileLoader(Path cacheDir, EmcWebsiteSessionFactory sessionFactory) {
		this.cacheDir = cacheDir;
		this.sessionFactory = sessionFactory;
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
		ImageIcon image = getPortraitFromCache(playerName, maxSize);
		if (image == null) {
			image = portraitCache.unknown(maxSize);
		}
		label.setIcon(image);

		//queue the image for download if necessary
		Job job = new Job(playerName, listener);
		queueJob(job);
	}

	/**
	 * Loads a profile image from the cache.
	 * @param playerName the player name
	 * @return the cached image or null if no image exists in the cache
	 */
	public ImageIcon getPortraitFromCache(String playerName, int maxSize) {
		ImageIcon image = portraitCache.get(playerName, maxSize);
		if (image != null) {
			return image;
		}

		Path file = portraitFile(playerName);
		if (!Files.exists(file)) {
			return null;
		}

		//load the image from the file cache
		byte data[] = null;
		try {
			data = Files.readAllBytes(file);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem loading profile image from cache.", e);
			return null;
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
				logger.log(Level.WARNING, "Problem loading profile properties file for player \"" + playerName + "\". Profile information will be downloaded instead.", e);
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

	/**
	 * Clears all private profiles from the in-memory cache. This should be
	 * called after the user logs into the EMC website after running an update.
	 */
	public void clearPrivateProfilesFromCache() {
		List<String> playerNames = profileCache.getPlayerNamesOfPrivateProfiles();
		synchronized (downloaded) {
			downloaded.removeAll(playerNames);
		}

		profileCache.clearPrivateProfiles();
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
					/*
					 * The "put" method will wait until there is enough room in
					 * the queue before adding it to the queue. While it's
					 * waiting, an InterruptedException could be thrown.
					 * However, this should never happen because the queue
					 * doesn't have a max size.
					 */
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
	private Path portraitFile(String playerName) {
		return cacheDir.resolve(playerName.toLowerCase());
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
				PlayerProfile profile;
				CloseableHttpClient client = sessionFactory.createSession(); //get the session information each time so the user's login session token can be used
				try {
					try {
						Document page = downloadProfilePage(playerName, client);
						profile = scraper.scrapeProfile(playerName, page);
					} catch (IOException e) {
						profile = null;
						logger.log(Level.WARNING, "Problem downloading player profile page.", e);
					}

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
							Path cachedFile = portraitFile(playerName);
							Instant lastModified;
							try {
								lastModified = Files.exists(cachedFile) ? Files.getLastModifiedTime(cachedFile).toInstant() : null;
							} catch (IOException e) {
								logger.log(Level.WARNING, "Problem determining last modified time of cached profile image.", e);
								lastModified = null;
							}

							byte[] data;
							try {
								data = scraper.downloadPortrait(profile, lastModified, client);
							} catch (IOException e) {
								data = null;
								logger.log(Level.WARNING, "Problem downloading profile image.", e);
							}

							//save to cache
							if (data != null) {
								try {
									Files.write(cachedFile, data);
									portraitCache.clear(playerName);
								} catch (IOException e) {
									logger.log(Level.WARNING, "Problem saving image to cache.", e);
								}
							}
						}
					}
				} finally {
					IOUtils.closeQuietly(client);
				}

				List<Job> waiting;
				synchronized (downloaded) {
					downloaded.add(playerName);

					waiting = waitList.get(playerName);
					if (waiting.isEmpty()) {
						/*
						 * There are no labels waiting to be updated. This
						 * should never happen; there should always be at least
						 * 1 label waiting to be updated.
						 */
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

		private Document downloadProfilePage(String playerName, HttpClient client) throws IOException {
			/*
			 * EmcWebsiteConnection#getPlayerProfile cannot be used because its
			 * HttpClient is configured to IGNORE redirects!
			 */
			String url = "https://u.emc.gs/" + UrlEscapers.urlPathSegmentEscaper().escape(playerName);
			HttpGet request = new HttpGet(url);
			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();

			try (InputStream in = entity.getContent()) {
				return Jsoup.parse(in, "UTF-8", "https://empireminecraft.com");
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
		 * @param profile the profile that was downloaded
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
		private final Map<String, PlayerProfile> cache = new HashMap<>();

		public PlayerProfile get(String player) {
			String key = key(player);
			synchronized (this) {
				return cache.get(key);
			}
		}

		public void set(String player, PlayerProfile profile) {
			String key = key(player);
			synchronized (this) {
				cache.put(key, profile);
			}
		}

		public synchronized void clearPrivateProfiles() {
			cache.values().removeIf(PlayerProfile::isPrivate);
		}

		public synchronized List<String> getPlayerNamesOfPrivateProfiles() {
			return cache.values().stream() //@formatter:off
				.filter(PlayerProfile::isPrivate)
				.map(PlayerProfile::getPlayerName)
			.collect(Collectors.toList()); //@formatter:on
		}

		private String key(String player) {
			return player.toLowerCase();
		}
	}

	private class PlayerProfileSerializer {
		public PlayerProfile load(String playerName) throws IOException {
			Path file = file(playerName);
			if (!Files.exists(file)) {
				return null;
			}

			PropertiesWrapper properties = new PropertiesWrapper(file);

			LocalDate joined;
			try {
				LocalDateTime date = properties.getDate("joined");
				joined = (date == null) ? null : date.toLocalDate();
			} catch (DateTimeException e) {
				joined = null;
			}

			//@formatter:off
			return new PlayerProfile.Builder()
				.playerName(properties.get("name"))
				.private_(properties.getBoolean("private", false))
				.joined(joined)
				.rank(properties.get("rank"), properties.get("rankColor"))
				.title(properties.get("title"))
			.build();
			//@formatter:on
		}

		public void save(PlayerProfile profile) throws IOException {
			PropertiesWrapper properties = new PropertiesWrapper();

			properties.set("name", profile.getPlayerName());
			properties.set("private", profile.isPrivate());

			LocalDate joined = profile.getJoined();
			properties.setDate("joined", (joined == null) ? null : joined.atStartOfDay());

			properties.set("rank", profile.getRank());
			properties.set("rankColor", profile.getRankColor());
			properties.set("title", profile.getTitle());

			Path file = file(profile.getPlayerName());
			properties.store(file, "");
		}

		private Path file(String playerName) {
			return cacheDir.resolve(playerName.toLowerCase() + ".properties");
		}
	}

	public interface EmcWebsiteSessionFactory {
		CloseableHttpClient createSession();
	}
}
