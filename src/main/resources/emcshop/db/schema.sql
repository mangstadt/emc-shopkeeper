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
	name VARCHAR(64) NOT NULL
);

CREATE TABLE transactions(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	ts TIMESTAMP NOT NULL,
	player SMALLINT NOT NULL REFERENCES players(id),
	item SMALLINT NOT NULL REFERENCES items(id),
	amount SMALLINT NOT NULL, --the amount earned/lost in the transaction
	quantity SMALLINT NOT NULL, --negative=player bought from you, positive=player sold to you
	balance INT NOT NULL
);