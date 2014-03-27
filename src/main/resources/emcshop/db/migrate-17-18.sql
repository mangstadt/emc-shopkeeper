CALL UPDATE_ITEM_NAMES();
CALL POPULATE_ITEMS_TABLE();

CREATE TABLE update_log (
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	ts TIMESTAMP NOT NULL,
	rupee_balance INT NOT NULL,
	transaction_count INT NOT NULL,
	payment_transaction_count INT NOT NULL,
	bonus_fee_transaction_count INT NOT NULL,
	time_taken INT NOT NULL
);
CREATE INDEX update_log_ts_index ON update_log(ts);

ALTER TABLE meta drop rupee_balance;