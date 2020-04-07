package emcshop;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import emcshop.util.ZipUtils;
import emcshop.util.ZipUtils.ZipListener;

/**
 * Manages database backups.
 */
public class BackupManager {
	private final File dbDir, backupDir;
	private final boolean backupsEnabled;
	private final Integer backupFrequency, maxBackups;
	private final DateTimeFormatter backupFileNameDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
	private final Pattern backupFileNameRegex = Pattern.compile("^db-(\\d{8}T\\d{6})\\.backup\\.zip$");

	/**
	 * @param dbDir the live database directory
	 * @param backupDir the directory where database backups are stored
	 * @param backupsEnabled true if auto backups are enabled, false if not
	 * @param backupFrequency how often backups should be made (in days)
	 * @param maxBackups the max number of backups to keep, or null to never
	 * delete
	 */
	public BackupManager(File dbDir, File backupDir, boolean backupsEnabled, Integer backupFrequency, Integer maxBackups) {
		this.dbDir = dbDir;
		this.backupDir = backupDir;
		this.backupsEnabled = backupsEnabled;
		this.backupFrequency = backupFrequency;
		this.maxBackups = maxBackups;
	}

	/**
	 * Deletes old backups.
	 */
	public void cleanup() {
		if (!backupsEnabled || maxBackups == null) {
			return;
		}

		Map<LocalDateTime, File> backups = getBackups();
		List<LocalDateTime> dates = new ArrayList<>(backups.keySet());
		Collections.sort(dates, Collections.reverseOrder());

		for (int i = maxBackups; i < dates.size(); i++) {
			LocalDateTime date = dates.get(i);
			File file = backups.get(date);
			file.delete();
		}
	}

	/**
	 * Determines if a backup should be performed.
	 * @return true if a backup is needed, false if not
	 */
	public boolean shouldBackup() {
		if (!backupsEnabled || !dbDir.exists()) {
			return false;
		}

		LocalDateTime latestBackup = getLatestBackupDate();
		if (latestBackup == null) {
			return true;
		}

		long daysAgo = latestBackup.until(LocalDateTime.now(), ChronoUnit.DAYS);
		return (daysAgo >= backupFrequency);
	}

	/**
	 * Gets the size of the live database.
	 * @return the database size in bytes
	 */
	public long getSizeOfDatabase() {
		return ZipUtils.getDirectorySize(dbDir);
	}

	/**
	 * Backs up the database.
	 * @param listener invoked every time a database file is added to the ZIP
	 * archive
	 * @throws IOException if there's a problem backing up the database
	 */
	public void backup(ZipListener listener) throws IOException {
		backupDir.mkdirs();
		File zip = getBackupFile(LocalDateTime.now());
		try {
			ZipUtils.zipDirectory(dbDir, zip, listener);
		} catch (IOException | RuntimeException e) {
			/*
			 * If the zip operation fails, delete the zip file and rethrow the
			 * exception.
			 */
			zip.delete();
			throw e;
		}
	}

	/**
	 * Restores a backed-up database.
	 * @param date the date of the backup
	 * @throws IOException if there's a problem restoring the database
	 */
	public void restore(LocalDateTime date) throws IOException {
		//rename the live database directory
		File dbDirMoved = new File(dbDir.getParent(), dbDir.getName() + ".tmp");
		dbDir.renameTo(dbDirMoved);

		File zipFile = getBackupFile(date);
		try {
			ZipUtils.unzip(dbDir.getParentFile(), zipFile);

			//delete the old live database
			try {
				FileUtils.deleteDirectory(dbDirMoved);
			} catch (IOException e) {
				//ignore
			}
		} catch (Throwable t) {
			//if an error occurs, restore the original database
			try {
				FileUtils.deleteDirectory(dbDir);
			} catch (IOException e) {
				//ignore
			}
			dbDirMoved.renameTo(dbDir);

			if (t instanceof IOException) {
				throw (IOException) t;
			}
			if (t instanceof RuntimeException) {
				throw (RuntimeException) t;
			}
		}
	}

	/**
	 * Deletes a backup.
	 * @param date the backup to delete
	 */
	public void delete(LocalDateTime date) {
		File zipFile = getBackupFile(date);
		zipFile.delete();
	}

	private File getBackupFile(LocalDateTime date) {
		return new File(backupDir, "db-" + backupFileNameDateFormat.format(date) + ".backup.zip");
	}

	/**
	 * Gets the date of the latest backup.
	 * @return the date or null if there are no backups
	 */
	private LocalDateTime getLatestBackupDate() {
		List<LocalDateTime> dates = getBackupDates();
		return dates.isEmpty() ? null : dates.get(0);
	}

	/**
	 * Gets the dates of all backups in descending order.
	 * @return the backup dates
	 */
	public List<LocalDateTime> getBackupDates() {
		Map<LocalDateTime, File> backups = getBackups();
		List<LocalDateTime> dates = new ArrayList<>(backups.keySet());
		Collections.sort(dates, Collections.reverseOrder());
		return dates;
	}

	private Map<LocalDateTime, File> getBackups() {
		Map<LocalDateTime, File> backups = new HashMap<>();
		if (!backupDir.isDirectory()) {
			return backups;
		}

		for (File file : backupDir.listFiles()) {
			Matcher m = backupFileNameRegex.matcher(file.getName());
			if (!m.find()) {
				continue;
			}

			LocalDateTime date = LocalDateTime.from(backupFileNameDateFormat.parse(m.group(1)));
			backups.put(date, file);
		}

		return backups;
	}
}
