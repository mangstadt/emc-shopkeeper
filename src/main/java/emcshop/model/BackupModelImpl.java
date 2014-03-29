package emcshop.model;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import emcshop.BackupManager;
import emcshop.db.DbDao;
import emcshop.util.GuiUtils;
import emcshop.util.Settings;
import emcshop.util.ZipUtils.ZipListener;

public class BackupModelImpl implements IBackupModel {
	private final DbDao dao;
	private final Settings settings;
	private final BackupManager backupManager;
	private final List<ActionListener> backupCompleteListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> backupPercentCompleteListeners = new ArrayList<ActionListener>();

	public BackupModelImpl(DbDao dao, Settings settings, BackupManager backupManager) {
		this.dao = dao;
		this.settings = settings;
		this.backupManager = backupManager;
	}

	@Override
	public void addBackupCompleteListener(ActionListener listener) {
		backupCompleteListeners.add(listener);
	}

	@Override
	public void addBackupPercentCompleteListener(ActionListener listener) {
		backupPercentCompleteListeners.add(listener);
	}

	@Override
	public boolean getAutoBackupEnabled() {
		return settings.getBackupsEnabled();
	}

	@Override
	public Integer getBackupFrequency() {
		return settings.getBackupFrequency();
	}

	@Override
	public Integer getMaxBackups() {
		return settings.getMaxBackups();
	}

	@Override
	public void setAutoBackupEnabled(boolean enabled) {
		settings.setBackupsEnabled(enabled);
	}

	@Override
	public void setBackupFrequency(Integer days) {
		settings.setBackupFrequency(days);
	}

	@Override
	public void setMaxBackups(Integer days) {
		settings.setMaxBackups(days);
	}

	@Override
	public List<Date> getBackups() {
		return backupManager.getBackupDates();
	}

	@Override
	public void saveSettings() {
		settings.save();
	}

	@Override
	public Thread startBackup() {
		Thread t = new Thread("Backup") {
			@Override
			public void run() {
				try {
					dao.close();
					final long size = backupManager.getSizeOfDatabase();
					backupManager.backup(new ZipListener() {
						private long zipped = 0;

						@Override
						public void onZippedFile(File file) {
							zipped += file.length();
							double percent = ((double) zipped / size) * 100.0;
							GuiUtils.fireEvents(backupPercentCompleteListeners, new ActionEvent(BackupModelImpl.this, 0, percent + ""));
						}
					});
				} catch (IOException e) {
					//TODO display error
					throw new RuntimeException(e);
				} catch (SQLException e) {
					//TODO display error
					throw new RuntimeException(e);
				} finally {
					try {
						GuiUtils.fireEvents(backupCompleteListeners);
					} finally {
						try {
							dao.reconnect();
						} catch (SQLException e) {
							//ignore
						}
					}
				}
			}
		};
		t.start();
		return t;
	}

}
