package emcshop.db;

import java.sql.SQLException;

/**
 * Data access object implementation for embedded Derby database.
 * @author Michael Angstadt
 */
public class DirbyMemoryDbDao extends DirbyDbDao {
	public DirbyMemoryDbDao(String dbName) throws SQLException {
		this(dbName, null);
	}

	public DirbyMemoryDbDao(String dbName, DbListener listener) throws SQLException {
		init("jdbc:derby:memory:" + dbName, true, listener);
	}
}