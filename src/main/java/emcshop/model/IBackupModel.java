package emcshop.model;

import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.util.List;

public interface IBackupModel {
	void addBackupCompleteListener(ActionListener listener);

	void addBackupPercentCompleteListener(ActionListener listener);

	void addRestoreCompleteListener(ActionListener listener);

	boolean getAutoBackupEnabled();

	Integer getBackupFrequency();

	Integer getMaxBackups();

	void setAutoBackupEnabled(boolean enabled);

	void setBackupFrequency(Integer days);

	void setMaxBackups(Integer days);

	List<LocalDateTime> getBackups();

	void saveSettings();

	Thread startBackup();

	Thread startRestore(LocalDateTime date);

	void deleteBackup(LocalDateTime date);
}
