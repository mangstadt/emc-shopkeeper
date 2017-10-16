package emcshop.db;

public class Inventory {
    private Integer id;
    private Integer itemId;
    private String item;
    private Integer quantity;
    private Integer lowInStockThreshold = 0;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getLowInStockThreshold() {
        return lowInStockThreshold;
    }

    public void setLowInStockThreshold(Integer threshold) {
        this.lowInStockThreshold = threshold;
    }
}
