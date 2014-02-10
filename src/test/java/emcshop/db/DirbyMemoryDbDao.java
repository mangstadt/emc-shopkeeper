package emcshop.db;

import java.sql.SQLException;

/**
 * Data access object implementation for embedded Derby database.
 * @author Michael Angstadt
 */
public class DirbyMemoryDbDao extends DirbyDbDao {
	public DirbyMemoryDbDao(String dbName) throws SQLException {
		init("jdbc:derby:memory:" + dbName, true, null);
	}
}