ALTER TABLE payment_transactions ADD reason VARCHAR(128);

CREATE PROCEDURE FIX_REASON()
LANGUAGE JAVA PARAMETER STYLE JAVA
MODIFIES SQL DATA
EXTERNAL NAME 'emcshop.db.MigrationSprocs.fixPaymentTransactionReason';

CALL FIX_REASON();

DROP PROCEDURE FIX_REASON;