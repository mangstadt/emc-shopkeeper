/*
 * This SQL script will always contain the full database schema for the most
 * recent version of the database.
 * 
 * Every time the schema changes:
 * 1. Create a migration script (e.g. "migrate-5-6.sql").
 * 2. Update the version number in DirbyDbDao.
 */

CREATE TABLE meta(
	--the database schema version
	--used for updating existing databases as future versions are released
	db_schema_version INTEGER NOT NULL
);

CREATE TABLE items(
	id SMALLINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	name VARCHAR(64) NOT NULL
);

CREATE TABLE players(
	id SMALLINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	name VARCHAR(64) NOT NULL,
	
	--the date of the player's first transaction
	first_seen TIMESTAMP,
	
	--the date of the player's latest transaction
	last_seen TIMESTAMP
);

CREATE TABLE transactions(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	ts TIMESTAMP NOT NULL,
	player SMALLINT NOT NULL REFERENCES players(id),
	item SMALLINT NOT NULL REFERENCES items(id),
	
	--the amount lost/earned in the transaction
	--(negative=player sold to you, positive=player bought from you)
	amount INT NOT NULL,
	
	--the quantity bought/sold
	--(negative=player bought from you, positive=player sold to you)
	quantity INT NOT NULL,
	
	--the player's rupee balance after the transaction occurred
	balance INT NOT NULL
);

CREATE INDEX ts_index ON transactions(ts);
CREATE INDEX player_index ON transactions(player);
CREATE INDEX item_index ON transactions(item);
