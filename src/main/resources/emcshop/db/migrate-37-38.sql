ALTER TABLE payment_transactions ALTER reason SET DATA TYPE VARCHAR(256);

CALL UPDATE_ITEM_NAMES();
CALL POPULATE_ITEMS_TABLE();