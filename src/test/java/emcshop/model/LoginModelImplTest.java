package emcshop.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import emcshop.AppContext;
import emcshop.Settings;

public class LoginModelImplTest {
	@Test
	public void getSavedUsername() {
		Settings settings = mock(Settings.class);
		when(settings.getUsername()).thenReturn("Notch");
		AppContext.init(settings);

		LoginModelImpl model = new LoginModelImpl();
		assertEquals("Notch", model.getSavedUsername());
	}

	@Test
	public void getSavedPassword() {
		Settings settings = mock(Settings.class);
		when(settings.getPassword()).thenReturn("password");
		AppContext.init(settings);

		LoginModelImpl model = new LoginModelImpl();
		assertEquals("password", model.getSavedPassword());
	}

	@Test
	public void getSavedUsername_none() {
		Settings settings = mock(Settings.class);
		when(settings.getUsername()).thenReturn(null);
		AppContext.init(settings);

		LoginModelImpl model = new LoginModelImpl();
		assertNull(model.getSavedUsername());
	}

	@Test
	public void getSavedPassword_none() {
		Settings settings = mock(Settings.class);
		when(settings.getPassword()).thenReturn(null);
		AppContext.init(settings);

		LoginModelImpl model = new LoginModelImpl();
		assertNull(model.getSavedPassword());
	}

	@Test
	public void saveSessionInfo() {
		Settings settings = mock(Settings.class);
		AppContext.init(settings);

		LoginModelImpl model = new LoginModelImpl();
		model.saveSessionInfo("Notch", "password");

		verify(settings).setUsername("Notch");
		verify(settings).setPassword("password");
		verify(settings).save();
	}
}
