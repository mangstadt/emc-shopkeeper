package emcshop;

import static org.apache.commons.io.FileUtils.touch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import emcshop.util.ZipUtils.ZipListener;

public class BackupManagerTest {
	@Rule
	public final TemporaryFolder temp = new TemporaryFolder();

	private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private File root, dbDir, dbBackupDir;

	@Before
	public void before() {
		root = temp.getRoot();
		dbDir = new File(root, "db");
		dbBackupDir = new File(root, "db-backup");
	}

	@Test
	public void cleanup() throws Throwable {
		File file1 = new File(dbBackupDir, "db-20130101T000000.backup.zip");
		touch(file1);
		File file2 = new File(dbBackupDir, "db-20130102T000000.backup.zip");
		touch(file2);
		File file3 = new File(dbBackupDir, "db-20130103T000000.backup.zip");
		touch(file3);

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.cleanup();

		assertFalse(file1.exists());
		assertTrue(file2.exists());
		assertTrue(file3.exists());
	}

	@Test
	public void cleanup_no_backups() {
		dbBackupDir.mkdir();
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.cleanup();

		assertDirectoryContents(dbBackupDir);
	}

	@Test
	public void cleanup_backups_disabled() throws Throwable {
		File file1 = new File(dbBackupDir, "db-20130101T000000.backup.zip");
		touch(file1);
		File file2 = new File(dbBackupDir, "db-20130102T000000.backup.zip");
		touch(file2);
		File file3 = new File(dbBackupDir, "db-20130103T000000.backup.zip");
		touch(file3);

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, false, 1, 2);
		bm.cleanup();

		assertTrue(file1.exists());
		assertTrue(file2.exists());
		assertTrue(file3.exists());
	}

	@Test
	public void cleanup_no_max() throws Throwable {
		File file1 = new File(dbBackupDir, "db-20130101T000000.backup.zip");
		touch(file1);
		File file2 = new File(dbBackupDir, "db-20130102T000000.backup.zip");
		touch(file2);
		File file3 = new File(dbBackupDir, "db-20130103T000000.backup.zip");
		touch(file3);

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, null);
		bm.cleanup();

		assertTrue(file1.exists());
		assertTrue(file2.exists());
		assertTrue(file3.exists());
	}

	@Test
	public void delete() throws Throwable {
		File file1 = new File(dbBackupDir, "db-20130101T000000.backup.zip");
		touch(file1);
		File file2 = new File(dbBackupDir, "db-20130102T000000.backup.zip");
		touch(file2);
		File file3 = new File(dbBackupDir, "db-20130103T000000.backup.zip");
		touch(file3);

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.delete(df.parse("2013-01-02 00:00:00"));

		assertTrue(file1.exists());
		assertFalse(file2.exists());
		assertTrue(file3.exists());
	}

	@Test
	public void delete_non_existent_backup() throws Throwable {
		File file1 = new File(dbBackupDir, "db-20130101T000000.backup.zip");
		touch(file1);
		File file2 = new File(dbBackupDir, "db-20130102T000000.backup.zip");
		touch(file2);
		File file3 = new File(dbBackupDir, "db-20130103T000000.backup.zip");
		touch(file3);

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.delete(df.parse("2013-06-20 00:00:00"));
		//nothing should happen

		assertTrue(file1.exists());
		assertTrue(file2.exists());
		assertTrue(file3.exists());
	}

	@Test
	public void shouldBackup_no_database() throws Throwable {
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertFalse(bm.shouldBackup());
	}

	@Test
	public void shouldBackup_backups_disabled() throws Throwable {
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, false, 1, 2);
		assertFalse(bm.shouldBackup());
	}

	@Test
	public void shouldBackup_no_backups() throws Throwable {
		dbDir.mkdir();
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertTrue(bm.shouldBackup());
	}

	@Test
	public void shouldBackup_frequency_exceeded() throws Throwable {
		dbDir.mkdir();
		touch(new File(dbBackupDir, "db-20130101T000000.backup.zip"));

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertTrue(bm.shouldBackup());
	}

	@Test
	public void shouldBackup_frequency_not_exceeded() throws Throwable {
		dbDir.mkdir();
		DateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
		touch(new File(dbBackupDir, "db-" + df.format(new Date()) + ".backup.zip"));

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertFalse(bm.shouldBackup());
	}

	@Test
	public void backup() throws Throwable {
		File file1 = new File(dbDir, "file1");
		touch(file1);
		File file2 = new File(dbDir, "file2");
		touch(file2);
		File file3 = new File(dbDir, "file3");
		touch(file3);

		ZipListener listener = mock(ZipListener.class);
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.backup(listener);

		verify(listener).onZippedFile(file1);
		verify(listener).onZippedFile(file2);
		verify(listener).onZippedFile(file3);

		//original database should remain
		assertTrue(file1.exists());
		assertTrue(file2.exists());
		assertTrue(file3.exists());

		File[] backups = dbBackupDir.listFiles();
		assertEquals(1, backups.length);
		assertTrue(backups[0].getName().matches("db-\\d{8}T\\d{6}\\.backup\\.zip"));
	}

	@Test
	public void backup_error() throws Throwable {
		File file1 = new File(dbDir, "file1");
		touch(file1);
		File file2 = new File(dbDir, "file2");
		touch(file2);
		File file3 = new File(dbDir, "file3");
		touch(file3);

		RuntimeException exception = new RuntimeException();
		ZipListener listener = mock(ZipListener.class);
		doThrow(exception).when(listener).onZippedFile(file2);

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		try {
			bm.backup(listener);
			fail();
		} catch (RuntimeException e) {
			assertEquals(exception, e);
		}

		//original database should remain
		assertTrue(file1.exists());
		assertTrue(file2.exists());
		assertTrue(file3.exists());

		//ZIP file should have been deleted
		assertDirectoryContents(dbBackupDir);
	}

	@Test
	public void restore() throws Throwable {
		File file1 = new File(dbDir, "file1");
		touch(file1);
		File file2 = new File(dbDir, "file2");
		touch(file2);
		File file3 = new File(dbDir, "file3");
		touch(file3);

		dbBackupDir.mkdir();
		File backupFile = new File(dbBackupDir, "db-20130101T000000.backup.zip");
		ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(backupFile));
		zout.putNextEntry(new ZipEntry(dbDir.getName() + "/restorefile1"));
		zout.putNextEntry(new ZipEntry(dbDir.getName() + "/restorefile2"));
		zout.putNextEntry(new ZipEntry(dbDir.getName() + "/restorefile3"));
		zout.close();

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.restore(df.parse("2013-01-01 00:00:00"));

		assertDirectoryContents(root, dbDir.getName(), dbBackupDir.getName());

		//check for backup DB files
		assertTrue(new File(dbDir, "restorefile1").exists());
		assertTrue(new File(dbDir, "restorefile2").exists());
		assertTrue(new File(dbDir, "restorefile3").exists());

		//backup ZIP should remain
		assertTrue(backupFile.exists());

		//original database should have been deleted
		assertFalse(file1.exists());
		assertFalse(file2.exists());
		assertFalse(file3.exists());
	}

	@Test
	public void restore_non_existent_backup() throws Throwable {
		File file1 = new File(dbDir, "file1");
		touch(file1);
		File file2 = new File(dbDir, "file2");
		touch(file2);
		File file3 = new File(dbDir, "file3");
		touch(file3);

		dbBackupDir.mkdir();
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		try {
			bm.restore(df.parse("2013-01-01 00:00:00"));
			fail();
		} catch (FileNotFoundException e) {
			//should be thrown
		}

		assertDirectoryContents(root, dbDir.getName(), dbBackupDir.getName());

		//original database should not have been deleted
		assertTrue(file1.exists());
		assertTrue(file2.exists());
		assertTrue(file3.exists());
	}

	@Test
	public void getBackupDates() throws Throwable {
		touch(new File(dbBackupDir, "db-20130101T000000.backup.zip"));
		touch(new File(dbBackupDir, "db-20130102T000000.backup.zip"));
		touch(new File(dbBackupDir, "db-20130103T000000.backup.zip"));
		touch(new File(dbBackupDir, "foo.txt"));

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		List<Date> actual = bm.getBackupDates();
		List<Date> expected = Arrays.asList(df.parse("2013-01-03 00:00:00"), df.parse("2013-01-02 00:00:00"), df.parse("2013-01-01 00:00:00"));
		assertEquals(expected, actual);
	}

	private static void assertDirectoryContents(File directory, String... expectedFileAndFolderNames) {
		Set<String> actual = new HashSet<String>();
		for (File file : directory.listFiles()) {
			actual.add(file.getName());
		}

		Set<String> expected = new HashSet<String>(Arrays.asList(expectedFileAndFolderNames));
		assertEquals(expected, actual);
	}
}
