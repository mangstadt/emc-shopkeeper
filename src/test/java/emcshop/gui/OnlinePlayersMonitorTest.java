package emcshop.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;

import emcshop.scraper.EmcServer;
import emcshop.scraper.OnlinePlayersScraper;

public class OnlinePlayersMonitorTest {
	@Test
	public void getPlayerServer() throws Throwable {
		Map<String, EmcServer> players = new HashMap<String, EmcServer>();
		players.put("Notch", EmcServer.SMP1);
		players.put("Jeb", EmcServer.SMP5);
		players.put("Dinnebone", EmcServer.UTOPIA);

		OnlinePlayersScraper scraper = Mockito.mock(OnlinePlayersScraper.class);
		when(scraper.getOnlinePlayers()).thenReturn(players);

		OnlinePlayersMonitor monitor = new OnlinePlayersMonitor(scraper, 1000);

		for (String player : players.keySet()) {
			assertNull(monitor.getPlayerServer(player));
			assertNull(monitor.getPlayerServer(player.toUpperCase()));
		}

		Thread thread = monitor.start();
		Thread.sleep(500);
		thread.interrupt();

		for (Map.Entry<String, EmcServer> entry : players.entrySet()) {
			String player = entry.getKey();

			EmcServer expected = entry.getValue();
			EmcServer actual = monitor.getPlayerServer(player);
			assertEquals(expected, actual);
		}
		assertNull(monitor.getPlayerServer("Cupquake"));
	}
}
