package emcshop.view;

import java.awt.event.ActionListener;
import java.time.Duration;

public interface IFirstUpdateView {
	void addOnCancelListener(ActionListener listener);

	void addOnBeginListener(ActionListener listener);

	void addStopAtPageChangedListener(ActionListener listener);

	Integer getStopAtPage();

	void setStopAtPage(Integer stopAtPage);

	Duration getMaxPaymentTransactionAge();

	void setMaxPaymentTransactionAge(Duration age);

	void setEstimatedTime(Duration estimatedTime);

	void display();

	void close();
}
