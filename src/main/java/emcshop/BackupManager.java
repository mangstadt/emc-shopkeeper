package emcshop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import emcshop.util.ZipUtils;
import emcshop.util.ZipUtils.ZipListener;

/**
 * Manages database backups.
 */
public class BackupManager {
	private static final Logger logger = Logger.getLogger(BackupManager.class.getName());

	private final Path dbDir, backupDir;
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
	public BackupManager(Path dbDir, Path backupDir, boolean backupsEnabled, Integer backupFrequency, Integer maxBackups) {
		this.dbDir = dbDir;
		this.backupDir = backupDir;
		this.backupsEnabled = backupsEnabled;
		this.backupFrequency = backupFrequency;
		this.maxBackups = maxBackups;
	}

	/**
	 * Deletes old backups.
	 * @throws IOException if there's a problem deleting any of the backups
	 */
	public void cleanup() throws IOException {
		if (!backupsEnabled || maxBackups == null) {
			return;
		}

		Map<LocalDateTime, Path> backups = getBackups();
		List<LocalDateTime> dates = new ArrayList<>(backups.keySet());
		Collections.sort(dates, Collections.reverseOrder());

		for (int i = maxBackups; i < dates.size(); i++) {
			LocalDateTime date = dates.get(i);
			Path file = backups.get(date);
			Files.delete(file);
		}
	}

	/**
	 * Determines if a backup should be performed.
	 * @return true if a backup is needed, false if not
	 * @throws IOException if there's a problem reading the backup data
	 */
	public boolean shouldBackup() throws IOException {
		if (!backupsEnabled || !Files.exists(dbDir)) {
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
	 * @throws IOException if there's a problem getting the database size
	 */
	public long getSizeOfDatabase() throws IOException {
		return ZipUtils.getDirectorySize(dbDir);
	}

	/**
	 * Backs up the database.
	 * @param listener invoked every time a database file is added to the ZIP
	 * archive (may be null)
	 * @throws IOException if there's a problem backing up the database
	 */
	public void backup(ZipListener listener) throws IOException {
		Files.createDirectories(backupDir);
		Path zip = getBackupFilePath(LocalDateTime.now());
		try {
			ZipUtils.zipDirectory(dbDir, zip, listener);
		} catch (IOException | RuntimeException e) {
			/*
			 * If the zip operation fails, delete the zip file and rethrow the
			 * exception.
			 */
			try {
				Files.delete(zip);
			} catch (IOException ignore) {
			}
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
		Path dbDirMoved = dbDir.resolveSibling(dbDir.getFileName() + ".tmp");
		Files.move(dbDir, dbDirMoved);

		Path zipFile = getBackupFilePath(date);
		try {
			ZipUtils.unzip(dbDir.getParent(), zipFile);

			//delete the old live database
			try {
				FileUtils.deleteDirectory(dbDirMoved.toFile());
			} catch (IOException e) {
				logger.log(Level.WARNING, "Unable to delete old database directory after restore operation completed successfully: " + dbDirMoved, e);
			}
		} catch (IOException | RuntimeException e) {
			//if an error occurs, restore the original database
			try {
				FileUtils.deleteDirectory(dbDir.toFile());
			} catch (IOException e2) {
				logger.log(Level.WARNING, "Unable to delete the folder of the restored database after restore operation failed: " + dbDir, e2);
			}
			Files.move(dbDirMoved, dbDir);

			throw e;
		}
	}

	/**
	 * Deletes a backup.
	 * @param date the backup to delete
	 * @throws IOException if there's a problem deleting the backup
	 */
	public void delete(LocalDateTime date) throws IOException {
		Path zipFile = getBackupFilePath(date);
		Files.delete(zipFile);
	}

	private Path getBackupFilePath(LocalDateTime date) {
		return backupDir.resolve("db-" + backupFileNameDateFormat.format(date) + ".backup.zip");
	}

	/**
	 * Gets the date of the latest backup.
	 * @return the date or null if there are no backups
	 * @throws IOException if there's a problem getting the list of available
	 * backups
	 */
	private LocalDateTime getLatestBackupDate() throws IOException {
		List<LocalDateTime> dates = getBackupDates();
		return dates.isEmpty() ? null : dates.get(0);
	}

	/**
	 * Gets the dates of all backups in descending order.
	 * @return the backup dates
	 * @throws IOException if there's a problem getting the list of available
	 * backups
	 */
	public List<LocalDateTime> getBackupDates() throws IOException {
		Map<LocalDateTime, Path> backups = getBackups();
		List<LocalDateTime> dates = new ArrayList<>(backups.keySet());
		Collections.sort(dates, Collections.reverseOrder());
		return dates;
	}

	private Map<LocalDateTime, Path> getBackups() throws IOException {
		Map<LocalDateTime, Path> backups = new HashMap<>();
		if (!Files.isDirectory(backupDir)) {
			return backups;
		}

		Files.list(backupDir).forEach(file -> {
			Matcher m = backupFileNameRegex.matcher(file.getFileName().toString());
			if (!m.find()) {
				return;
			}

			LocalDateTime date = LocalDateTime.from(backupFileNameDateFormat.parse(m.group(1)));
			backups.put(date, file);
		});

		return backups;
	}
}
