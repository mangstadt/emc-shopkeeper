package emcshop.model;

import java.awt.event.ActionListener;
import java.util.Date;
import java.util.List;

public interface IBackupModel {
	void addBackupCompleteListener(ActionListener listener);

	void addBackupPercentCompleteListener(ActionListener listener);

	boolean getAutoBackupEnabled();

	Integer getBackupFrequency();

	Integer getMaxBackups();

	void setAutoBackupEnabled(boolean enabled);

	void setBackupFrequency(Integer days);

	void setMaxBackups(Integer days);

	List<Date> getBackups();

	void saveSettings();

	Thread startBackup();
}
