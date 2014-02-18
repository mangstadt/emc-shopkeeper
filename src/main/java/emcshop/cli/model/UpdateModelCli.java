package emcshop.cli.model;

import emcshop.db.DbDao;
import emcshop.model.UpdateModelImpl;
import emcshop.scraper.EmcSession;
import emcshop.scraper.TransactionPuller;

public class UpdateModelCli extends UpdateModelImpl {
	public UpdateModelCli(TransactionPuller.Config pullerConfig, EmcSession session, DbDao dao) {
		super(pullerConfig, session, dao);
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
