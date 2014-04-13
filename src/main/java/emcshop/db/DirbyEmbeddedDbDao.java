package emcshop.db;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Data access object implementation for embedded Derby database.
 * @author Michael Angstadt
 */
public class DirbyEmbeddedDbDao extends DirbyDbDao {
	/**
	 * @param databaseDir the directory the database will be saved to
	 * @throws SQLException if there's a problem starting the database
	 */
	public DirbyEmbeddedDbDao(File databaseDir) throws SQLException {
		this(databaseDir, null);
	}

	/**
	 * @param databaseDir the directory the database will be saved to
	 * @param listener implement this to be notified of various points in the
	 * startup process
	 * @throws SQLException if there's a problem starting the database
	 */
	public DirbyEmbeddedDbDao(File databaseDir, DbListener listener) throws SQLException {
		databaseDir = new File(databaseDir.getAbsolutePath());
		System.setProperty("derby.system.home", databaseDir.getParentFile().getAbsolutePath());
		init("jdbc:derby:" + databaseDir.getName(), !databaseDir.isDirectory(), listener);
	}

	/**
	 * @param connection the connection to wrap
	 */
	public DirbyEmbeddedDbDao(Connection connection) {
		init(connection);
	}
}