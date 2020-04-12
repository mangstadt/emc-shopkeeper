package emcshop;

import static emcshop.util.TestUtils.assertFileContent;
import static emcshop.util.TestUtils.mkdir;
import static emcshop.util.TestUtils.mkfile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import emcshop.util.ZipUtils;
import emcshop.util.ZipUtils.ZipListener;

public class BackupManagerTest {
	@Rule
	public final TemporaryFolder temp = new TemporaryFolder();

	private Path root, dbDir, dbBackupDir;

	@Before
	public void before() throws Exception {
		root = temp.getRoot().toPath();
		dbDir = mkdir(root, "db");
		dbBackupDir = mkdir(root, "db-backup");
	}

	@Test
	public void constructor_backups_folder_does_not_exist() throws Exception {
		Path dbBackupDir = dbDir.resolveSibling("backups-folder");
		Path versionFile = dbBackupDir.resolve("version");
		assertFalse(Files.isDirectory(dbBackupDir));

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);

		assertTrue(Files.isDirectory(dbBackupDir));
		assertEquals(1, bm.getVersion());
		assertFileContent(versionFile, "1");
	}

	@Test
	public void constructor_backups_folder_exists_without_version_file() throws Exception {
		Path dbBackupDir = dbDir.resolveSibling("backups-folder");
		Path versionFile = dbBackupDir.resolve("version");
		Files.createDirectory(dbBackupDir);

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);

		assertEquals(0, bm.getVersion());

		/*
		 * Version file is not created until setVersionToLatest() is called.
		 */
		assertFalse(Files.exists(versionFile));
	}

	@Test
	public void constructor_backups_folder_exists_with_version_file() throws Exception {
		Path dbBackupDir = dbDir.resolveSibling("backups-folder");
		Path versionFile = dbBackupDir.resolve("version");
		Files.createDirectory(dbBackupDir);
		Files.write(versionFile, "1".getBytes());

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);

		assertEquals(1, bm.getVersion());
	}

	@Test
	public void constructor_backups_folder_exists_with_version_file_bad_value() throws Exception {
		Path dbBackupDir = dbDir.resolveSibling("backups-folder");
		Path versionFile = dbBackupDir.resolve("version");
		Files.createDirectory(dbBackupDir);
		Files.write(versionFile, "bad".getBytes());

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);

		assertEquals(0, bm.getVersion());
	}

	@Test
	public void setVersionToLatest_without_existing_version_file() throws Exception {
		Path dbBackupDir = dbDir.resolveSibling("backups-folder");
		Path versionFile = dbBackupDir.resolve("version");
		Files.createDirectory(dbBackupDir);

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertEquals(0, bm.getVersion());

		bm.setVersionToLatest();
		assertEquals(1, bm.getVersion());
		assertFileContent(versionFile, "1");
	}

	@Test
	public void setVersionToLatest_with_existing_version_file() throws Exception {
		Path dbBackupDir = dbDir.resolveSibling("backups-folder");
		Path versionFile = dbBackupDir.resolve("version");
		Files.createDirectory(dbBackupDir);
		Files.write(versionFile, "0".getBytes());

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertEquals(0, bm.getVersion());

		bm.setVersionToLatest();
		assertEquals(1, bm.getVersion());
		assertFileContent(versionFile, "1");
	}

	@Test
	public void cleanup() throws Exception {
		Path file1 = mkfile(dbBackupDir, "db-20130101T000000.backup.zip");
		Path file2 = mkfile(dbBackupDir, "db-20130102T000000.backup.zip");
		Path file3 = mkfile(dbBackupDir, "db-20130103T000000.backup.zip");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.cleanup();

		assertFalse(Files.exists(file1));
		assertTrue(Files.exists(file2));
		assertTrue(Files.exists(file3));
	}

	@Test
	public void cleanup_no_backups() throws Exception {
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.cleanup();

		assertDirectoryContents(dbBackupDir);
	}

	@Test
	public void cleanup_backups_disabled() throws Exception {
		Path file1 = mkfile(dbBackupDir, "db-20130101T000000.backup.zip");
		Path file2 = mkfile(dbBackupDir, "db-20130102T000000.backup.zip");
		Path file3 = mkfile(dbBackupDir, "db-20130103T000000.backup.zip");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, false, 1, 2);
		bm.cleanup();

		assertTrue(Files.exists(file1));
		assertTrue(Files.exists(file2));
		assertTrue(Files.exists(file3));
	}

	@Test
	public void cleanup_no_max() throws Exception {
		Path file1 = mkfile(dbBackupDir, "db-20130101T000000.backup.zip");
		Path file2 = mkfile(dbBackupDir, "db-20130102T000000.backup.zip");
		Path file3 = mkfile(dbBackupDir, "db-20130103T000000.backup.zip");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, null);
		bm.cleanup();

		assertTrue(Files.exists(file1));
		assertTrue(Files.exists(file2));
		assertTrue(Files.exists(file3));
	}

	@Test
	public void delete() throws Exception {
		Path file1 = mkfile(dbBackupDir, "db-20130101T000000.backup.zip");
		Path file2 = mkfile(dbBackupDir, "db-20130102T000000.backup.zip");
		Path file3 = mkfile(dbBackupDir, "db-20130103T000000.backup.zip");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.delete(LocalDateTime.of(2013, 1, 2, 0, 0, 0));

		assertTrue(Files.exists(file1));
		assertFalse(Files.exists(file2));
		assertTrue(Files.exists(file3));
	}

	@Test
	public void delete_non_existent_backup() throws Exception {
		Path file1 = mkfile(dbBackupDir, "db-20130101T000000.backup.zip");
		Path file2 = mkfile(dbBackupDir, "db-20130102T000000.backup.zip");
		Path file3 = mkfile(dbBackupDir, "db-20130103T000000.backup.zip");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		try {
			bm.delete(LocalDateTime.of(2013, 6, 20, 0, 0, 0));
			fail();
		} catch (NoSuchFileException expected) {
		}

		assertTrue(Files.exists(file1));
		assertTrue(Files.exists(file2));
		assertTrue(Files.exists(file3));
	}

	@Test
	public void shouldBackup_no_database() throws Exception {
		Files.delete(dbDir);
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertFalse(bm.shouldBackup());
	}

	@Test
	public void shouldBackup_backups_disabled() throws Exception {
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, false, 1, 2);
		assertFalse(bm.shouldBackup());
	}

	@Test
	public void shouldBackup_no_backups() throws Exception {
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertTrue(bm.shouldBackup());
	}

	@Test
	public void shouldBackup_frequency_exceeded() throws Exception {
		mkfile(dbBackupDir, "db-20130101T000000.backup.zip");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertTrue(bm.shouldBackup());
	}

	@Test
	public void shouldBackup_frequency_not_exceeded() throws Exception {
		DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
		mkfile(dbBackupDir, "db-" + df.format(LocalDateTime.now()) + ".backup.zip");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		assertFalse(bm.shouldBackup());
	}

	@Test
	public void backup() throws Exception {
		Path file1 = mkfile(dbDir, "file1");
		Path file2 = mkfile(dbDir, "file2");
		Path file3 = mkfile(dbDir, "file3");

		ZipListener listener = mock(ZipListener.class);
		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.backup(listener);

		verify(listener).onZippedFile(file1, 0);
		verify(listener).onZippedFile(file2, 0);
		verify(listener).onZippedFile(file3, 0);

		//original database should remain
		assertTrue(Files.exists(file1));
		assertTrue(Files.exists(file2));
		assertTrue(Files.exists(file3));

		List<Path> backups = Files.list(dbBackupDir).collect(Collectors.toList());
		assertEquals(1, backups.size());
		assertTrue(backups.get(0).getFileName().toString().matches("db-\\d{8}T\\d{6}\\.backup\\.zip"));
	}

	@Test
	public void backup_error() throws Exception {
		Path file1 = mkfile(dbDir, "file1");
		Path file2 = mkfile(dbDir, "file2");
		Path file3 = mkfile(dbDir, "file3");

		RuntimeException exception = new RuntimeException();
		ZipListener listener = mock(ZipListener.class);
		doThrow(exception).when(listener).onZippedFile(eq(file2), anyInt());

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		try {
			bm.backup(listener);
			fail();
		} catch (RuntimeException e) {
			assertSame(exception, e);
		}

		//original database should remain
		assertTrue(Files.exists(file1));
		assertTrue(Files.exists(file2));
		assertTrue(Files.exists(file3));

		//ZIP file should have been deleted
		assertDirectoryContents(dbBackupDir);
	}

	@Test
	public void restore() throws Exception {
		Path file1 = mkfile(dbDir, "file1");
		Path file2 = mkfile(dbDir, "file2");
		Path file3 = mkfile(dbDir, "file3");

		Path backupFile = dbBackupDir.resolve("db-20130101T000000.backup.zip");
		try (FileSystem zip = ZipUtils.openNewZipFile(backupFile)) {
			Path folder = zip.getPath(dbDir.getFileName().toString());
			Files.createDirectory(folder);

			mkfile(folder, "restorefile1");
			mkfile(folder, "restorefile2");
			mkfile(folder, "restorefile3");
		}

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		bm.restore(LocalDateTime.of(2013, 1, 1, 0, 0, 0));

		assertDirectoryContents(root, dbDir.getFileName().toString(), dbBackupDir.getFileName().toString());

		//check for backup DB files
		assertTrue(Files.exists(dbDir.resolve("restorefile1")));
		assertTrue(Files.exists(dbDir.resolve("restorefile2")));
		assertTrue(Files.exists(dbDir.resolve("restorefile3")));

		//backup ZIP should remain
		assertTrue(Files.exists(backupFile));

		//original database should have been deleted
		assertFalse(Files.exists(file1));
		assertFalse(Files.exists(file2));
		assertFalse(Files.exists(file3));
	}

	@Test
	public void restore_non_existent_backup() throws Exception {
		Path file1 = mkfile(dbDir, "file1");
		Path file2 = mkfile(dbDir, "file2");
		Path file3 = mkfile(dbDir, "file3");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		try {
			bm.restore(LocalDateTime.of(2013, 1, 1, 0, 0, 0));
			fail();
		} catch (FileSystemNotFoundException expected) {
		}

		assertDirectoryContents(root, dbDir.getFileName().toString(), dbBackupDir.getFileName().toString());

		//original database should not have been deleted
		assertTrue(Files.exists(file1));
		assertTrue(Files.exists(file2));
		assertTrue(Files.exists(file3));
	}

	@Test
	public void getBackupDates() throws Exception {
		mkfile(dbBackupDir, "db-20130101T000000.backup.zip");
		mkfile(dbBackupDir, "db-20130102T000000.backup.zip");
		mkfile(dbBackupDir, "db-20130103T000000.backup.zip");
		mkfile(dbBackupDir, "foo.txt");

		BackupManager bm = new BackupManager(dbDir, dbBackupDir, true, 1, 2);
		List<LocalDateTime> actual = bm.getBackupDates();
		List<LocalDateTime> expected = Arrays.asList( //@formatter:off
			LocalDateTime.of(2013, 1, 3, 0, 0, 0),
			LocalDateTime.of(2013, 1, 2, 0, 0, 0),
			LocalDateTime.of(2013, 1, 1, 0, 0, 0)
		); //@formatter:on
		assertEquals(expected, actual);
	}

	private static void assertDirectoryContents(Path directory, String... expectedFileAndFolderNames) throws IOException {
		Set<String> actual = Files.list(directory).map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
		Set<String> expected = new HashSet<>(Arrays.asList(expectedFileAndFolderNames));
		assertEquals(expected, actual);
	}
}
