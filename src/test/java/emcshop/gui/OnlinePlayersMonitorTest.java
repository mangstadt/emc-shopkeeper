package emcshop.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import emcshop.net.EmcServer;
import emcshop.net.EmcWebsiteConnection;

/**
 * @author Michael Angstadt
 */
public class OnlinePlayersMonitorTest {
	@Test
	public void getPlayerServer() throws Throwable {
		EmcWebsiteConnection connection = mock(EmcWebsiteConnection.class);
		when(connection.getOnlinePlayers(any(EmcServer.class))).thenReturn(Collections.<String> emptyList());
		when(connection.getOnlinePlayers(EmcServer.SMP1)).thenReturn(Arrays.asList("Notch"));
		when(connection.getOnlinePlayers(EmcServer.SMP5)).thenReturn(Arrays.asList("Jeb"));
		when(connection.getOnlinePlayers(EmcServer.UTOPIA)).thenReturn(Arrays.asList("Dinnerbone"));

		OnlinePlayersMonitor monitor = new OnlinePlayersMonitor(connection, 1000);

		for (String player : new String[] { "Notch", "Jeb", "Dinnerbone" }) {
			assertNull(monitor.getPlayerServer(player));
			assertNull(monitor.getPlayerServer(player.toUpperCase()));
		}

		Thread thread = monitor.start();
		Thread.sleep(500);
		thread.interrupt();

		assertEquals(EmcServer.SMP1, monitor.getPlayerServer("Notch"));
		assertEquals(EmcServer.SMP1, monitor.getPlayerServer("NOTCH"));
		assertEquals(EmcServer.SMP5, monitor.getPlayerServer("Jeb"));
		assertEquals(EmcServer.UTOPIA, monitor.getPlayerServer("Dinnerbone"));
		assertNull(monitor.getPlayerServer("Cupquake"));
	}
}
