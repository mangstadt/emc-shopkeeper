package emcshop.presenter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import emcshop.model.IBackupModel;
import emcshop.view.IBackupView;

public class BackupPresenter {
	private final IBackupView view;
	private final IBackupModel model;

	public BackupPresenter(IBackupView view, IBackupModel model) {
		this.view = view;
		this.model = model;

		view.addStartBackupListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onStartBackup();
			}
		});

		view.addRestoreBackupListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onRestoreBackup();
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
		view.backupComplete();
	}

	private void onRestoreBackup() {
		//TODO
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
}
