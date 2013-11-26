--in the previous version, if the database was wiped, the "items" table was not re-populated
--make sure the table is populated incase the user wiped the database
CALL POPULATE_ITEMS_TABLE();