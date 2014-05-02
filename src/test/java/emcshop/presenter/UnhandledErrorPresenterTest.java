package emcshop.presenter;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;

import org.junit.Test;

import emcshop.model.IUnhandledErrorModel;
import emcshop.view.IUnhandledErrorView;

public class UnhandledErrorPresenterTest {
	@Test
	public void init() {
		IUnhandledErrorView view = mock(IUnhandledErrorView.class);

		IUnhandledErrorModel model = mock(IUnhandledErrorModel.class);
		when(model.getMessage()).thenReturn("Message");
		Throwable thrown = new Throwable();
		when(model.getThrown()).thenReturn(thrown);

		new UnhandledErrorPresenter(view, model);

		verify(view).setMessage("Message");
		verify(view).setThrown(thrown);
		verify(model).logError();
		verify(view).display();
	}

	@Test
	public void send_error_report() {
		IUnhandledErrorView view = mock(IUnhandledErrorView.class);
		ListenerAnswer clickSendErrorReport = new ListenerAnswer();
		doAnswer(clickSendErrorReport).when(view).addSendErrorReportListener(any(ActionListener.class));

		IUnhandledErrorModel model = mock(IUnhandledErrorModel.class);

		new UnhandledErrorPresenter(view, model);
		clickSendErrorReport.fire();

		verify(model).sendErrorReport();
		verify(view).errorReportSent();
	}

	@Test
	public void close_window() {
		IUnhandledErrorView view = mock(IUnhandledErrorView.class);
		ListenerAnswer clickClose = new ListenerAnswer();
		doAnswer(clickClose).when(view).addCloseListener(any(ActionListener.class));

		IUnhandledErrorModel model = mock(IUnhandledErrorModel.class);

		new UnhandledErrorPresenter(view, model);
		clickClose.fire();

		verify(view).close();
	}
}
