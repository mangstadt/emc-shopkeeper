CREATE PROCEDURE UPDATE_ITEM_NAMES()
LANGUAGE JAVA PARAMETER STYLE JAVA
MODIFIES SQL DATA
EXTERNAL NAME 'emcshop.db.MigrationSprocs.updateItemNames';

CREATE PROCEDURE POPULATE_ITEMS_TABLE()
LANGUAGE JAVA PARAMETER STYLE JAVA
MODIFIES SQL DATA
EXTERNAL NAME 'emcshop.db.MigrationSprocs.populateItemsTable';

CALL UPDATE_ITEM_NAMES();
CALL POPULATE_ITEMS_TABLE();