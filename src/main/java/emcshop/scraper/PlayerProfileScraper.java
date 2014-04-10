package emcshop.scraper;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class PlayerProfileScraper {
	private static final Map<String, Rank> titleToRankMapping;
	static {
		Map<String, Rank> m = new HashMap<String, Rank>();
		m.put("Iron Supporter", Rank.IRON);
		m.put("Gold Supporter", Rank.GOLD);
		m.put("Diamond Supporter", Rank.DIAMOND);
		m.put("Moderator", Rank.MODERATOR);
		m.put("Senior Staff", Rank.SENIOR_STAFF);
		m.put("Developer", Rank.DEVELOPER);
		m.put("Lead Developer", Rank.ADMIN);
		m.put("Community Manager", Rank.ADMIN);

		titleToRankMapping = Collections.unmodifiableMap(m);
	}

	/**
	 * Scrapes a player's profile page.
	 * @param playerName the player name
	 * @return the scraped page
	 * @throws IOException
	 */
	public PlayerProfile scrapeProfile(String playerName) throws IOException {
		return scrapeProfile(playerName, new DefaultHttpClient());
	}

	/**
	 * Scrapes a player's profile page.
	 * @param playerName the player name
	 * @param client the HTTP client to use
	 * @return the scraped page
	 * @throws IOException
	 */
	public PlayerProfile scrapeProfile(String playerName, HttpClient client) throws IOException {
		Document profilePage = downloadProfilePage(playerName, client);

		PlayerProfile profile = new PlayerProfile();

		String player = getPlayerName(profilePage); //get the proper case of the player's name
		if (player == null) {
			player = playerName;
		}
		profile.setPlayerName(player);

		profile.setPortraitUrl(getPortraitUrl(profilePage));
		profile.setRank(getRank(profilePage));
		profile.setJoined(getJoined(profilePage));

		return profile;
	}

	private Document downloadProfilePage(String playerName, HttpClient client) throws IOException {
		HttpGet request = new HttpGet("http://u.emc.gs/" + playerName);
		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		try {
			return Jsoup.parse(entity.getContent(), "UTF-8", "");
		} finally {
			EntityUtils.consume(entity);
		}
	}

	private String getPlayerName(Document document) {
		Elements elements = document.getElementsByAttributeValue("itemprop", "name");
		return elements.isEmpty() ? null : elements.first().text();
	}

	private String getPortraitUrl(Document document) {
		Elements elements = document.select(".avatarScaler img");
		if (elements.isEmpty()) { //players can choose to make their profile private
			return null;
		}

		String src = elements.first().attr("src");
		if (src.isEmpty()) {
			return null;
		}

		if (!src.startsWith("http")) {
			src = "http://empireminecraft.com/" + src;
		}
		return src;
	}

	private Rank getRank(Document document) {
		Elements elements = document.select(".userTitle span");
		if (elements.isEmpty()) {
			return null;
		}

		String title = elements.first().text();
		return titleToRankMapping.get(title);
	}

	private Date getJoined(Document document) {
		Elements elements = document.select(".infoblock .secondaryContent");
		if (elements.isEmpty()) {
			return null;
		}

		boolean useNext = false;
		for (Element element : elements) {
			for (Element child : element.children()) {
				String text = child.text();

				if (text.equals("Joined:")) {
					useNext = true;
					continue;
				}

				if (useNext) {
					DateFormat df = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
					try {
						return df.parse(text);
					} catch (ParseException e) {
						return null;
					}
				}
			}
		}

		return null;
	}
}
