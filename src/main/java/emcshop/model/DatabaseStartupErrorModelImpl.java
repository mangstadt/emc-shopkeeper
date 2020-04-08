package emcshop.model;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import emcshop.AppContext;
import emcshop.BackupManager;
import emcshop.ReportSender;
import emcshop.db.DbDao;
import emcshop.util.GuiUtils;

public class DatabaseStartupErrorModelImpl implements IDatabaseStartupErrorModel {
	private static final Logger logger = Logger.getLogger(DatabaseStartupErrorModelImpl.class.getName());
	private static final AppContext context = AppContext.instance();

	private final DbDao dao;
	private final BackupManager backupManager;
	private final ReportSender reportSender;
	private final Throwable thrown;
	private final List<ActionListener> restoreCompleteListeners = new ArrayList<>();

	/**
	 * @param thrown the exception that was thrown
	 */
	public DatabaseStartupErrorModelImpl(Throwable thrown) {
		this.thrown = thrown;
		dao = context.get(DbDao.class);
		backupManager = context.get(BackupManager.class);
		reportSender = context.get(ReportSender.class);
	}

	@Override
	public void addRestoreCompleteListener(ActionListener listener) {
		restoreCompleteListeners.add(listener);
	}

	@Override
	public Throwable getThrown() {
		return thrown;
	}

	@Override
	public void logError() {
		logger.log(Level.SEVERE, "Error starting up database.", thrown);
	}

	@Override
	public void sendErrorReport() {
		reportSender.report(null, thrown);
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
	public Thread startRestore(LocalDateTime date) {
		Thread t = new Thread(() -> {
			try {
				if (dao != null) {
					dao.close();
				}
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
}
