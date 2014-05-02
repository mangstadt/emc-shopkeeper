package emcshop.presenter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.io.IOException;

import org.junit.Test;
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
		when(model.login(anyString(), anyString(), anyBoolean())).thenThrow(e);

		ILoginView view = mock(ILoginView.class);
		ListenerAnswer clickLogin = new ListenerAnswer();
		doAnswer(clickLogin).when(view).addOnLoginListener(any(ActionListener.class));

		LoginPresenter presenter = new LoginPresenter(view, model);

		clickLogin.fire();

		verify(model).logNetworkError(e);
		verify(view).networkError();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void bad_login() throws Throwable {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(anyString(), anyString(), anyBoolean())).thenReturn(null);

		ILoginView view = mock(ILoginView.class);
		ListenerAnswer clickLogin = new ListenerAnswer();
		doAnswer(clickLogin).when(view).addOnLoginListener(any(ActionListener.class));

		LoginPresenter presenter = new LoginPresenter(view, model);

		clickLogin.fire();

		verify(view).badLogin();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void valid_login() throws Throwable {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(anyString(), anyString(), anyBoolean())).thenReturn("token");

		ILoginView view = mock(ILoginView.class);
		ListenerAnswer clickLogin = new ListenerAnswer();
		doAnswer(clickLogin).when(view).addOnLoginListener(any(ActionListener.class));

		LoginPresenter presenter = new LoginPresenter(view, model);

		clickLogin.fire();

		verify(model).saveSession(any(EmcSession.class), anyBoolean());
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void cancel_login() throws Throwable {
		ILoginModel model = mock(ILoginModel.class);
		when(model.login(anyString(), anyString(), anyBoolean())).thenReturn("token");

		ILoginView view = mock(ILoginView.class);
		ListenerAnswer clickCancel = new ListenerAnswer();
		doAnswer(clickCancel).when(view).addOnCancelListener(any(ActionListener.class));

		LoginPresenter presenter = new LoginPresenter(view, model);

		clickCancel.fire();

		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void cancel_login_while_logging_in() throws Throwable {
		ILoginModel model = mock(ILoginModel.class);
		stub(model.login(anyString(), anyString(), anyBoolean())).toAnswer(new Answer<String>() {
			@Override
			public String answer(InvocationOnMock invocation) throws Throwable {
				Thread.sleep(300); //simulate network latency
				return "token";
			}
		});

		ILoginView view = mock(ILoginView.class);
		ListenerAnswer clickCancel = new ListenerAnswer();
		doAnswer(clickCancel).when(view).addOnCancelListener(any(ActionListener.class));
		final ListenerAnswer clickLogin = new ListenerAnswer();
		doAnswer(clickLogin).when(view).addOnLoginListener(any(ActionListener.class));

		LoginPresenter presenter = new LoginPresenter(view, model);

		Thread t = new Thread() {
			@Override
			public void run() {
				clickLogin.fire();
			}
		};
		t.start();

		Thread.sleep(100);
		clickCancel.fire();
		t.join();

		verify(view).close();
		verify(model, never()).saveSession(any(EmcSession.class), any(boolean.class));
		assertTrue(presenter.isCanceled());
	}
}
