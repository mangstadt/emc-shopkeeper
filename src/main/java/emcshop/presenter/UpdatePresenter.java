package emcshop.presenter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import emcshop.model.IUpdateModel;
import emcshop.scraper.EmcSession;
import emcshop.view.IUpdateView;

public class UpdatePresenter {
	private final IUpdateView view;
	private final IUpdateModel model;

	public UpdatePresenter(IUpdateView view, IUpdateModel model) {
		this.view = view;
		this.model = model;

		view.addCancelListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onCancel();
			}
		});

		view.addStopListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onStop();
			}
		});

		model.addPageDownloadedListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onPageDownloaded();
			}
		});

		model.addBadSessionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onBadSession();
			}
		});

		model.addDownloadErrorListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onDownloadError();
			}
		});

		model.addDownloadCompleteListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onDownloadComplete();
			}
		});

		//		if (model.getSession() == null) {
		//			//LoginPresenter p = LoginPresenter.show(owner, settings); can't unit test because it will show an actual dialog
		//			//EmcSession session = view.showLogin(); //the view impl will 
		//			if (session == null) {
		//				view.close();
		//				return;
		//			}
		//			model.setSession(session);
		//		}
		//
		//		if (model.isFirstUpdate()) {
		//			IFirstUpdateView firstUpdateView = view.showFirstUpdate();
		//			if (firstUpdateView == null) {
		//				view.close();
		//				return;
		//			}
		//
		//			model.setMaxPaymentTransactionAge(firstUpdateView.getMaxPaymentTransactionAge());
		//			model.setStopAtPage(firstUpdateView.getStopAtPage());
		//		}

		view.setFirstUpdate(model.isFirstUpdate());
		view.setEstimatedTime(model.getEstimatedTime());
		view.setStopAtPage(model.getStopAtPage());
		view.display();
		model.startDownload();
	}

	private void onBadSession() {
		EmcSession session = view.getNewLoginCredentials();
		if (session == null) {
			view.close();
			return;
		}
		model.setSession(session);

		view.reset();
		model.startDownload();
	}

	private void onCancel() {
		model.stopDownload();
		model.discardTransactions();
		view.close();
	}

	private void onStop() {
		model.stopDownload();
		model.saveTransactions();
		view.close();
	}

	private void onPageDownloaded() {
		view.setPages(model.getPagesDownloaded());
		view.setShopTransactions(model.getShopTransactionsDownloaded());
		view.setPaymentTransactions(model.getPaymentTransactionsDownloaded());
		view.setBonusFeeTransactions(model.getBonusFeeTransactionsDownloaded());
		view.setOldestParsedTransactonDate(model.getOldestParsedTransactionDate());
	}

	private void onDownloadError() {
		Throwable thrown = model.getDownloadError();
		boolean saveTransactions = view.showDownloadError(thrown);
		if (saveTransactions) {
			model.saveTransactions();
		} else {
			model.discardTransactions();
		}
		view.close();
	}

	private void onDownloadComplete() {
		model.saveTransactions();
		view.close();
	}
}
