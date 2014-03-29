package emcshop.presenter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;

import emcshop.model.IBackupModel;
import emcshop.view.IBackupView;

public class BackupPresenter {
	private final IBackupView view;
	private final IBackupModel model;
	private boolean exit = false;

	public BackupPresenter(IBackupView view, IBackupModel model) {
		this.view = view;
		this.model = model;

		view.addStartBackupListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onStartBackup();
			}
		});

		view.addStartRestoreListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onStartRestore();
			}
		});

		view.addDeleteBackupListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onDeleteBackup();
			}
		});

		view.addSaveSettingsListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onSaveSettings();
			}
		});

		view.addCancelListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onCancel();
			}
		});

		view.addExitListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onExit();
			}
		});

		model.addBackupPercentCompleteListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				onBackupPercentComplete(Double.parseDouble(event.getActionCommand()));
			}
		});

		model.addBackupCompleteListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onBackupComplete();
			}
		});

		model.addRestoreCompleteListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onRestoreComplete();
			}
		});

		view.setAutoBackupEnabled(model.getAutoBackupEnabled());
		view.setBackupFrequency(model.getBackupFrequency());
		view.setMaxBackups(model.getMaxBackups());
		view.setBackups(model.getBackups());

		view.display();
	}

	private void onStartBackup() {
		model.startBackup();
	}

	private void onBackupPercentComplete(double percent) {
		view.setBackupPercentComplete(percent);
	}

	private void onBackupComplete() {
		view.setBackups(model.getBackups());
		view.backupComplete();
	}

	private void onStartRestore() {
		model.startRestore(view.getSelectedBackup());
	}

	private void onRestoreComplete() {
		view.restoreComplete();
	}

	private void onDeleteBackup() {
		Date date = view.getSelectedBackup();
		model.deleteBackup(date);
		view.setBackups(model.getBackups());
	}

	private void onSaveSettings() {
		Integer frequency = view.getBackupFrequency();
		if (frequency <= 0) {
			view.invalidFrequency();
			return;
		}

		Integer max = view.getMaxBackups();
		if (max <= 0) {
			view.invalidMax();
			return;
		}

		model.setAutoBackupEnabled(view.getAutoBackupEnabled());
		model.setBackupFrequency(frequency);
		model.setMaxBackups(max);
		model.saveSettings();

		view.close();
	}

	private void onCancel() {
		view.close();
	}

	private void onExit() {
		exit = true;
		view.close();
	}

	public boolean getExit() {
		return exit;
	}
}
