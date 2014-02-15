package emcshop.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Test;

import emcshop.scraper.EmcSession;
import emcshop.util.Settings;

public class LoginModelImplTest {
	@Test
	public void getSavedUsername() {
		Settings settings = mock(Settings.class);
		when(settings.getSession()).thenReturn(new EmcSession("Notch", "token", new Date()));

		LoginModelImpl model = new LoginModelImpl(settings);
		assertEquals("Notch", model.getSavedUsername());
	}

	@Test
	public void getSavedUsername_none() {
		Settings settings = mock(Settings.class);
		when(settings.getSession()).thenReturn(null);

		LoginModelImpl model = new LoginModelImpl(settings);
		assertNull(model.getSavedUsername());
	}

	@Test
	public void getSavedRememberMe() {
		Settings settings = mock(Settings.class);
		when(settings.isPersistSession()).thenReturn(true);

		LoginModelImpl model = new LoginModelImpl(settings);
		assertTrue(model.getSavedRememberMe());
	}

	@Test
	public void saveSession() {
		Settings settings = mock(Settings.class);

		LoginModelImpl model = new LoginModelImpl(settings);
		EmcSession session = new EmcSession("Notch", "token", new Date());
		model.saveSession(session, true);

		verify(settings).setSession(session);
		verify(settings).setPersistSession(true);
		verify(settings).save();
	}
}
