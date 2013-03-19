package emcshop.db;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;

/**
 * Data access object implementation for embedded Derby database.
 * @author Michael Angstadt
 */
public class DirbyEmbeddedDbDao extends DirbyDbDao {
	private final File databaseDir;

	/**
	 * @param databaseDir the directory the database will be saved to
	 * @throws SQLException
	 */
	public DirbyEmbeddedDbDao(File databaseDir) throws SQLException {
		this(databaseDir, null);
	}

	/**
	 * @param databaseDir the directory the database will be saved to
	 * @param listener
	 * @throws SQLException
	 */
	public DirbyEmbeddedDbDao(File dir, DbListener listener) throws SQLException {
		databaseDir = new File(dir.getAbsolutePath());
		System.setProperty("derby.system.home", databaseDir.getParentFile().getAbsolutePath());
		init("jdbc:derby:" + databaseDir.getName(), !databaseDir.isDirectory(), listener);
	}

	@Override
	protected void deleteDatabase() throws IOException {
		FileUtils.deleteDirectory(databaseDir);
	}
}