package emcshop.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfileSelectorModelImpl implements IProfileSelectorModel {
	private final File profileRootDir;

	/**
	 * @param profileRootDir the directory where the profiles are kept
	 */
	public ProfileSelectorModelImpl(File profileRootDir) {
		this.profileRootDir = profileRootDir;
	}

	@Override
	public List<String> getAvailableProfiles() {
		List<String> profiles = new ArrayList<String>();

		File files[] = profileRootDir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				profiles.add(file.getName());
			}
		}

		Collections.sort(profiles);
		return profiles;
	}

	@Override
	public boolean createProfile(String profile) {
		File profileDir = new File(profileRootDir, profile);
		if (profileDir.isDirectory()) {
			//already created
			return true;
		}

		return profileDir.mkdir();
	}
}
