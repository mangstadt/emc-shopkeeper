package emcshop.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Queries the GitHub Commits API.
 * @author Michael Angstadt
 * @see "https://developer.github.com/v3/repos/commits/"
 */
public class GitHubCommitsApi {
	private final URI uri;

	/**
	 * @param author the username of the project owner
	 * @param project the project name
	 */
	public GitHubCommitsApi(String author, String project) {
		uri = URI.create("https://api.github.com/repos/" + author + "/" + project + "/commits");
	}

	/**
	 * Gets the latest commit date of a file.
	 * @param filePath the path of the file (relative to the project root)
	 * @return the commit date or null if no commits can be found
	 * @throws IOException if there's a problem querying the API
	 */
	public Date getDateOfLatestCommit(String filePath) throws IOException {
		String json = getCommits(filePath);
		JsonNode node = parseJson(json);

		if (node.isArray() && node.size() == 0) {
			return null;
		}

		String dateStr;
		try {
			dateStr = node.get(0).get("commit").get("author").get("date").asText();
		} catch (NullPointerException e) {
			throw new IOException("JSON response not recognized:\n" + json);
		}

		try {
			return parseDate(dateStr);
		} catch (ParseException e) {
			throw new IOException("Date not in recognizable format: " + dateStr);
		}
	}

	/**
	 * Gets the latest commits of a file. This method is package-private so it
	 * can be overridden in unit tests.
	 * @param filePath the path of the file (relative to the project root)
	 * @return the JSON response
	 * @throws IOException if there's a problem querying the API
	 */
	String getCommits(String filePath) throws IOException {
		URIBuilder builder = new URIBuilder(uri);
		builder.addParameter("path", filePath);

		CloseableHttpClient client = HttpClientBuilder.create().build();
		try {
			HttpGet request = new HttpGet(builder.build());
			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();
			return EntityUtils.toString(entity);
		} catch (URISyntaxException ignore) {
			throw new IOException(ignore);
		} finally {
			client.close();
		}
	}

	private JsonNode parseJson(String jsonStr) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readTree(jsonStr);
	}

	private Date parseDate(String dateStr) throws ParseException {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		return df.parse(dateStr);
	}
}
