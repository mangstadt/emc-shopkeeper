package emcshop.presenter;

import static emcshop.util.GuiUtils.fireEvents;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import emcshop.model.ILoginModel;
import emcshop.scraper.EmcSession;
import emcshop.view.ILoginView;

public class LoginPresenterTest {
	@Test
	public void no_saved_session() {
		ILoginModel model = mock(ILoginModel.class);
		when(model.getSavedUsername()).thenReturn(null);
		when(model.getSavedRememberMe()).thenReturn(false);

		ILoginView view = mock(ILoginView.class);

		LoginPresenter presenter = new LoginPresenter(view, model);

		verify(view).setUsername(null);
		verify(view).setRememberMe(false);
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void saved_session() {
		ILoginModel model = mock(ILoginModel.class);
		when(model.getSavedUsername()).thenReturn("Notch");
		when(model.getSavedRememberMe()).thenReturn(true);

		ILoginView view = mock(ILoginView.class);

		LoginPresenter presenter = new LoginPresenter(view, model);

		verify(view).setUsername("Notch");
		verify(view).setRememberMe(true);
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void network_error() throws Throwable {
		IOException e = new IOException();
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean())).thenThrow(e);

		LoginViewAdapter view = spy(new LoginViewAdapter());

		LoginPresenter presenter = new LoginPresenter(view, model);

		view.clickLogin();

		verify(model).logNetworkError(e);
		verify(view).networkError();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void bad_login() throws Throwable {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

		LoginViewAdapter view = spy(new LoginViewAdapter());

		LoginPresenter presenter = new LoginPresenter(view, model);

		view.clickLogin();

		verify(view).badLogin();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void valid_login() throws Throwable {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean())).thenReturn("token");

		LoginViewAdapter view = spy(new LoginViewAdapter());

		LoginPresenter presenter = new LoginPresenter(view, model);

		view.clickLogin();

		verify(model).saveSession(any(EmcSession.class), Mockito.anyBoolean());
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void cancel_login() throws Throwable {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean())).thenReturn("token");

		LoginViewAdapter view = spy(new LoginViewAdapter());

		LoginPresenter presenter = new LoginPresenter(view, model);

		view.clickCancel();

		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void cancel_login_while_logging_in() throws Throwable {
		ILoginModel model = mock(ILoginModel.class);
		stub(model.login(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean())).toAnswer(new Answer<String>() {
			@Override
			public String answer(InvocationOnMock invocation) throws Throwable {
				Thread.sleep(300); //simulate network latency
				return "token";
			}
		});

		final LoginViewAdapter view = spy(new LoginViewAdapter());

		LoginPresenter presenter = new LoginPresenter(view, model);

		Thread t = new Thread() {
			@Override
			public void run() {
				view.clickLogin();
			}
		};
		t.start();

		Thread.sleep(100);
		view.clickCancel();
		t.join();

		verify(view).close();
		verify(model, never()).saveSession(any(EmcSession.class), any(boolean.class));
		assertTrue(presenter.isCanceled());
	}

	private static class LoginViewAdapter implements ILoginView {
		private String username = "", password = "";
		private boolean rememberMe;

		private final List<ActionListener> onLogin = new ArrayList<ActionListener>();
		private final List<ActionListener> onCancel = new ArrayList<ActionListener>();

		public void clickLogin() {
			fireEvents(onLogin);
		}

		public void clickCancel() {
			fireEvents(onCancel);
		}

		@Override
		public void addOnLoginListener(ActionListener listener) {
			onLogin.add(listener);
		}

		@Override
		public void addOnCancelListener(ActionListener listener) {
			onCancel.add(listener);
		}

		@Override
		public void setUsername(String username) {
			this.username = username;
		}

		@Override
		public String getUsername() {
			return username;
		}

		@Override
		public String getPassword() {
			return password;
		}

		@Override
		public void setRememberMe(boolean rememberMe) {
			this.rememberMe = rememberMe;
		}

		@Override
		public boolean getRememberMe() {
			return rememberMe;
		}

		@Override
		public void networkError() {
		}

		@Override
		public void badLogin() {
		}

		@Override
		public void close() {
		}

		@Override
		public void display() {
		}
	}
}
