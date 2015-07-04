package emcshop.scraper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.mangstadt.emc.net.EmcServer;
import com.github.mangstadt.emc.net.EmcWebsiteConnection;

/**
 * Downloads the list of online players from the EMC website.
 * @uathor Michael Angstadt
 */
public class OnlinePlayersScraper {
	private final EmcWebsiteConnection connection;

	/**
	 * @param connection the EMC website connection to use
	 */
	public OnlinePlayersScraper(EmcWebsiteConnection connection) {
		this.connection = connection;
	}

	/**
	 * Retrieves the list of online players from the EMC website.
	 * @return the online players (key), along with the server they are logged
	 * into (value)
	 * @throws IOException if there's a problem downloading the list
	 */
	public Map<String, EmcServer> getOnlinePlayers() throws IOException {
		Map<String, EmcServer> onlinePlayers = new HashMap<String, EmcServer>();

		for (EmcServer server : EmcServer.values()) {
			List<String> players = connection.getOnlinePlayers(server);
			for (String player : players) {
				onlinePlayers.put(player, server);
			}
		}

		return onlinePlayers;
	}
}
