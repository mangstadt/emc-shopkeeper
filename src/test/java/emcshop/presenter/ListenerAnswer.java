package emcshop.presenter;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Used during the unit-testing of presenters to record the
 * {@link ActionListener} objects that a presenter sends to a view or model.
 */
public class ListenerAnswer implements Answer<Object> {
	private final List<ActionListener> listeners = new ArrayList<>();

	@Override
	public Object answer(InvocationOnMock invocation) {
		ActionListener listener = (ActionListener) invocation.getArguments()[0];
		listeners.add(listener);
		return null;
	}

	/**
	 * Fires all of the recorded {@link ActionListener}s.
	 */
	public void fire() {
		for (ActionListener listener : listeners) {
			listener.actionPerformed(null);
		}
	}
}
