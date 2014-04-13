package emcshop.model;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
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
	private final List<ActionListener> restoreCompleteListeners = new ArrayList<ActionListener>();

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
		reportSender.report(thrown);
	}

	@Override
	public List<Date> getBackups() {
		return backupManager.getBackupDates();
	}

	@Override
	public Thread startRestore(final Date date) {
		Thread t = new Thread("Restore") {
			@Override
			public void run() {
				try {
					if (dao != null) {
						dao.close();
					}
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
			}
		};
		t.start();
		return t;
	}
}
