package emcshop.presenter;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import emcshop.model.FirstUpdateModelImpl;
import emcshop.model.IFirstUpdateModel;
import emcshop.view.FirstUpdateViewImpl;
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

		view.addOnBeginListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onBegin();
			}
		});

		view.addOnCancelListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onCancel();
			}
		});

		view.addStopAtPageChangedListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onStopAtPageChanged();
			}
		});

		view.setStopAtPage(5000);
		view.setMaxPaymentTransactionAge(7);
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

	public static FirstUpdatePresenter show(Window owner) {
		IFirstUpdateView view = new FirstUpdateViewImpl(owner);
		IFirstUpdateModel model = new FirstUpdateModelImpl();
		return new FirstUpdatePresenter(view, model);
	}
}
