package emcshop.presenter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import emcshop.model.IDatabaseStartupErrorModel;
import emcshop.view.IDatabaseStartupErrorView;

public class DatabaseStartupErrorPresenter {
    private final IDatabaseStartupErrorView view;
    private final IDatabaseStartupErrorModel model;
    private boolean quit = false;

    public DatabaseStartupErrorPresenter(IDatabaseStartupErrorView view, IDatabaseStartupErrorModel model) {
        this.view = view;
        this.model = model;

        view.addSendErrorReportListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSendErrorReport();
            }
        });

        view.addCloseListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onClose();
            }
        });

        view.addStartRestoreListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onStartRestore();
            }
        });

        model.addRestoreCompleteListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                onRestoreComplete();
            }
        });

        view.setThrown(model.getThrown());
        view.setBackups(model.getBackups());
        model.logError();
        view.display();
    }

    private void onSendErrorReport() {
        model.sendErrorReport();
        view.errorReportSent();
    }

    private void onClose() {
        quit = true;
        view.close();
    }

    private void onStartRestore() {
        model.startRestore(view.getSelectedBackup());
    }

    private void onRestoreComplete() {
        quit = false;
        view.close();
    }

    public boolean getQuit() {
        return quit;
    }
}
