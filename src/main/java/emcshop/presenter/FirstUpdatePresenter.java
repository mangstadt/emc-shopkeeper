package emcshop.presenter;

import emcshop.model.IFirstUpdateModel;
import emcshop.view.IFirstUpdateView;

public class FirstUpdatePresenter {
	private final IFirstUpdateView view;
	private final IFirstUpdateModel model;

	private Integer stopAtPage;
	private Integer maxPaymentTransactionAge;
	private boolean canceled;

	public FirstUpdatePresenter(IFirstUpdateView view, IFirstUpdateModel model) {
		this.view = view;
		this.model = model;

		view.addOnBeginListener(event -> onBegin());
		view.addOnCancelListener(event -> onCancel());
		view.addStopAtPageChangedListener(event -> onStopAtPageChanged());

		onStopAtPageChanged();
		view.display();
	}

	private void onCancel() {
		view.close();
		canceled = true;
	}

	private void onBegin() {
		stopAtPage = view.getStopAtPage();
		maxPaymentTransactionAge = view.getMaxPaymentTransactionAge();
		view.close();
	}

	private void onStopAtPageChanged() {
		Integer stopAtPage = view.getStopAtPage();
		Long estimatedTime = (stopAtPage == null) ? null : model.getEstimatedTime(stopAtPage);
		view.setEstimatedTime(estimatedTime);
	}

	public Integer getStopAtPage() {
		return stopAtPage;
	}

	public Integer getMaxPaymentTransactionAge() {
		return maxPaymentTransactionAge;
	}

	public boolean isCanceled() {
		return canceled;
	}
}
