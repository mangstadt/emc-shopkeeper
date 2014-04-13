package emcshop.presenter;

import static emcshop.util.GuiUtils.fireEvents;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
		EmcSession session = new EmcSession("", "", new Date());
		UpdateModelMock model = spy(new UpdateModelMock());

		IUpdateView view = mock(IUpdateView.class);
		when(view.getNewSession()).thenReturn(session);

		new UpdatePresenter(view, model);

		model.badSession();

		verify(model).setSession(session);
		verify(view).reset();
		verify(model, times(2)).startDownload();
	}

	@Test
	public void bad_session_login_canceled() {
		UpdateModelMock model = spy(new UpdateModelMock());

		IUpdateView view = mock(IUpdateView.class);
		when(view.getNewSession()).thenReturn(null);

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		model.badSession();

		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void download_error_saveTransactions() {
		Throwable thrown = new Throwable();
		UpdateModelMock model = spy(new UpdateModelMock());
		when(model.getDownloadError()).thenReturn(thrown);

		IUpdateView view = mock(IUpdateView.class);
		when(view.showDownloadError(thrown)).thenReturn(true);

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		model.downloadError();

		verify(model).saveTransactions();
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void download_error_discardTransactions() {
		Throwable thrown = new Throwable();
		UpdateModelMock model = spy(new UpdateModelMock());
		when(model.getDownloadError()).thenReturn(thrown);

		IUpdateView view = mock(IUpdateView.class);
		when(view.showDownloadError(thrown)).thenReturn(false);

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		model.downloadError();

		verify(model).discardTransactions();
		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void download_stopped() {
		IUpdateModel model = mock(IUpdateModel.class);

		UpdateViewMock view = spy(new UpdateViewMock());

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		view.stopDownload();

		verify(model).stopDownload();
		verify(model).saveTransactions();
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	@Test
	public void download_canceled() {
		IUpdateModel model = mock(IUpdateModel.class);

		UpdateViewMock view = spy(new UpdateViewMock());

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		view.cancelDownload();

		verify(model).stopDownload();
		verify(model).discardTransactions();
		verify(view).close();
		assertTrue(presenter.isCanceled());
	}

	@Test
	public void download_finished() {
		UpdateModelMock model = spy(new UpdateModelMock());

		IUpdateView view = mock(IUpdateView.class);

		UpdatePresenter presenter = new UpdatePresenter(view, model);

		model.pageDownloaded();
		model.pageDownloaded();
		model.pageDownloaded();
		model.downloadComplete();

		verify(view, times(3)).setPages(anyInt());
		verify(view, times(3)).setShopTransactions(anyInt());
		verify(view, times(3)).setPaymentTransactions(anyInt());
		verify(view, times(3)).setBonusFeeTransactions(anyInt());
		verify(view, times(3)).setOldestParsedTransactonDate(any(Date.class));
		verify(model).saveTransactions();
		verify(view).close();
		assertFalse(presenter.isCanceled());
	}

	private static class UpdateViewMock implements IUpdateView {
		private final List<ActionListener> stopDownload = new ArrayList<ActionListener>();
		private final List<ActionListener> cancelDownload = new ArrayList<ActionListener>();
		private final List<ActionListener> reportError = new ArrayList<ActionListener>();

		public void stopDownload() {
			fireEvents(stopDownload);
		}

		public void cancelDownload() {
			fireEvents(cancelDownload);
		}

		@Override
		public void addCancelListener(ActionListener listener) {
			cancelDownload.add(listener);
		}

		@Override
		public void addStopListener(ActionListener listener) {
			stopDownload.add(listener);
		}

		@Override
		public void addReportErrorListener(ActionListener listener) {
			reportError.add(listener);
		}

		@Override
		public EmcSession getNewSession() {
			return null;
		}

		@Override
		public boolean showDownloadError(Throwable thrown) {
			return false;
		}

		@Override
		public boolean getShowResults() {
			return false;
		}

		@Override
		public void setFirstUpdate(boolean firstUpdate) {
			//empty
		}

		@Override
		public void setEstimatedTime(Long estimatedTime) {
			//empty
		}

		@Override
		public void setStopAtPage(Integer stopAtPage) {
			//empty
		}

		@Override
		public void setOldestParsedTransactonDate(Date date) {
			//empty
		}

		@Override
		public void setPages(int pages) {
			//empty
		}

		@Override
		public void setShopTransactions(int count) {
			//empty
		}

		@Override
		public void setPaymentTransactions(int count) {
			//empty
		}

		@Override
		public void setBonusFeeTransactions(int count) {
			//empty
		}

		@Override
		public void reset() {
			//empty
		}

		@Override
		public void display() {
			//empty
		}

		@Override
		public void close() {
			//empty
		}
	}

	private static class UpdateModelMock implements IUpdateModel {
		private final List<ActionListener> pageDownloadedListeners = new ArrayList<ActionListener>();
		private final List<ActionListener> badSessionListeners = new ArrayList<ActionListener>();
		private final List<ActionListener> downloadErrorListeners = new ArrayList<ActionListener>();
		private final List<ActionListener> downloadCompleteListeners = new ArrayList<ActionListener>();

		public void badSession() {
			fireEvents(badSessionListeners);
		}

		public void downloadError() {
			fireEvents(downloadErrorListeners);
		}

		public void downloadComplete() {
			fireEvents(downloadCompleteListeners);
		}

		public void pageDownloaded() {
			fireEvents(pageDownloadedListeners);
		}

		@Override
		public void addPageDownloadedListener(ActionListener listener) {
			pageDownloadedListeners.add(listener);
		}

		@Override
		public void addBadSessionListener(ActionListener listener) {
			badSessionListeners.add(listener);
		}

		@Override
		public void addDownloadErrorListener(ActionListener listener) {
			downloadErrorListeners.add(listener);
		}

		@Override
		public void addDownloadCompleteListener(ActionListener listener) {
			downloadCompleteListeners.add(listener);
		}

		@Override
		public boolean isFirstUpdate() {
			return false;
		}

		@Override
		public Long getEstimatedTime() {
			return null;
		}

		@Override
		public Integer getStopAtPage() {
			return null;
		}

		@Override
		public void setSession(EmcSession session) {
			//empty
		}

		@Override
		public int getPagesDownloaded() {
			return 0;
		}

		@Override
		public int getShopTransactionsDownloaded() {
			return 0;
		}

		@Override
		public int getPaymentTransactionsDownloaded() {
			return 0;
		}

		@Override
		public int getBonusFeeTransactionsDownloaded() {
			return 0;
		}

		@Override
		public Date getOldestParsedTransactionDate() {
			return null;
		}

		@Override
		public Date getStarted() {
			return null;
		}

		@Override
		public long getTimeTaken() {
			return 0;
		}

		@Override
		public Integer getRupeeBalance() {
			return null;
		}

		@Override
		public Throwable getDownloadError() {
			return null;
		}

		@Override
		public Thread startDownload() {
			return null;
		}

		@Override
		public void stopDownload() {
			//empty
		}

		@Override
		public void saveTransactions() {
			//empty
		}

		@Override
		public void discardTransactions() {
			//empty
		}

		@Override
		public void reportError() {
			//empty
		}
	}
}
