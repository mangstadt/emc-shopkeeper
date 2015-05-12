package emcshop.cli.model;

import emcshop.db.DbDao;
import emcshop.model.UpdateModelImpl;
import emcshop.scraper.TransactionPullerFactory;

public class UpdateModelCli extends UpdateModelImpl {
	public UpdateModelCli(TransactionPullerFactory pullerFactory, DbDao dao) {
		super(pullerFactory);
	}

	@Override
	public Thread startDownload() {
		Thread thread = super.startDownload();
		try {
			thread.join();
		} catch (InterruptedException e) {
			//ignore
		}
		return thread;
	}
}
