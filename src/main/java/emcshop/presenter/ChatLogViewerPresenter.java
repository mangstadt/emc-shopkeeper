package emcshop.presenter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Date;

import emcshop.db.PaymentTransactionDb;
import emcshop.model.IChatLogViewerModel;
import emcshop.view.IChatLogViewerView;

public class ChatLogViewerPresenter {
    private final IChatLogViewerView view;
    private final IChatLogViewerModel model;

    public ChatLogViewerPresenter(IChatLogViewerView view, IChatLogViewerModel model) {
        this.view = view;
        this.model = model;

        view.addDateChangedListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDateChanged();
            }
        });

        view.addLogDirectoryChanged(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onLogDirectoryChanged();
            }
        });

        view.addCloseListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onClose();
            }
        });

        view.setLogDirectory(model.getLogDirectory());
        view.setCurrentPlayer(model.getCurrentPlayer());

        PaymentTransactionDb paymentTransaction = model.getPaymentTransaction();
        view.setPaymentTransaction(paymentTransaction);

        Date dateToDisplay = (paymentTransaction == null) ? new Date() : paymentTransaction.getTs();
        view.setDate(dateToDisplay);
        view.setChatMessages(model.getChatMessages(dateToDisplay));

        view.display();
    }

    private void onDateChanged() {
        Date date = view.getDate();
        view.setChatMessages(model.getChatMessages(date));
    }

    private void onLogDirectoryChanged() {
        File dir = view.getLogDirectory();
        if (!dir.exists()) {
            view.showError("The specified log directory does not exist.");
            return;
        }
        if (!dir.isDirectory()) {
            view.showError("The specified path is not a directory.");
            return;
        }

        model.setLogDirectory(dir);
        onDateChanged();
    }

    private void onClose() {
        view.close();
    }
}
