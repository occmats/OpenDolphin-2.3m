/*      PANDA -- a simple transaction monitor

Copyright (C) 1998-1999 Ogochan.
2000-2003 Ogochan & JMA (Japan Medical Association).

This module is part of PANDA.

PANDA is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY.  No author or distributor accepts responsibility
to anyone for the consequences of using it or for whether it serves
any particular purpose or works at all, unless he says so in writing.
Refer to the GNU General Public License for full details.

Everyone is granted permission to copy, modify and redistribute
PANDA, but only under the conditions described in the GNU General
Public License.  A copy of this license is supposed to have been given
to you along with PANDA so you can know your rights and
responsibilities.  It should be in a file named COPYING.  Among other
things, the copyright notice and this notice must be preserved on all
copies.
*/

package org.montsuqi.widgets;

import java.awt.Container;
import javax.swing.JButton;

public class NotebookDummyButton extends JButton {

	private int index;
	private Notebook notebook;

	public NotebookDummyButton(String label, int index, Notebook notebook) {
		super(label);
		this.index = index;
		this.notebook = notebook;
		notebook.registerTabButton(this);
	}

	public int getIndex() {
		return index;
	}

	public Container getParent() {
		return notebook;
	}

	public void setText(String s) {
		super.setText(s);
		if (notebook != null) {
			notebook.setTitleAt(index, s);
		}
	}

	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		if (notebook != null) {
			notebook.setEnabledAt(index, enabled);
		}
	}
}