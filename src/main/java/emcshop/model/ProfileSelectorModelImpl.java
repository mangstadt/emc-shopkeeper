package emcshop.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileSelectorModelImpl implements IProfileSelectorModel {
	private final Path profileRootDir;

	/**
	 * @param profileRootDir the directory where the profiles are kept
	 */
	public ProfileSelectorModelImpl(Path profileRootDir) {
		this.profileRootDir = profileRootDir;
	}

	@Override
	public List<String> getAvailableProfiles() {
		try {
			return Files.list(profileRootDir) //@formatter:off
				.filter(Files::isDirectory)
				.map(Path::getFileName)
				.map(Path::toString)
				.sorted()
			.collect(Collectors.toList()); //@formatter:on
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean createProfile(String profile) {
		Path profileDir = profileRootDir.resolve(profile);
		if (Files.isDirectory(profileDir)) {
			//already created
			return true;
		}

		try {
			Files.createDirectory(profileDir);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
