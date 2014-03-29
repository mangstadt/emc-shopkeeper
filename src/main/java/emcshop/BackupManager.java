package emcshop;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emcshop.util.ZipUtils;
import emcshop.util.ZipUtils.ZipListener;

/**
 * Manages database backups.
 */
public class BackupManager {
	private final File dbDir, backupDir;
	private final Integer backupFrequency;
	private final DateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

	/**
	 * @param dbDir the live database directory
	 * @param backupDir the directory where database backups are stored
	 * @param backupFrequency how often backups should be made (in days), or
	 * null to never backup
	 */
	public BackupManager(File dbDir, File backupDir, Integer backupFrequency) {
		this.dbDir = dbDir;
		this.backupDir = backupDir;
		this.backupFrequency = backupFrequency;
	}

	/**
	 * Determines if a backup should be performed.
	 * @return true if a backup is needed, false if not
	 */
	public boolean shouldBackup() {
		if (backupFrequency == null || !dbDir.exists()) {
			return false;
		}

		Date latestBackup = getLatestBackupDate();
		if (latestBackup == null) {
			return true;
		}

		Date d = new Date(System.currentTimeMillis() - backupFrequency * 24 * 60 * 60 * 1000);
		return latestBackup.before(d);
	}

	public long getSizeOfDatabase() {
		return ZipUtils.getDirectorySize(dbDir);
	}

	/**
	 * Backs up the database.
	 * @param listener
	 * @return
	 * @throws IOException
	 */
	public void backup(ZipListener listener) throws IOException {
		backupDir.mkdirs();
		File zip = new File(backupDir, "db-" + df.format(new Date()) + ".backup.zip");
		try {
			ZipUtils.zipDirectory(dbDir, zip, listener);
		} catch (Throwable t) {
			zip.delete();

			if (t instanceof IOException) {
				throw (IOException) t;
			}
			if (t instanceof RuntimeException) {
				throw (RuntimeException) t;
			}
		}
	}

	/**
	 * Gets the date of the latest backup.
	 * @return the date or null if there are no backups
	 */
	private Date getLatestBackupDate() {
		List<Date> dates = getBackupDates();
		return dates.isEmpty() ? null : dates.get(dates.size() - 1);
	}

	/**
	 * Gets the dates of all backups.
	 * @return the backup dates
	 */
	public List<Date> getBackupDates() {
		if (!backupDir.isDirectory()) {
			return new ArrayList<Date>(0);
		}

		Pattern p = Pattern.compile("^db-(\\d{8}T\\d{6})\\.backup\\.zip$");
		List<Date> dates = new ArrayList<Date>();
		for (File file : backupDir.listFiles()) {
			Matcher m = p.matcher(file.getName());
			if (!m.find()) {
				continue;
			}

			Date date;
			try {
				date = df.parse(m.group(1));
			} catch (ParseException e) {
				//should never be thrown because of the regex
				continue;
			}

			dates.add(date);
		}

		Collections.sort(dates);
		return dates;
	}
}
