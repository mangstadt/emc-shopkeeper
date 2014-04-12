package emcshop.scraper;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

/**
 * Downloads the list of online players from the EMC website.
 */
public class OnlinePlayersScraper {
	private static final Map<EMCServer, Integer> serverNumbers = new EnumMap<EMCServer, Integer>(EMCServer.class);
	static {
		serverNumbers.put(EMCServer.SMP1, 1);
		serverNumbers.put(EMCServer.SMP2, 2);
		serverNumbers.put(EMCServer.SMP3, 4);
		serverNumbers.put(EMCServer.SMP4, 5);
		serverNumbers.put(EMCServer.SMP5, 6);
		serverNumbers.put(EMCServer.SMP6, 7);
		serverNumbers.put(EMCServer.SMP7, 8);
		serverNumbers.put(EMCServer.SMP8, 9);
		serverNumbers.put(EMCServer.SMP9, 10);
		serverNumbers.put(EMCServer.UTOPIA, 3);
	}

	private final HttpClient client;
	private final Pattern nameRegex = Pattern.compile("\"name\":\"(.*?)\"");

	/**
	 * @param client the HTTP client to use
	 */
	public OnlinePlayersScraper(HttpClient client) {
		this.client = client;
	}

	/**
	 * Retrieves the list of online players from the EMC website.
	 * @return the online players (key), along with the server they are logged
	 * into (value)
	 * @throws IOException
	 */
	public Map<String, EMCServer> getOnlinePlayers() throws IOException {
		Map<String, EMCServer> onlinePlayers = new HashMap<String, EMCServer>();

		for (EMCServer server : EMCServer.values()) {
			//get the JSON response
			String json;
			HttpEntity entity = null;
			try {
				Integer number = serverNumbers.get(server);
				HttpGet request = new HttpGet("http://empireminecraft.com/api/server-online-" + number + ".json");

				HttpResponse response = client.execute(request);
				entity = response.getEntity();
				json = EntityUtils.toString(entity);
			} finally {
				EntityUtils.consumeQuietly(entity);
			}

			//scrape the response
			onlinePlayers.putAll(scrape(server, json));
		}

		return onlinePlayers;
	}

	public Map<String, EMCServer> scrape(EMCServer server, String json) {
		Map<String, EMCServer> onlinePlayers = new HashMap<String, EMCServer>();

		Matcher matcher = nameRegex.matcher(json);
		while (matcher.find()) {
			String playerName = matcher.group(1);
			onlinePlayers.put(playerName, server);
		}

		return onlinePlayers;
	}
}
