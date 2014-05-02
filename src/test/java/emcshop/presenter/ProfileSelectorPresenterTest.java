package emcshop.presenter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import emcshop.model.IProfileSelectorModel;
import emcshop.view.IProfileSelectorView;

public class ProfileSelectorPresenterTest {
	@Test
	public void populate_profile_names() {
		IProfileSelectorView view = mock(IProfileSelectorView.class);

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		List<String> profiles = Arrays.asList("one", "two", "three");
		when(model.getAvailableProfiles()).thenReturn(profiles);

		new ProfileSelectorPresenter(view, model);
		verify(view).setAvailableProfiles(profiles);
		verify(view).display();
	}

	@Test
	public void select_profile() {
		IProfileSelectorView view = mock(IProfileSelectorView.class);
		ListenerAnswer clickOk = new ListenerAnswer();
		doAnswer(clickOk).when(view).addProfileSelectedListener(any(ActionListener.class));
		when(view.getSelectedProfile()).thenReturn("one");

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		when(model.createProfile("one")).thenReturn(true);

		ProfileSelectorPresenter presenter = new ProfileSelectorPresenter(view, model);
		clickOk.fire();

		verify(view).close();
		assertEquals("one", presenter.getSelectedProfile());
	}

	@Test
	public void null_profile_name() {
		IProfileSelectorView view = mock(IProfileSelectorView.class);
		ListenerAnswer clickOk = new ListenerAnswer();
		doAnswer(clickOk).when(view).addProfileSelectedListener(any(ActionListener.class));
		when(view.getSelectedProfile()).thenReturn(null);

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		when(model.createProfile("one")).thenReturn(true);

		new ProfileSelectorPresenter(view, model);
		clickOk.fire();

		verify(view).showValidationError(anyString());
		verify(view, never()).close();
	}

	@Test
	public void empty_profile_name() {
		IProfileSelectorView view = mock(IProfileSelectorView.class);
		ListenerAnswer clickOk = new ListenerAnswer();
		doAnswer(clickOk).when(view).addProfileSelectedListener(any(ActionListener.class));
		when(view.getSelectedProfile()).thenReturn("");

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		when(model.createProfile("one")).thenReturn(true);

		new ProfileSelectorPresenter(view, model);
		clickOk.fire();

		verify(view).showValidationError(anyString());
		verify(view, never()).close();
	}

	@Test
	public void could_not_create_profile() {
		IProfileSelectorView view = mock(IProfileSelectorView.class);
		ListenerAnswer clickOk = new ListenerAnswer();
		doAnswer(clickOk).when(view).addProfileSelectedListener(any(ActionListener.class));
		when(view.getSelectedProfile()).thenReturn("one");

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		when(model.createProfile("one")).thenReturn(false);

		new ProfileSelectorPresenter(view, model);
		clickOk.fire();

		verify(view).showValidationError(anyString());
		verify(view, never()).close();
	}

	@Test
	public void cancel() {
		IProfileSelectorView view = mock(IProfileSelectorView.class);
		ListenerAnswer clickCancel = new ListenerAnswer();
		doAnswer(clickCancel).when(view).addCancelListener(any(ActionListener.class));

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		List<String> profiles = Arrays.asList("one", "two", "three");
		when(model.getAvailableProfiles()).thenReturn(profiles);

		ProfileSelectorPresenter presenter = new ProfileSelectorPresenter(view, model);
		clickCancel.fire();

		verify(view).close();
		assertNull(presenter.getSelectedProfile());
	}
}
