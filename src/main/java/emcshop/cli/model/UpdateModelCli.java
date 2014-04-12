package emcshop.cli.model;

import emcshop.db.DbDao;
import emcshop.model.UpdateModelImpl;
import emcshop.scraper.EmcSession;
import emcshop.scraper.TransactionPullerFactory;

public class UpdateModelCli extends UpdateModelImpl {
	public UpdateModelCli(TransactionPullerFactory pullerFactory, EmcSession session, DbDao dao) {
		super(pullerFactory, session, dao);
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
