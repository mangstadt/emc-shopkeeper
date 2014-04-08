package emcshop.gui;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

import emcshop.gui.OnlinePlayersMonitor;
import emcshop.scraper.OnlinePlayersScraper;

public class OnlinePlayersMonitorTest {
	@Test
	public void isPlayerOnline() throws Throwable {
		List<String> players = Arrays.asList("Notch", "Jeb", "Dinnerbone");
		OnlinePlayersScraper scraper = Mockito.mock(OnlinePlayersScraper.class);
		when(scraper.getOnlinePlayers()).thenReturn(new HashSet<String>(players));

		OnlinePlayersMonitor monitor = new OnlinePlayersMonitor(scraper, 1000);

		for (String player : players) {
			assertFalse(monitor.isPlayerOnline(player));
			assertFalse(monitor.isPlayerOnline(player.toUpperCase()));
		}

		Thread thread = monitor.start();
		Thread.sleep(500);
		thread.interrupt();

		for (String player : players) {
			assertTrue(monitor.isPlayerOnline(player));
			assertTrue(monitor.isPlayerOnline(player.toUpperCase()));
		}
		assertFalse(monitor.isPlayerOnline("Cupquake"));
	}
}
