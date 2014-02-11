package emcshop.db;

public interface DbListener {
	/**
	 * Called when the database doesn't exist and has to be created.
	 */
	void onCreate();

	/**
	 * Called when a database is being migrated to a new version.
	 * @param oldVersion the existing database version
	 * @param newVersion the new database version
	 */
	void onMigrate(int oldVersion, int newVersion);
}
