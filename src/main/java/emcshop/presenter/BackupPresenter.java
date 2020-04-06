package emcshop.presenter;

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

		view.addStartBackupListener(event -> onStartBackup());
		view.addStartRestoreListener(event -> onStartRestore());
		view.addDeleteBackupListener(event -> onDeleteBackup());
		view.addSaveSettingsListener(event -> onSaveSettings());
		view.addCancelListener(event -> onCancel());
		view.addExitListener(event -> onExit());

		model.addBackupPercentCompleteListener(event -> {
			double percent = Double.parseDouble(event.getActionCommand());
			onBackupPercentComplete(percent);
		});

		model.addBackupCompleteListener(event -> onBackupComplete());
		model.addRestoreCompleteListener(event -> onRestoreComplete());

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
		if (frequency == null || frequency <= 0) {
			view.invalidFrequency();
			return;
		}

		Integer max = view.getMaxBackups();
		if (max == null || max <= 0) {
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
