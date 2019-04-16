package emcshop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

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

				InputStream in = GitHubCommitsApiTest.class.getResourceAsStream("github-commits-api-response.json");
				try {
					return IOUtils.toString(in);
				} finally {
					IOUtils.closeQuietly(in);
				}
			}
		};

		Date actual = api.getDateOfLatestCommit("foo/bar.txt");
		Date expected = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z").parse("5/15/2018 15:52:59 GMT");
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

		Date actual = api.getDateOfLatestCommit("foo/bar.txt");
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