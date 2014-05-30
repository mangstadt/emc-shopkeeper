CALL UPDATE_ITEM_NAMES();
CALL POPULATE_ITEMS_TABLE();

ALTER TABLE transactions ALTER player NULL;
ALTER TABLE transactions ADD shop_owner FOREIGN KEY players(id);
CREATE INDEX shop_owner_index ON transactions(shop_owner);