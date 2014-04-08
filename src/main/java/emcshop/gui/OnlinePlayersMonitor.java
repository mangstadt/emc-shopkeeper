package emcshop.gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import emcshop.scraper.EMCServer;
import emcshop.scraper.OnlinePlayersScraper;

/**
 * Keeps track of what players are online.
 */
public class OnlinePlayersMonitor {
	private static final Logger logger = Logger.getLogger(OnlinePlayersMonitor.class.getName());

	private final OnlinePlayersScraper scraper;
	private final int refreshRate;
	private final Map<String, EMCServer> onlinePlayers = new HashMap<String, EMCServer>();

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
	 * Gets the server that a player is currently logged into.
	 * @param playerName the player name
	 * @return the server or null if the player is not logged in
	 */
	public EMCServer getPlayerServer(String playerName) {
		playerName = playerName.toLowerCase();
		synchronized (onlinePlayers) {
			return onlinePlayers.get(playerName);
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
					Map<String, EMCServer> players = scraper.getOnlinePlayers();
					synchronized (onlinePlayers) {
						onlinePlayers.clear();
						for (Map.Entry<String, EMCServer> entry : players.entrySet()) {
							String playerName = entry.getKey();
							EMCServer server = entry.getValue();

							onlinePlayers.put(playerName.toLowerCase(), server);
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
