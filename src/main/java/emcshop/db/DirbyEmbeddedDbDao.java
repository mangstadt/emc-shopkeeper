package emcshop.db;

import java.io.File;
import java.sql.SQLException;

/**
 * Data access object implementation for embedded Derby database.
 * @author Michael Angstadt
 */
public class DirbyEmbeddedDbDao extends DirbyDbDao {
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
	public DirbyEmbeddedDbDao(File databaseDir, DbListener listener) throws SQLException {
		databaseDir = new File(databaseDir.getAbsolutePath());
		System.setProperty("derby.system.home", databaseDir.getParentFile().getAbsolutePath());
		init("jdbc:derby:" + databaseDir.getName(), !databaseDir.isDirectory(), listener);
	}
}
