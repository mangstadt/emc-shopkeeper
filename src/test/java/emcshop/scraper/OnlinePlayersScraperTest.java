package emcshop.scraper;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class OnlinePlayersScraperTest {
	@Test
	public void getOnlinePlayers() throws Throwable {
		HttpClient client = createMockClient();
		OnlinePlayersScraper scraper = new OnlinePlayersScraper(client);
		Set<String> actual = scraper.getOnlinePlayers();

		Set<String> expected = new HashSet<String>();
		expected.add("Multiple");
		expected.add("Names");
		for (int i = 4; i <= 10; i++) {
			expected.add("Test" + i);
		}
		assertEquals(expected, actual);

		//10 requests should have been sent (for the 10 servers)
		verify(client, times(10)).execute(Mockito.any(HttpGet.class));
	}

	private static HttpClient createMockClient() throws Throwable {
		HttpClient client = mock(HttpClient.class);
		when(client.execute(Mockito.any(HttpGet.class))).then(new Answer<HttpResponse>() {
			private final Pattern p = Pattern.compile("http://empireminecraft.com/api/server-online-(\\d+)\\.json");

			@Override
			public HttpResponse answer(InvocationOnMock invocation) throws Throwable {
				HttpGet request = (HttpGet) invocation.getArguments()[0];
				String uri = request.getURI().toString();

				Matcher m = p.matcher(uri);
				if (!m.find()) {
					fail();
				}

				int server = Integer.parseInt(m.group(1));
				if (server < 1 || server > 10) {
					fail();
				}

				String responseStr;
				if (server == 1) {
					responseStr = "[{\"group\":\"1\",\"name\":\"Multiple\",\"start\":\"1396985706\"}],[{\"group\":\"1\",\"name\":\"Names\",\"start\":\"1396985706\"}]";
				} else if (server == 2) {
					responseStr = "[]";
				} else if (server == 3) {
					responseStr = "invalid-JSON";
				} else {
					responseStr = "[{\"group\":\"1\",\"name\":\"Test" + server + "\",\"start\":\"1396985706\"}]";
				}

				HttpResponse response = mock(HttpResponse.class);

				HttpEntity entity = mock(HttpEntity.class);
				when(entity.getContent()).thenReturn(new ByteArrayInputStream(responseStr.getBytes()));
				when(entity.getContentLength()).thenReturn((long) responseStr.length());
				when(response.getEntity()).thenReturn(entity);

				return response;
			}
		});
		return client;
	}
}
