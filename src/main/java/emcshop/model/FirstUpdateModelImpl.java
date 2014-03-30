package emcshop.model;

import emcshop.EMCShopkeeper;

public class FirstUpdateModelImpl implements IFirstUpdateModel {
	@Override
	public long getEstimatedTime(Integer stopAtPage) {
		return EMCShopkeeper.estimateUpdateTime(stopAtPage);
	}
}
