package emcshop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class GitHubCommitsApiTest {
	@Test
	public void getDateOfLatestCommit() throws Exception {
		GitHubCommitsApi api = new GitHubCommitsApi("author", "project") {
			@Override
			String getCommits(String filePath) throws IOException {
				assertEquals("foo/bar.txt", filePath);

				try (InputStream in = GitHubCommitsApiTest.class.getResourceAsStream("github-commits-api-response.json")) {
					return IOUtils.toString(in);
				}
			}
		};

		Instant actual = api.getDateOfLatestCommit("foo/bar.txt");
		Instant expected = ZonedDateTime.of(2018, 5, 15, 15, 52, 59, 0, ZoneId.of("GMT")).toInstant();
		assertEquals(expected, actual);
	}

	@Test
	public void getDateOfLatestCommit_file_not_found() throws Exception {
		GitHubCommitsApi api = new GitHubCommitsApi("author", "project") {
			@Override
			String getCommits(String filePath) throws IOException {
				assertEquals("foo/bar.txt", filePath);
				return "[ ]";
			}
		};

		Instant actual = api.getDateOfLatestCommit("foo/bar.txt");
		assertNull(actual);
	}

	@Test(expected = IOException.class)
	public void getDateOfLatestCommit_bad_date() throws Exception {
		GitHubCommitsApi api = new GitHubCommitsApi("author", "project") {
			@Override
			String getCommits(String filePath) throws IOException {
				assertEquals("foo/bar.txt", filePath);

				//@formatter:off
				return
				"[" +
				"  {" +
				"    \"commit\": {" +
				"      \"author\": {" +
				"        \"date\": \"not a date\"" +
				"      }" +
				"    }" +
				"  }" +
				"]";
				//@formatter:on
			}
		};

		api.getDateOfLatestCommit("foo/bar.txt");
	}

	@Test(expected = IOException.class)
	public void getDateOfLatestCommit_different_kind_of_empty_response() throws Exception {
		GitHubCommitsApi api = new GitHubCommitsApi("author", "project") {
			@Override
			String getCommits(String filePath) throws IOException {
				assertEquals("foo/bar.txt", filePath);
				return "\"\"";
			}
		};

		api.getDateOfLatestCommit("foo/bar.txt");
	}

	@Test(expected = IOException.class)
	public void getDateOfLatestCommit_json_fields_not_found() throws Exception {
		GitHubCommitsApi api = new GitHubCommitsApi("author", "project") {
			@Override
			String getCommits(String filePath) throws IOException {
				assertEquals("foo/bar.txt", filePath);
				return "[ { \"some\" : \"other JSON file\" } ]";
			}
		};

		api.getDateOfLatestCommit("foo/bar.txt");
	}

	@Test(expected = IOException.class)
	public void getDateOfLatestCommit_invalid_json() throws Exception {
		GitHubCommitsApi api = new GitHubCommitsApi("author", "project") {
			@Override
			String getCommits(String filePath) throws IOException {
				assertEquals("foo/bar.txt", filePath);
				return "not JSON";
			}
		};

		api.getDateOfLatestCommit("foo/bar.txt");
	}
}
