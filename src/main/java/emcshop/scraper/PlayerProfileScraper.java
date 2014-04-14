package emcshop.scraper;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.collect.ImmutableMap;

/**
 * Scrapes information from player profile pages.
 */
public class PlayerProfileScraper {
	private static final Map<String, Rank> titleToRankMapping;
	static {
		ImmutableMap.Builder<String, Rank> builder = ImmutableMap.builder();
		builder.put("Iron Supporter", Rank.IRON);
		builder.put("Gold Supporter", Rank.GOLD);
		builder.put("Diamond Supporter", Rank.DIAMOND);
		builder.put("Moderator", Rank.MODERATOR);
		builder.put("Senior Staff", Rank.SENIOR_STAFF);
		builder.put("Developer", Rank.DEVELOPER);
		builder.put("Lead Developer", Rank.ADMIN);
		builder.put("Community Manager", Rank.ADMIN);

		titleToRankMapping = builder.build();
	}

	/**
	 * Downloads and scrapes a player's profile page.
	 * @param playerName the player name
	 * @return the scraped page
	 * @throws IOException if there's a problem downloading the profile
	 */
	public PlayerProfile downloadProfile(String playerName) throws IOException {
		return downloadProfile(playerName, new DefaultHttpClient());
	}

	/**
	 * Downloads and scrapes a player's profile page.
	 * @param playerName the player name
	 * @param client the HTTP client to use
	 * @return the scraped page or null if the player does not exist
	 * @throws IOException if there's a problem downloading the profile
	 */
	public PlayerProfile downloadProfile(String playerName, HttpClient client) throws IOException {
		Document document;
		HttpGet request = new HttpGet("http://u.emc.gs/" + playerName);
		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		try {
			document = Jsoup.parse(entity.getContent(), "UTF-8", "http://empireminecraft.com");
		} finally {
			EntityUtils.consume(entity);
		}

		return scrapeProfile(playerName, document);
	}

	/**
	 * Scrapes a player's profile page.
	 * @param playerName the player name
	 * @param document the profile page
	 * @return the scraped page or null if the player does not exist
	 * @throws IOException if there's a problem downloading the profile
	 */
	public PlayerProfile scrapeProfile(String playerName, Document document) throws IOException {
		boolean isPrivate = isPrivate(document);
		String scrapedPlayerName = scrapePlayerName(document);
		if (!isPrivate && scrapedPlayerName == null) {
			//user does not exist
			return null;
		}

		PlayerProfile profile = new PlayerProfile();
		profile.setPrivate(isPrivate);
		if (isPrivate) {
			profile.setPlayerName(playerName);
			return profile;
		}

		profile.setPlayerName(scrapedPlayerName);
		profile.setPortraitUrl(scrapePortraitUrl(document));
		profile.setRank(scrapeRank(document));
		profile.setJoined(scrapeJoined(document));

		return profile;
	}

	/**
	 * Downloads a player's profile portrait.
	 * @param profile the player profile
	 * @param lastModified the value of the "If-Modified-Since" HTTP header, or
	 * null not to add this header
	 * @param client the HTTP client to use to download the image
	 * @return the image data, null if the image hasn't changed, or null the
	 * image could not be fetched
	 * @throws IOException if there's a problem downloading the profile
	 */
	public byte[] downloadPortrait(PlayerProfile profile, Date lastModified, HttpClient client) throws IOException {
		String url = profile.getPortraitUrl();
		if (url == null) {
			return null;
		}

		HttpGet request = new HttpGet(url);
		if (lastModified != null) {
			DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			request.addHeader("If-Modified-Since", df.format(lastModified));
		}

		HttpResponse response = client.execute(request);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode == HttpStatus.SC_NOT_MODIFIED || statusCode == HttpStatus.SC_NOT_FOUND) {
			//image not modified or not found
			return null;
		}

		HttpEntity entity = response.getEntity();
		try {
			return EntityUtils.toByteArray(entity);
		} finally {
			EntityUtils.consumeQuietly(entity);
		}
	}

	private boolean isPrivate(Document document) {
		Elements elements = document.select("label[for=ctrl_0]");
		return !elements.isEmpty();
	}

	private String scrapePlayerName(Document document) {
		Elements elements = document.getElementsByAttributeValue("itemprop", "name");
		return elements.isEmpty() ? null : elements.first().text();
	}

	private String scrapePortraitUrl(Document document) {
		Elements elements = document.select(".avatarScaler img");
		if (elements.isEmpty()) { //players can choose to make their profile private
			return null;
		}

		String src = elements.first().absUrl("src");
		return src.isEmpty() ? null : src;
	}

	private Rank scrapeRank(Document document) {
		Elements elements = document.select(".userTitle span");
		if (elements.isEmpty()) {
			return null;
		}

		String title = elements.first().text();
		return titleToRankMapping.get(title);
	}

	private Date scrapeJoined(Document document) {
		Elements elements = document.select(".infoblock .secondaryContent");
		if (elements.isEmpty()) {
			return null;
		}

		boolean parseNextElement = false;
		for (Element element : elements) {
			for (Element child : element.children()) {
				String text = child.text();

				if (text.equals("Joined:")) {
					parseNextElement = true;
					continue;
				}

				if (parseNextElement) {
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
