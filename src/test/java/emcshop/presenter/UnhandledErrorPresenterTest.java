package emcshop.presenter;

import static emcshop.util.GuiUtils.fireEvents;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

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
		UnhandledErrorViewAdapter view = spy(new UnhandledErrorViewAdapter());

		IUnhandledErrorModel model = mock(IUnhandledErrorModel.class);

		new UnhandledErrorPresenter(view, model);
		view.clickSendErrorReport();

		verify(model).sendErrorReport();
		verify(view).errorReportSent();
	}

	@Test
	public void close_window() {
		UnhandledErrorViewAdapter view = spy(new UnhandledErrorViewAdapter());

		IUnhandledErrorModel model = mock(IUnhandledErrorModel.class);

		new UnhandledErrorPresenter(view, model);
		view.clickClose();

		verify(view).close();
	}

	private static class UnhandledErrorViewAdapter implements IUnhandledErrorView {
		private final List<ActionListener> onSendErrorReport = new ArrayList<ActionListener>();
		private final List<ActionListener> onClose = new ArrayList<ActionListener>();

		public void clickSendErrorReport() {
			fireEvents(onSendErrorReport);
		}

		public void clickClose() {
			fireEvents(onClose);
		}

		@Override
		public void addSendErrorReportListener(ActionListener listener) {
			onSendErrorReport.add(listener);
		}

		@Override
		public void addCloseListener(ActionListener listener) {
			onClose.add(listener);
		}

		@Override
		public void setMessage(String message) {
		}

		@Override
		public void setThrown(Throwable thrown) {
		}

		@Override
		public void errorReportSent() {
		}

		@Override
		public void display() {
		}

		@Override
		public void close() {
		}
	}
}
