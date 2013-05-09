CREATE TABLE payment_transactions(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	ts TIMESTAMP NOT NULL,
	player SMALLINT NOT NULL REFERENCES players(id),
	amount INT NOT NULL,
	balance INT NOT NULL,
	"transaction" INT REFERENCES transactions(id),
	ignore BOOLEAN NOT NULL DEFAULT false
);