CREATE PROCEDURE FIRST_LAST_SEEN()
LANGUAGE JAVA PARAMETER STYLE JAVA
MODIFIES SQL DATA
EXTERNAL NAME 'emcshop.db.MigrationSprocs.calculatePlayersFirstLastSeenDates';

CALL FIRST_LAST_SEEN();

ALTER TABLE meta ADD rupee_balance INTEGER DEFAULT 0 NOT NULL;