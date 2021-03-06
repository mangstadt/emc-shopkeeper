package emcshop.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProfileSelectorModelImplTest {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void getAvailableProfiles() throws Exception {
		temp.newFolder("one");
		temp.newFolder("two");
		temp.newFolder("three");
		temp.newFolder("four");
		temp.newFile("five");

		ProfileSelectorModelImpl model = new ProfileSelectorModelImpl(temp.getRoot().toPath());
		List<String> expected = Arrays.asList("four", "one", "three", "two");
		List<String> actual = model.getAvailableProfiles();
		assertEquals(expected, actual);
	}

	@Test
	public void createProfile() throws Exception {
		temp.newFolder("one");
		temp.newFile("two");

		ProfileSelectorModelImpl model = new ProfileSelectorModelImpl(temp.getRoot().toPath());
		assertTrue(model.createProfile("one"));
		assertFalse(model.createProfile("two"));
		assertTrue(model.createProfile("three"));
		assertTrue(Files.isDirectory(temp.getRoot().toPath().resolve("three")));
	}
}
