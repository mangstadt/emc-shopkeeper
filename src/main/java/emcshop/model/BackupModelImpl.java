package emcshop.model;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import emcshop.AppContext;
import emcshop.BackupManager;
import emcshop.Settings;
import emcshop.db.DbDao;
import emcshop.util.GuiUtils;
import emcshop.util.ZipUtils.ZipListener;

public class BackupModelImpl implements IBackupModel {
	private static final AppContext context = AppContext.instance();

	private final DbDao dao;
	private final Settings settings;
	private final BackupManager backupManager;

	private final List<ActionListener> backupCompleteListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> backupPercentCompleteListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> restoreCompleteListeners = new ArrayList<ActionListener>();

	public BackupModelImpl() {
		dao = context.get(DbDao.class);
		settings = context.get(Settings.class);
		backupManager = context.get(BackupManager.class);
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
	public void addRestoreCompleteListener(ActionListener listener) {
		restoreCompleteListeners.add(listener);
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
		Thread t = new Thread(() -> {
			try {
				dao.close();
				long size = backupManager.getSizeOfDatabase();
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
						throw new RuntimeException("Could not reconnect to database after backup completed.", e);
					}
				}
			}
		});
		t.start();
		return t;
	}

	@Override
	public Thread startRestore(Date date) {
		Thread t = new Thread(() -> {
			try {
				dao.close();
				backupManager.restore(date);
			} catch (IOException e) {
				//TODO display error
				throw new RuntimeException(e);
			} catch (SQLException e) {
				//TODO display error
				throw new RuntimeException(e);
			} finally {
				GuiUtils.fireEvents(restoreCompleteListeners);
			}
		});
		t.start();
		return t;
	}

	@Override
	public void deleteBackup(Date date) {
		backupManager.delete(date);
	}
}
