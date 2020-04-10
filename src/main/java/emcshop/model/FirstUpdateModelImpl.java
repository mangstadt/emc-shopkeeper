package emcshop.model;

import java.time.Duration;

import emcshop.EMCShopkeeper;

public class FirstUpdateModelImpl implements IFirstUpdateModel {
	@Override
	public Duration getEstimatedTime(Integer stopAtPage) {
		return EMCShopkeeper.estimateUpdateTime(stopAtPage);
	}
}
