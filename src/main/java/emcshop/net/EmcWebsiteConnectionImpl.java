package emcshop.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.SocketConfig;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.UrlEscapers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Represents a connection to the EmpireMinecraft website.
 *
 * @author Michael Angstadt
 */
public class EmcWebsiteConnectionImpl implements EmcWebsiteConnection {
    private static final Map<EmcServer, Integer> serverNumbers;

    static {
        ImmutableMap.Builder<EmcServer, Integer> map = ImmutableMap.builder();
        map.put(EmcServer.SMP1, 1);
        map.put(EmcServer.SMP2, 2);
        map.put(EmcServer.SMP3, 4);
        map.put(EmcServer.SMP4, 5);
        map.put(EmcServer.SMP5, 6);
        map.put(EmcServer.SMP6, 7);
        map.put(EmcServer.SMP7, 8);
        map.put(EmcServer.SMP8, 9);
        map.put(EmcServer.SMP9, 10);
        map.put(EmcServer.UTOPIA, 3);
        serverNumbers = map.build();
    }

    private final CloseableHttpClient client;
    private final CookieStore cookieStore;

    /**
     * Creates an unauthenticated connection.
     */
    public EmcWebsiteConnectionImpl() {
        this.cookieStore = new BasicCookieStore();
        this.client = createClient();
    }

    /**
     * Creates a new HTTP connection using an existing cookie store.
     *
     * @param cookieStore the cookie store
     */
    public EmcWebsiteConnectionImpl(CookieStore cookieStore) {
        this.cookieStore = copyOf(cookieStore);
        this.client = createClient();
    }

    /**
     * Creates an authenticated connection.
     *
     * @param username the user's username
     * @param password the user's password
     * @throws InvalidCredentialsException if the username/password is incorrect
     * @throws IOException
     */
    public EmcWebsiteConnectionImpl(String username, String password) throws IOException {
        this();

        //load the home page in order to get the initial session cookie
        loadHomePage();

        //log the user in
        if (!login(username, password)) {
            throw new InvalidCredentialsException(username, password);
        }
    }

    private CloseableHttpClient createClient() {
        int connectionTimeout = (int) TimeUnit.MINUTES.toMillis(10);

        //@formatter:off
        return HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(connectionTimeout)
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(connectionTimeout)
                        .build())
                .setUserAgent("EMC Rupee Transaction Downloader")
                .build();
        //@formatter:on
    }

    private static CookieStore copyOf(CookieStore original) {
        CookieStore copy = new BasicCookieStore();
        for (Cookie cookie : original.getCookies()) {
            copy.addCookie(cookie);
        }
        return copy;
    }

    @Override
    public CookieStore getCookieStore() {
        return cookieStore;
    }

    @Override
    public HttpClient getHttpClient() {
        return client;
    }

    @Override
    public Document getRupeeTransactionPage(int pageNumber) throws IOException {
        /*
         * Note: The HttpClient library is used here because using
		 * "Jsoup.connect()" doesn't always work when the application is run as
		 * a Web Start app.
		 *
		 * The login dialog was repeatedly appearing because, even though the
		 * login was successful (a valid session cookie was generated), the
		 * TransactionPuller would fail when it tried to get the first
		 * transaction from the first page (i.e. when calling "isLoggedIn()").
		 * It was failing because it was getting back the unauthenticated
		 * version of the rupee page. It was as if jsoup wasn't sending the
		 * session cookie with the request.
		 *
		 * The issue appeared to only occur when running under Web Start. It
		 * could not be reproduced when running via Eclipse.
		 */

        String base = "https://empireminecraft.com/rupees/transactions/";
        String url = base + "?page=" + pageNumber;

        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        InputStream in = entity.getContent();
        try {
            return Jsoup.parse(in, "UTF-8", base);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    @Override
    public Document getProfilePage(String playerName) throws IOException {
        String url = "https://u.emc.gs/" + UrlEscapers.urlPathSegmentEscaper().escape(playerName);
        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        InputStream in = entity.getContent();
        try {
            return Jsoup.parse(in, "UTF-8", "https://empireminecraft.com");
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    @Override
    public List<String> getOnlinePlayers(EmcServer server) throws IOException {
        Integer serverNumber = serverNumbers.get(server);
        String url = "https://empireminecraft.com/api/server-online-" + serverNumber + ".json";
        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);

        JsonElement root;
        HttpEntity entity = response.getEntity();
        Reader reader = new InputStreamReader(entity.getContent());
        try {
            root = new JsonParser().parse(reader);
        } catch (JsonParseException e) {
            throw new IOException(e);
        } finally {
            IOUtils.closeQuietly(reader);
        }

        try {
            JsonArray array = root.getAsJsonArray();
            List<String> players = new ArrayList<String>(array.size());
            for (JsonElement element : array) {
                JsonObject player = element.getAsJsonObject();
                JsonElement name = player.get("name");
                if (name != null) {
                    players.add(name.getAsString());
                }
            }
            return players;
        } catch (IllegalStateException e) {
			/*
			 * Thrown if the JSON is not structured as expected (e.g. if there's
			 * an array where an object should be).
			 */
            throw new IOException(e);
        }
    }

    private void loadHomePage() throws IOException {
        String url = "https://empireminecraft.com";
        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        EntityUtils.consume(entity);
    }

    private boolean login(String username, String password) throws IOException {
        String url = "https://empireminecraft.com/login/login";
        HttpPost request = new HttpPost(url);

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("login", username));
        params.add(new BasicNameValuePair("password", password));
        params.add(new BasicNameValuePair("cookie_check", "1"));
        request.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));

        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        EntityUtils.consume(entity);

        //client is redirected to the homepage on successful login
        return response.getStatusLine().getStatusCode() == 303;
    }

    @Override
    public void close() throws IOException {
        //TODO logout in order to invalidate the token
        client.close();
    }
}