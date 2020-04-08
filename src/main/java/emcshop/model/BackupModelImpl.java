package emcshop.model;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import emcshop.AppContext;
import emcshop.BackupManager;
import emcshop.Settings;
import emcshop.db.DbDao;
import emcshop.util.GuiUtils;

public class BackupModelImpl implements IBackupModel {
	private static final AppContext context = AppContext.instance();

	private final DbDao dao;
	private final Settings settings;
	private final BackupManager backupManager;

	private final List<ActionListener> backupCompleteListeners = new ArrayList<>();
	private final List<ActionListener> backupPercentCompleteListeners = new ArrayList<>();
	private final List<ActionListener> restoreCompleteListeners = new ArrayList<>();

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
	public List<LocalDateTime> getBackups() {
		try {
			return backupManager.getBackupDates();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
				backupManager.backup((file, percent) -> {
					GuiUtils.fireEvents(backupPercentCompleteListeners, new ActionEvent(this, 0, percent + ""));
				});
			} catch (IOException | SQLException e) {
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
	public Thread startRestore(LocalDateTime date) {
		Thread t = new Thread(() -> {
			try {
				dao.close();
				backupManager.restore(date);
			} catch (IOException | SQLException e) {
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
	public void deleteBackup(LocalDateTime date) {
		try {
			backupManager.delete(date);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
