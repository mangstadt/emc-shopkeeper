package emcshop.presenter;

import java.time.Duration;

import emcshop.model.IFirstUpdateModel;
import emcshop.view.IFirstUpdateView;

public class FirstUpdatePresenter {
	private final IFirstUpdateView view;
	private final IFirstUpdateModel model;

	private Integer stopAtPage;
	private Duration maxPaymentTransactionAge;
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
		Duration estimatedTime = (stopAtPage == null) ? null : model.getEstimatedTime(stopAtPage);
		view.setEstimatedTime(estimatedTime);
	}

	public Integer getStopAtPage() {
		return stopAtPage;
	}

	public Duration getMaxPaymentTransactionAge() {
		return maxPaymentTransactionAge;
	}

	public boolean isCanceled() {
		return canceled;
	}
}
