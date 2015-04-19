package emcshop.gui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.swing.JPanel;

/**
 * Iterates over all the components in a container, recusively including the
 * components of {@link JPanel}s.
 * @author Michael Angstadt
 */
public class RecursiveComponentIterator implements Iterator<Component>, Iterable<Component> {
	private final List<Component> components = new ArrayList<Component>();
	private int curIndex = 0;
	private Component next;

	/**
	 * @param container the container to retrieve the components from
	 */
	public RecursiveComponentIterator(Container container) {
		components.addAll(Arrays.asList(container.getComponents()));
		next = getNext();
	}

	@Override
	public Iterator<Component> iterator() {
		return this;
	}

	@Override
	public boolean hasNext() {
		return next != null;
	}

	@Override
	public Component next() {
		Component next = this.next;
		this.next = getNext();
		return next;
	}

	private Component getNext() {
		Component component = nextComponent();
		while (component instanceof JPanel) {
			JPanel panel = (JPanel) component;
			components.addAll(Arrays.asList(panel.getComponents()));
			component = nextComponent();
		}
		return component;
	}

	private Component nextComponent() {
		return (curIndex < components.size()) ? components.get(curIndex++) : null;
	}

	@Override
	public void remove() {
		components.remove(curIndex);
	}
}