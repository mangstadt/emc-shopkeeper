package emcshop.scraper;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
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
	 * @return the online players
	 * @throws IOException
	 */
	public Set<String> getOnlinePlayers() throws IOException {
		Set<String> onlinePlayers = new HashSet<String>();

		for (int i = 1; i <= 10; i++) {
			//get the JSON response
			String json;
			HttpEntity entity = null;
			try {
				HttpGet request = new HttpGet("http://empireminecraft.com/api/server-online-" + i + ".json");

				HttpResponse response = client.execute(request);
				entity = response.getEntity();
				json = EntityUtils.toString(entity);
			} finally {
				EntityUtils.consumeQuietly(entity);
			}

			//scrape the response
			Matcher matcher = nameRegex.matcher(json);
			while (matcher.find()) {
				onlinePlayers.add(matcher.group(1));
			}
		}

		return onlinePlayers;
	}
}
