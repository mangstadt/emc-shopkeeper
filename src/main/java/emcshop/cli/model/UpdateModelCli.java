package emcshop.cli.model;

import emcshop.model.UpdateModelImpl;
import emcshop.rupees.RupeeTransactionReader;

public class UpdateModelCli extends UpdateModelImpl {
    public UpdateModelCli(RupeeTransactionReader.Builder builder, Integer oldestAllowablePaymentTransactionAge) {
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
