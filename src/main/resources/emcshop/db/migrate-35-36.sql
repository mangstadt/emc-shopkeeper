ALTER TABLE bonuses_fees ADD highest_balance INT DEFAULT 0 NOT NULL;
ALTER TABLE bonuses_fees ADD highest_balance_ts TIMESTAMP;

CREATE PROCEDURE FIND_HIGHEST_BALANCE()
LANGUAGE JAVA PARAMETER STYLE JAVA
MODIFIES SQL DATA
EXTERNAL NAME 'emcshop.db.MigrationSprocs.findHighestBalance';

CALL FIND_HIGHEST_BALANCE();

DROP PROCEDURE FIND_HIGHEST_BALANCE;

CALL UPDATE_ITEM_NAMES();
CALL POPULATE_ITEMS_TABLE();