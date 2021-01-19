--Treat all "Grass" transactions as if they were for "Grass Blocks", since "Grass Blocks" were probably sold more often
--There's no way to differentiate between the two items due to the way the items were mapped in item.xml (which has now been fixed)
--(Since 1.13, the transaction history used "Grass Block" for the block and "Grass" for grass item, so EMC Shopkeeper was treating all Grass items as if they were blocks due to an incorrect mapping)
--Old mapping: <Item name="Grass" emcNames="Grass Block" /> <Item name="Long Grass" emcNames="Tall Grass" />
--Fix this by hand because UPDATE_ITEM_NAMES() cannot handle this situation
UPDATE items SET name='Grass Block' WHERE name='Grass';
UPDATE items SET name='Grass' WHERE name='Long Grass';

CALL UPDATE_ITEM_NAMES();
CALL POPULATE_ITEMS_TABLE();