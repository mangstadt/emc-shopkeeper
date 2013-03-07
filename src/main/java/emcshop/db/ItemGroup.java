package emcshop.db;

public class ItemGroup {
	private String item;
	private int boughtQuantity, soldQuantity;
	private int boughtAmount, soldAmount;

	public String getItem() {
		return item;
	}

	public void setItem(String item) {
		this.item = item;
	}

	public int getBoughtQuantity() {
		return boughtQuantity;
	}

	public void setBoughtQuantity(int boughtQuantity) {
		this.boughtQuantity = boughtQuantity;
	}

	public int getSoldQuantity() {
		return soldQuantity;
	}

	public void setSoldQuantity(int soldQuantity) {
		this.soldQuantity = soldQuantity;
	}

	public int getBoughtAmount() {
		return boughtAmount;
	}

	public void setBoughtAmount(int boughtAmount) {
		this.boughtAmount = boughtAmount;
	}

	public int getSoldAmount() {
		return soldAmount;
	}

	public void setSoldAmount(int soldAmount) {
		this.soldAmount = soldAmount;
	}

	public int getNetQuantity() {
		return boughtQuantity + soldQuantity;
	}

	public int getNetAmount() {
		return boughtAmount + soldAmount;
	}

}
