package emcshop.scraper;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
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
		Map<String, EmcServer> actual = scraper.getOnlinePlayers();

		Map<String, EmcServer> expected = new HashMap<String, EmcServer>();
		expected.put("Notch", EmcServer.SMP1);
		expected.put("Jeb", EmcServer.SMP1);
		expected.put("Dinnerbone", EmcServer.SMP2);
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
					responseStr = "[{\"group\":\"1\",\"name\":\"Notch\",\"start\":\"1396985706\"},{\"group\":\"1\",\"name\":\"Jeb\",\"start\":\"1396985706\"}]";
				} else if (server == 2) {
					responseStr = "[{\"group\":\"1\",\"name\":\"Dinnerbone\",\"start\":\"1396985706\"}]";
				} else if (server == 3) {
					responseStr = "invalid-JSON";
				} else {
					responseStr = "[]";
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
