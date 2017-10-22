package emcshop.net;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.jsoup.nodes.Document;

/**
 * Represents a connection to the EmpireMinecraft website.
 *
 * @author Michael Angstadt
 */
public interface EmcWebsiteConnection extends Closeable {
    /**
     * Downloads a rupee history transaction page.
     *
     * @param pageNumber the page number
     * @return the HTML page
     * @throws IOException
     */
    Document getRupeeTransactionPage(int pageNumber) throws IOException;

    /**
     * Downloads a player's profile page.
     *
     * @param playerName the player name
     * @return the HTML page
     * @throws IOException
     */
    Document getProfilePage(String playerName) throws IOException;

    /**
     * Gets the list of players that are logged into a server.
     *
     * @param the EMC server
     * @return the players
     * @throws IOException
     */
    List<String> getOnlinePlayers(EmcServer server) throws IOException;

    /**
     * Gets the cookies that are associated with this connection.
     *
     * @return the cookies
     */
    CookieStore getCookieStore();

    /**
     * Gets the wrapped HTTP connection object.
     *
     * @return the HTTP connection
     */
    HttpClient getHttpClient();
}