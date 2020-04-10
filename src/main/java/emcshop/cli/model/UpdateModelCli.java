package emcshop.cli.model;

import java.time.Duration;

import com.github.mangstadt.emc.rupees.RupeeTransactionReader;

import emcshop.model.UpdateModelImpl;

public class UpdateModelCli extends UpdateModelImpl {
	public UpdateModelCli(RupeeTransactionReader.Builder builder, Duration oldestAllowablePaymentTransactionAge) {
		super(builder, oldestAllowablePaymentTransactionAge);
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
