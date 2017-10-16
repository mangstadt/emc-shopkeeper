package emcshop.rupees.dto;

/**
 * Represents a shop transaction.
 *
 * @author Michael Angstadt
 */
public class ShopTransaction extends RupeeTransaction {
    private final String shopCustomer, shopOwner, item;
    private final int quantity;

    private ShopTransaction(Builder builder) {
        super(builder);
        shopCustomer = builder.shopCustomer;
        shopOwner = builder.shopOwner;
        item = builder.item;
        quantity = builder.quantity;
    }

    public String getShopCustomer() {
        return shopCustomer;
    }

    public String getShopOwner() {
        return shopOwner;
    }

    public String getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public static class Builder extends RupeeTransaction.Builder<Builder> {
        private String shopCustomer, shopOwner, item;
        private int quantity;

        public Builder() {
            //empty
        }

        public Builder(ShopTransaction orig) {
            super(orig);
            shopCustomer = orig.shopCustomer;
            shopOwner = orig.shopOwner;
            item = orig.item;
            quantity = orig.quantity;
        }

        public String shopCustomer() {
            return shopCustomer;
        }

        public Builder shopCustomer(String shopCustomer) {
            this.shopCustomer = shopCustomer;
            this.shopOwner = null;
            return this;
        }

        public String shopOwner() {
            return shopOwner;
        }

        public Builder shopOwner(String shopOwner) {
            this.shopOwner = shopOwner;
            this.shopCustomer = null;
            return this;
        }

        public String item() {
            return item;
        }

        public Builder item(String item) {
            this.item = item;
            return this;
        }

        public int quantity() {
            return quantity;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        @Override
        public ShopTransaction build() {
            return new ShopTransaction(this);
        }
    }
}