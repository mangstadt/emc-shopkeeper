package emcshop.util;

import org.apache.http.client.HttpClient;

public interface HttpClientFactory {
	HttpClient create();
}
