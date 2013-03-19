package emcshop.db;

import java.sql.SQLException;

/**
 * Data access object implementation for embedded Derby database.
 * @author Michael Angstadt
 */
public class DirbyMemoryDbDao extends DirbyDbDao {
	public DirbyMemoryDbDao() throws SQLException {
		init("jdbc:derby:memory:emc-shopkeeper", true, null);
	}

	@Override
	protected void deleteDatabase() {
		//do nothing
	}
}