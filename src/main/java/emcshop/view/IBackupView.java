package emcshop.view;

import java.awt.event.ActionListener;
import java.util.Date;
import java.util.List;

public interface IBackupView {
	void addStartBackupListener(ActionListener listener);

	void addRestoreBackupListener(ActionListener listener);

	void addSaveSettingsListener(ActionListener listener);

	void addCancelListener(ActionListener listener);

	boolean getAutoBackupEnabled();

	Integer getBackupFrequency();

	Integer getMaxBackups();

	void setAutoBackupEnabled(boolean enabled);

	void setBackupFrequency(Integer days);

	void setMaxBackups(Integer days);

	void setBackups(List<Date> backups);

	void setBackupPercentComplete(double percent);

	void backupComplete();

	void invalidFrequency();

	void invalidMax();

	void display();

	void close();
}
