package emcshop.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;

import emcshop.scraper.EMCServer;
import emcshop.scraper.OnlinePlayersScraper;

public class OnlinePlayersMonitorTest {
	@Test
	public void getPlayerServer() throws Throwable {
		Map<String, EMCServer> players = new HashMap<String, EMCServer>();
		players.put("Notch", EMCServer.SMP1);
		players.put("Jeb", EMCServer.SMP5);
		players.put("Dinnebone", EMCServer.UTOPIA);

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

		for (Map.Entry<String, EMCServer> entry : players.entrySet()) {
			String player = entry.getKey();

			EMCServer expected = entry.getValue();
			EMCServer actual = monitor.getPlayerServer(player);
			assertEquals(expected, actual);
		}
		assertNull(monitor.getPlayerServer("Cupquake"));
	}
}
