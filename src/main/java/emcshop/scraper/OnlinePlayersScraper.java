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

import com.google.common.collect.ImmutableMap;

/**
 * Downloads the list of online players from the EMC website.
 */
public class OnlinePlayersScraper {
	private static final Map<EmcServer, Integer> serverNumbers;
	static {
		Map<EmcServer, Integer> m = new EnumMap<EmcServer, Integer>(EmcServer.class);
		m.put(EmcServer.SMP1, 1);
		m.put(EmcServer.SMP2, 2);
		m.put(EmcServer.SMP3, 4);
		m.put(EmcServer.SMP4, 5);
		m.put(EmcServer.SMP5, 6);
		m.put(EmcServer.SMP6, 7);
		m.put(EmcServer.SMP7, 8);
		m.put(EmcServer.SMP8, 9);
		m.put(EmcServer.SMP9, 10);
		m.put(EmcServer.UTOPIA, 3);

		serverNumbers = ImmutableMap.copyOf(m);
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
	 * @throws IOException if there's a problem downloading the list
	 */
	public Map<String, EmcServer> getOnlinePlayers() throws IOException {
		Map<String, EmcServer> onlinePlayers = new HashMap<String, EmcServer>();

		for (EmcServer server : EmcServer.values()) {
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

	public Map<String, EmcServer> scrape(EmcServer server, String json) {
		Map<String, EmcServer> onlinePlayers = new HashMap<String, EmcServer>();

		Matcher matcher = nameRegex.matcher(json);
		while (matcher.find()) {
			String playerName = matcher.group(1);
			onlinePlayers.put(playerName, server);
		}

		return onlinePlayers;
	}
}
