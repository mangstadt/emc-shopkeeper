package emcshop.db;

import java.nio.file.Files;
import java.nio.file.Path;
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
	public DirbyEmbeddedDbDao(Path databaseDir) throws SQLException {
		this(databaseDir, null);
	}

	/**
	 * @param databaseDir the directory the database will be saved to
	 * @param listener implement this to be notified of various points in the
	 * startup process
	 * @throws SQLException if there's a problem starting the database
	 */
	public DirbyEmbeddedDbDao(Path databaseDir, DbListener listener) throws SQLException {
		databaseDir = databaseDir.toAbsolutePath();
		System.setProperty("derby.system.home", databaseDir.getParent().toAbsolutePath().toString());
		init("jdbc:derby:" + databaseDir.getFileName(), !Files.isDirectory(databaseDir), listener);
	}

	/**
	 * @param connection the connection to wrap
	 */
	public DirbyEmbeddedDbDao(Connection connection) {
		init(connection);
	}
}