package emcshop.view;

import java.awt.event.ActionListener;

public interface IFirstUpdateView {
    void addOnCancelListener(ActionListener listener);

    void addOnBeginListener(ActionListener listener);

    void addStopAtPageChangedListener(ActionListener listener);

    Integer getStopAtPage();

    void setStopAtPage(Integer stopAtPage);

    Integer getMaxPaymentTransactionAge();

    void setMaxPaymentTransactionAge(Integer age);

    void setEstimatedTime(Long estimatedTime);

    void display();

    void close();
}
