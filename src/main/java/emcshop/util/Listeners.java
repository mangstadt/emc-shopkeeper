package emcshop.util;

import java.awt.event.ActionListener;
import java.util.ArrayList;

@SuppressWarnings("serial")
public class Listeners extends ArrayList<ActionListener> {
	public Listeners() {
		super(1);
	}

	public void fire() {
		for (ActionListener listener : this) {
			listener.actionPerformed(null);
		}
	}
}
