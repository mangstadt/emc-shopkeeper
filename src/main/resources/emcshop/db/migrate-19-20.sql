CALL UPDATE_ITEM_NAMES();
CALL POPULATE_ITEMS_TABLE();

CREATE TABLE other_shop_transactions(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	ts TIMESTAMP NOT NULL,
	owner SMALLINT NOT NULL REFERENCES players(id),
	item SMALLINT NOT NULL REFERENCES items(id),
	
	--the amount lost/earned in the transaction
	--(negative=you bought from the shop, positive=you sold to the shop)
	amount INT NOT NULL,
	
	--the quantity bought/sold
	--(negative=you sold to the shop, positive=you bought from the shop)
	quantity INT NOT NULL,
	
	--the player's rupee balance after the transaction occurred
	balance INT NOT NULL
);

CREATE INDEX owner_index ON other_shop_transactions(owner);
CREATE INDEX item_index3 ON other_shop_transactions(item);