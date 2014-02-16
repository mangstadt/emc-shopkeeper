package emcshop.model;

import emcshop.Main;

public class FirstUpdateModelImpl implements IFirstUpdateModel {
	@Override
	public long getEstimatedTime(Integer stopAtPage) {
		return Main.estimateUpdateTime(stopAtPage);
	}
}
