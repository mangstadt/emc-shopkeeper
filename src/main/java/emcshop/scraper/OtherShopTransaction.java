package emcshop.scraper;

/**
 * Represents a transaction that occurred when the player bought or sold from/to
 * another player's shop.
 */
public class OtherShopTransaction extends RupeeTransaction {
	private Integer id;
	private String shopOwner;
	private String item;
	private int quantity;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getShopOwner() {
		return shopOwner;
	}

	public void setShopOwner(String shopOwner) {
		this.shopOwner = shopOwner;
	}

	public String getItem() {
		return item;
	}

	public void setItem(String item) {
		this.item = item;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((item == null) ? 0 : item.hashCode());
		result = prime * result + ((shopOwner == null) ? 0 : shopOwner.hashCode());
		result = prime * result + quantity;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!super.equals(obj)) return false;
		if (getClass() != obj.getClass()) return false;
		OtherShopTransaction other = (OtherShopTransaction) obj;
		if (id == null) {
			if (other.id != null) return false;
		} else if (!id.equals(other.id)) return false;
		if (item == null) {
			if (other.item != null) return false;
		} else if (!item.equals(other.item)) return false;
		if (shopOwner == null) {
			if (other.shopOwner != null) return false;
		} else if (!shopOwner.equals(other.shopOwner)) return false;
		if (quantity != other.quantity) return false;
		return true;
	}
}
