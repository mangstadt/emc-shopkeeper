package emcshop.presenter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Test;

import com.github.mangstadt.emc.net.InvalidCredentialsException;
import com.github.mangstadt.emc.net.TwoFactorAuthException;

import emcshop.model.ILoginModel;
import emcshop.scraper.EmcSession;
import emcshop.view.ILoginView;

public class LoginPresenterTest {
	@Test
	public void constructor() {
		ILoginModel model = mock(ILoginModel.class);
		when(model.getSavedUsername()).thenReturn("username");
		when(model.getSavedPassword()).thenReturn("password");

		ILoginView view = mock(ILoginView.class);

		LoginPresenter presenter = new LoginPresenter(view, model);

		verify(view).setUsername("username");
		verify(view).setPassword("password");
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void network_error() throws Exception {
		IOException e = new IOException();
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(anyString(), anyString(), anyString())).thenThrow(e);

		ILoginView view = mock(ILoginView.class);

		LoginPresenter presenter = new LoginPresenter(view, model);
		presenter.onLogin();

		verify(model).logNetworkError(e);
		verify(view).networkError();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void bad_login() throws Exception {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(anyString(), anyString(), anyString())).thenThrow(new InvalidCredentialsException("", ""));

		ILoginView view = mock(ILoginView.class);

		LoginPresenter presenter = new LoginPresenter(view, model);
		presenter.onLogin();

		verify(view).badLogin();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void two_factor_auth_code_required() throws Exception {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(anyString(), anyString(), isNull(String.class))).thenThrow(new TwoFactorAuthException(""));

		ILoginView view = mock(ILoginView.class);
		when(view.getTwoFactorAuthCode()).thenReturn(null);

		LoginPresenter presenter = new LoginPresenter(view, model);
		presenter.onLogin();

		verify(view).twoFactorAuthCodeRequired();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void bad_two_factor_auth_code() throws Exception {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(anyString(), anyString(), eq("code"))).thenThrow(new TwoFactorAuthException(""));

		ILoginView view = mock(ILoginView.class);
		when(view.getTwoFactorAuthCode()).thenReturn("code");

		LoginPresenter presenter = new LoginPresenter(view, model);
		presenter.onLogin();

		verify(view).badTwoFactorAuthCode();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void valid_login_save_password() throws Exception {
		ILoginModel model = mock(ILoginModel.class);
		EmcSession session = new EmcSession("username", "password", new BasicCookieStore());
		when(model.login("username", "password", null)).thenReturn(session);

		ILoginView view = mock(ILoginView.class);
		when(view.getUsername()).thenReturn("username");
		when(view.getPassword()).thenReturn("password");
		when(view.getTwoFactorAuthCode()).thenReturn(null);
		when(view.getSavePassword()).thenReturn(true);

		LoginPresenter presenter = new LoginPresenter(view, model);
		presenter.onLogin();

		verify(model).saveSessionInfo("username", "password");
		verify(model).setSession(session);
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void valid_login_do_not_save_password() throws Exception {
		ILoginModel model = mock(ILoginModel.class);
		EmcSession session = new EmcSession("username", "password", new BasicCookieStore());
		when(model.login("username", "password", null)).thenReturn(session);

		ILoginView view = mock(ILoginView.class);
		when(view.getUsername()).thenReturn("username");
		when(view.getPassword()).thenReturn("password");
		when(view.getTwoFactorAuthCode()).thenReturn(null);
		when(view.getSavePassword()).thenReturn(false);

		LoginPresenter presenter = new LoginPresenter(view, model);
		presenter.onLogin();

		verify(model).saveSessionInfo("username", null);
		verify(model).setSession(session);
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void cancel_login() throws Exception {
		ILoginModel model = mock(ILoginModel.class);
		EmcSession session = new EmcSession("username", "password", new BasicCookieStore());
		when(model.login("username", "password", null)).thenReturn(session);

		ILoginView view = mock(ILoginView.class);
		when(view.getUsername()).thenReturn("username");
		when(view.getPassword()).thenReturn("password");
		when(view.getTwoFactorAuthCode()).thenReturn(null);
		when(view.getSavePassword()).thenReturn(true);

		LoginPresenter presenter = new LoginPresenter(view, model);
		presenter.onCancel();

		verify(model, never()).saveSessionInfo(anyString(), anyString());
		verify(model, never()).setSession(any(EmcSession.class));
		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void cancel_login_while_logging_in() throws Exception {
		ILoginModel model = mock(ILoginModel.class);
		stub(model.login(anyString(), anyString(), anyString())).toAnswer(invocation -> {
			Thread.sleep(300); //simulate network latency
			return new EmcSession("", "", new BasicCookieStore());
		});

		ILoginView view = mock(ILoginView.class);

		final LoginPresenter presenter = new LoginPresenter(view, model);

		Thread t = new Thread(presenter::onLogin);
		t.start();

		Thread.sleep(100);
		presenter.onCancel();
		t.join();

		verify(view).close();
		verify(model, never()).saveSessionInfo(anyString(), anyString());
		verify(model, never()).setSession(any(EmcSession.class));
		assertTrue(presenter.isCanceled());
	}
}
