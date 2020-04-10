package emcshop.scraper;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

/**
 * Scrapes information from player profile pages.
 * @author Michael Angstadt
 */
public class PlayerProfileScraper {
	private final DateTimeFormatter ifModifiedSinceHeaderDateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZone(ZoneId.of("GMT"));
	private final DateTimeFormatter joinedDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy").withLocale(Locale.US);

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

		PlayerProfile.Builder builder = new PlayerProfile.Builder();
		builder.private_(isPrivate);
		if (isPrivate) {
			return builder.playerName(playerName).build();
		}

		scrapeRankAndTitle(document, builder);

		//@formatter:off
		return builder
			.playerName(scrapedPlayerName)
			.portraitUrl(scrapePortraitUrl(document))
			.joined(scrapeJoined(document))
		.build();
		//@formatter:on
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
	public byte[] downloadPortrait(PlayerProfile profile, Instant lastModified, HttpClient client) throws IOException {
		String url = profile.getPortraitUrl();
		if (url == null) {
			return null;
		}

		HttpGet request = new HttpGet(url);
		if (lastModified != null) {
			request.addHeader("If-Modified-Since", ifModifiedSinceHeaderDateFormat.format(lastModified));
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

	private void scrapeRankAndTitle(Document document, PlayerProfile.Builder builder) {
		Elements elements = document.select(".userTitle");
		if (elements.isEmpty()) {
			return;
		}

		//Example of what the child nodes look like for the ".userTitle" element:
		//<span style="color:#00BFBF;font-weight:bold;">Diamond Supporter</span><br>Elite Member
		Pattern colorRegex = Pattern.compile("color:(#[0-9a-f]{6})", Pattern.CASE_INSENSITIVE);
		for (Node node : elements.first().childNodes()) {
			if (node instanceof Element) {
				Element element = (Element) node;
				if (element.tagName().equals("span")) {
					String rank = element.text();
					String color;
					{
						String style = element.attr("style");
						Matcher m = colorRegex.matcher(style);
						color = m.find() ? m.group(1) : null;
					}

					builder.rank(rank, color);
				}
				continue;
			}

			if (node instanceof TextNode) {
				TextNode textNode = (TextNode) node;
				String title = textNode.text();
				builder.title(title);
				continue;
			}
		}
	}

	private LocalDate scrapeJoined(Document document) {
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
					try {
						return LocalDate.from(joinedDateFormat.parse(text));
					} catch (DateTimeException e) {
						return null;
					}
				}
			}
		}

		return null;
	}
}
