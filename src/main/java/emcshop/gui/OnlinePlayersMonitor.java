package emcshop.gui;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import emcshop.scraper.OnlinePlayersScraper;

/**
 * Keeps track of what players are online.
 */
public class OnlinePlayersMonitor {
	private static final Logger logger = Logger.getLogger(OnlinePlayersMonitor.class.getName());

	private final OnlinePlayersScraper scraper;
	private final int refreshRate;
	private final Set<String> onlinePlayers = new HashSet<String>();

	/**
	 * @param scraper the website scraper
	 * @param refreshRate how often the player list will be refreshed (in
	 * milliseconds)
	 */
	public OnlinePlayersMonitor(OnlinePlayersScraper scraper, int refreshRate) {
		this.scraper = scraper;
		this.refreshRate = refreshRate;
	}

	/**
	 * Determines if a player is online or not.
	 * @param playerName the player name
	 * @return true if the player is online, false if not
	 */
	public boolean isPlayerOnline(String playerName) {
		playerName = playerName.toLowerCase();
		synchronized (this) {
			return onlinePlayers.contains(playerName);
		}
	}

	/**
	 * Starts the monitor.
	 * @return the thread
	 */
	public Thread start() {
		WorkerThread t = new WorkerThread();
		t.setDaemon(true);
		t.start();
		return t;
	}

	private class WorkerThread extends Thread {
		@Override
		public void run() {
			while (true) {
				try {
					Set<String> players = scraper.getOnlinePlayers();
					synchronized (OnlinePlayersMonitor.this) {
						onlinePlayers.clear();
						for (String player : players) {
							onlinePlayers.add(player.toLowerCase());
						}
					}
				} catch (IOException e) {
					logger.log(Level.WARNING, "Unable to retrieve list of online players.", e);
				}

				try {
					Thread.sleep(refreshRate);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}
}
