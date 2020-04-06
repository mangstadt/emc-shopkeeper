package emcshop.gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import com.github.mangstadt.emc.net.EmcServer;
import com.github.mangstadt.emc.net.EmcWebsiteConnection;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * Keeps track of what players are online.
 * @author Michael Angstadt
 */
public class OnlinePlayersMonitor {
	private static final Logger logger = Logger.getLogger(OnlinePlayersMonitor.class.getName());

	private final EmcWebsiteConnection connection;
	private final int refreshRate;
	private final Map<String, EmcServer> onlinePlayers = new HashMap<>();

	/**
	 * @param connection the connection to the EMC website
	 * @param refreshRate how often the player list will be refreshed (in
	 * milliseconds)
	 */
	public OnlinePlayersMonitor(EmcWebsiteConnection connection, int refreshRate) {
		this.connection = connection;
		this.refreshRate = refreshRate;
	}

	/**
	 * Gets the server that a player is currently logged into.
	 * @param playerName the player name
	 * @return the server or null if the player is not logged in
	 */
	public EmcServer getPlayerServer(String playerName) {
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
			try {
				while (true) {
					try {
						Multimap<EmcServer, String> onlinePlayersMultimap = ArrayListMultimap.create();
						for (EmcServer server : EmcServer.values()) {
							List<String> players = connection.getOnlinePlayers(server);
							onlinePlayersMultimap.putAll(server, players);
						}

						synchronized (onlinePlayers) {
							onlinePlayers.clear();
							for (Map.Entry<EmcServer, String> entry : onlinePlayersMultimap.entries()) {
								EmcServer server = entry.getKey();
								String playerName = entry.getValue();

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
			} finally {
				IOUtils.closeQuietly(connection);
			}
		}
	}
}
