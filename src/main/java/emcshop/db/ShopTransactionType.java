package emcshop.db;

/**
 * Used when querying shop transactions
 * @author Michael Angstadt
 */
public enum ShopTransactionType {
	/**
	 * Only include the transactions that occurred when people bought or sold
	 * from your own shop.
	 */
	MY_SHOP,

	/**
	 * Only include the shop transactions that occurred when you bought or sold
	 * to someone else's shop.
	 */
	OTHER_SHOPS,

	/**
	 * Include all shop transactions.
	 */
	ALL
}
