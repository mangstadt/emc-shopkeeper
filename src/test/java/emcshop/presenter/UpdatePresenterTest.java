package emcshop.presenter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.time.LocalDateTime;

import org.junit.Test;

import emcshop.model.IUpdateModel;
import emcshop.scraper.EmcSession;
import emcshop.view.IUpdateView;

public class UpdatePresenterTest {
	@Test
	public void init() {
		IUpdateModel model = mock(IUpdateModel.class);
		when(model.isFirstUpdate()).thenReturn(true);
		when(model.getEstimatedTime()).thenReturn(123L);
		when(model.getStopAtPage()).thenReturn(3000);

		IUpdateView view = mock(IUpdateView.class);

		new UpdatePresenter(view, model);

		verify(view).addCancelListener(any(ActionListener.class));
		verify(view).addStopListener(any(ActionListener.class));
		verify(view).setFirstUpdate(true);
		verify(view).setEstimatedTime(123L);
		verify(view).setStopAtPage(3000);
		verify(model).addBadSessionListener(any(ActionListener.class));
		verify(model).addDownloadCompleteListener(any(ActionListener.class));
		verify(model).addDownloadErrorListener(any(ActionListener.class));
		verify(model).addPageDownloadedListener(any(ActionListener.class));
		verify(model).startDownload();
		verify(view).display();
	}

	@Test
	public void bad_session() {
		EmcSession session = mock(EmcSession.class);
		IUpdateModel model = mock(IUpdateModel.class);
		ListenerAnswer badSession = new ListenerAnswer();
		doAnswer(badSession).when(model).addBadSessionListener(any(ActionListener.class));

		IUpdateView view = mock(IUpdateView.class);
		when(view.getNewSession()).thenReturn(session);

		new UpdatePresenter(view, model);

		badSession.fire();

		verify(model).setSession(session);
		verify(view).reset();
		verify(model, times(2)).startDownload();
	}

	@Test
	public void bad_session_login_canceled() {
		IUpdateModel model = mock(IUpdateModel.class);
		ListenerAnswer badSession = new ListenerAnswer();
		doAnswer(badSession).when(model).addBadSessionListener(any(ActionListener.class));

		IUpdateView view = mock(IUpdateView.class);
		when(view.getNewSession()).thenReturn(null);

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		badSession.fire();

		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void download_error_saveTransactions() {
		IUpdateModel model = mock(IUpdateModel.class);
		ListenerAnswer downloadError = new ListenerAnswer();
		doAnswer(downloadError).when(model).addDownloadErrorListener(any(ActionListener.class));
		Throwable thrown = new Throwable();
		when(model.getDownloadError()).thenReturn(thrown);

		IUpdateView view = mock(IUpdateView.class);
		when(view.showDownloadError(thrown)).thenReturn(true);

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		downloadError.fire();

		verify(model).saveTransactions();
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void download_error_discardTransactions() {
		IUpdateModel model = mock(IUpdateModel.class);
		ListenerAnswer downloadError = new ListenerAnswer();
		doAnswer(downloadError).when(model).addDownloadErrorListener(any(ActionListener.class));
		Throwable thrown = new Throwable();
		when(model.getDownloadError()).thenReturn(thrown);

		IUpdateView view = mock(IUpdateView.class);
		when(view.showDownloadError(thrown)).thenReturn(false);

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		downloadError.fire();

		verify(model).discardTransactions();
		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void download_stopped() {
		IUpdateModel model = mock(IUpdateModel.class);

		IUpdateView view = mock(IUpdateView.class);
		ListenerAnswer stopDownload = new ListenerAnswer();
		doAnswer(stopDownload).when(view).addStopListener(any(ActionListener.class));

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		stopDownload.fire();

		verify(model).stopDownload();
		verify(model).saveTransactions();
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void download_canceled() {
		IUpdateModel model = mock(IUpdateModel.class);

		IUpdateView view = mock(IUpdateView.class);
		ListenerAnswer cancelDownload = new ListenerAnswer();
		doAnswer(cancelDownload).when(view).addCancelListener(any(ActionListener.class));

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		cancelDownload.fire();

		verify(model).stopDownload();
		verify(model).discardTransactions();
		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void download_finished() {
		IUpdateModel model = mock(IUpdateModel.class);
		ListenerAnswer pageDownloaded = new ListenerAnswer();
		doAnswer(pageDownloaded).when(model).addPageDownloadedListener(any(ActionListener.class));
		ListenerAnswer downloadComplete = new ListenerAnswer();
		doAnswer(downloadComplete).when(model).addDownloadCompleteListener(any(ActionListener.class));

		IUpdateView view = mock(IUpdateView.class);

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		pageDownloaded.fire();
		pageDownloaded.fire();
		pageDownloaded.fire();
		downloadComplete.fire();

		verify(view, times(3)).setPages(anyInt());
		verify(view, times(3)).setShopTransactions(anyInt());
		verify(view, times(3)).setPaymentTransactions(anyInt());
		verify(view, times(3)).setBonusFeeTransactions(anyInt());
		verify(view, times(3)).setOldestParsedTransactonDate(any(LocalDateTime.class));
		verify(model).saveTransactions();
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}
}
