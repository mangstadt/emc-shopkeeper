package emcshop.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.beans.PropertyVetoException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import com.michaelbaranov.microba.calendar.DatePicker;

/**
 * Contains information about a window's state (such as size and position), as
 * well as the values of its components (such as textboxes).
 * @author Michael Angstadt
 */
public class WindowState {
	private final Map<String, Object> componentValues;
	private final Point location;
	private final Dimension size;
	private final Integer state;

	/**
	 * Constructs a new collection of window state information.
	 * @param componentValues the component values
	 * @param location the window location
	 * @param size the window size
	 */
	public WindowState(Map<String, Object> componentValues, Point location, Dimension size, Integer state) {
		this.componentValues = Collections.unmodifiableMap(componentValues);
		this.location = location;
		this.size = size;
		this.state = state;
	}

	/**
	 * Extracts the window state information from a window.
	 * @param dialog the window
	 * @return the window state information
	 */
	public static WindowState of(JDialog dialog) {
		Map<String, Object> componentValues = getComponentValues(dialog.getContentPane());
		return new WindowState(componentValues, dialog.getLocation(), dialog.getSize(), null);
	}

	/**
	 * Extracts the window state information from a window.
	 * @param frame the window
	 * @return the window state information
	 */
	public static WindowState of(JFrame frame) {
		Map<String, Object> componentValues = getComponentValues(frame.getContentPane());
		return new WindowState(componentValues, frame.getLocation(), frame.getSize(), frame.getExtendedState());
	}

	private static Map<String, Object> getComponentValues(Container container) {
		Map<String, Object> componentValues = new HashMap<String, Object>();
		for (Component component : new RecursiveComponentIterator(container)) {
			String name = component.getName();
			if (name == null) {
				continue;
			}

			Object value;
			if (component instanceof JTextField) {
				value = ((JTextField) component).getText();
			} else if (component instanceof JCheckBox) {
				value = ((JCheckBox) component).isSelected();
			} else if (component instanceof JRadioButton) {
				value = ((JRadioButton) component).isSelected();
			} else if (component instanceof DatePicker) {
				value = ((DatePicker) component).getDate();
			} else {
				continue;
			}

			componentValues.put(name, value);
		}

		return componentValues;
	}

	/**
	 * Applies the window state information in this object to the given window.
	 * @param frame the window
	 */
	public void applyTo(JFrame frame) {
		applyComponentValues(frame.getContentPane());

		if (size != null) {
			frame.setSize(size);
		}
		if (location != null) {
			frame.setLocation(location);
		} else {
			frame.setLocationRelativeTo(null);
		}
		if (state != null) {
			frame.setExtendedState(state);
		}
	}

	/**
	 * Applies the window state information in this object to the given window.
	 * @param dialog the window
	 */
	public void applyTo(JDialog dialog) {
		applyComponentValues(dialog.getContentPane());

		if (size != null) {
			dialog.setSize(size);
		}
		if (location != null) {
			dialog.setLocation(location);
		} else {
			dialog.setLocationRelativeTo(null);
		}
	}

	private void applyComponentValues(Container container) {
		for (Component component : new RecursiveComponentIterator(container)) {
			String name = component.getName();
			if (name == null) {
				continue;
			}

			Object value = componentValues.get(name);
			if (value == null) {
				continue;
			}

			if (component instanceof JTextField && value instanceof String) {
				((JTextField) component).setText((String) value);
			} else if (component instanceof JCheckBox && value instanceof Boolean) {
				((JCheckBox) component).setSelected((Boolean) value);
			} else if (component instanceof JRadioButton && value instanceof Boolean) {
				((JRadioButton) component).setSelected((Boolean) value);
			} else if (component instanceof DatePicker && value instanceof Date) {
				try {
					((DatePicker) component).setDate((Date) value);
				} catch (PropertyVetoException e) {
					//should never be thrown
				}
			}
		}
	}

	/**
	 * Gets the values of the window's components.
	 * @return the values (the key is the {@link Component#setName(String)
	 * component's name}, the value is its value)
	 */
	public Map<String, Object> getComponentValues() {
		return componentValues;
	}

	/**
	 * Gets the locaton of the window on the screen.
	 * @return the window location
	 */
	public Point getLocation() {
		return location;
	}

	/**
	 * Gets the window's size.
	 * @return the window's size
	 */
	public Dimension getSize() {
		return size;
	}

	/**
	 * Gets the state of the window (e.g. if it's maximized)
	 * @return the window state
	 */
	public Integer getState() {
		return state;
	}
}
