package emcshop.presenter;

import static emcshop.util.TestUtils.fireEvents;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

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
		ProfileSelectorViewAdapter view = spy(new ProfileSelectorViewAdapter());
		when(view.getSelectedProfile()).thenReturn("one");

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		when(model.createProfile("one")).thenReturn(true);

		ProfileSelectorPresenter presenter = new ProfileSelectorPresenter(view, model);
		view.clickOk();

		verify(view).close();
		assertEquals("one", presenter.getSelectedProfile());
	}

	@Test
	public void null_profile_name() {
		ProfileSelectorViewAdapter view = spy(new ProfileSelectorViewAdapter());
		when(view.getSelectedProfile()).thenReturn(null);

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		when(model.createProfile("one")).thenReturn(true);

		new ProfileSelectorPresenter(view, model);
		view.clickOk();

		verify(view).showValidationError(Mockito.anyString());
		verify(view, never()).close();
	}

	@Test
	public void empty_profile_name() {
		ProfileSelectorViewAdapter view = spy(new ProfileSelectorViewAdapter());
		when(view.getSelectedProfile()).thenReturn("");

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		when(model.createProfile("one")).thenReturn(true);

		new ProfileSelectorPresenter(view, model);
		view.clickOk();

		verify(view).showValidationError(Mockito.anyString());
		verify(view, never()).close();
	}

	@Test
	public void could_not_create_profile() {
		ProfileSelectorViewAdapter view = spy(new ProfileSelectorViewAdapter());
		when(view.getSelectedProfile()).thenReturn("one");

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		when(model.createProfile("one")).thenReturn(false);

		new ProfileSelectorPresenter(view, model);
		view.clickOk();

		verify(view).showValidationError(Mockito.anyString());
		verify(view, never()).close();
	}

	@Test
	public void cancel() {
		ProfileSelectorViewAdapter view = spy(new ProfileSelectorViewAdapter());

		IProfileSelectorModel model = mock(IProfileSelectorModel.class);
		List<String> profiles = Arrays.asList("one", "two", "three");
		when(model.getAvailableProfiles()).thenReturn(profiles);

		ProfileSelectorPresenter presenter = new ProfileSelectorPresenter(view, model);
		view.clickCancel();

		verify(view).close();
		assertNull(presenter.getSelectedProfile());
	}

	private static class ProfileSelectorViewAdapter implements IProfileSelectorView {
		private final List<ActionListener> onOk = new ArrayList<ActionListener>();
		private final List<ActionListener> onCancel = new ArrayList<ActionListener>();

		public void clickOk() {
			fireEvents(onOk);
		}

		public void clickCancel() {
			fireEvents(onCancel);
		}

		@Override
		public void addProfileSelectedListener(ActionListener listener) {
			onOk.add(listener);
		}

		@Override
		public void addCancelListener(ActionListener listener) {
			onCancel.add(listener);
		}

		@Override
		public void setAvailableProfiles(List<String> profiles) {
		}

		@Override
		public String getSelectedProfile() {
			return null;
		}

		@Override
		public void showValidationError(String error) {
		}

		@Override
		public void close() {
		}

		@Override
		public void display() {
		}
	}
}
