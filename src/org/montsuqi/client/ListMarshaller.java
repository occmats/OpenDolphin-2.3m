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

package org.montsuqi.client;

import java.awt.Component;
import java.io.IOException;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;

class ListMarshaller extends WidgetMarshaller {
	
	synchronized boolean receive(WidgetValueManager manager, Component widget) throws IOException {
		Protocol con = manager.getProtocol();
		JList list = (JList)widget;
		DefaultListModel listModel = (DefaultListModel)list.getModel();
		con.receiveDataTypeWithCheck(Type.RECORD);
		StringBuffer label = con.getWidgetNameBuffer();
		int offset = label.length();
		int nItem = con.receiveInt();
		int count = -1;
		int from = 0;
		while (nItem-- != 0) {
			String name = con.receiveString();
			if (handleCommon(manager, widget, name)) {
				continue;
			}
			int num;
			if ("count".equals(name)) { //$NON-NLS-1$
				count = con.receiveIntData();
			} else if ("from".equals(name)) { //$NON-NLS-1$
				from = con.receiveIntData();
			} else if ("item".equals(name)) { //$NON-NLS-1$
				if (listModel.getSize() > 0) {
					listModel.clear();
				}
				con.receiveDataTypeWithCheck(Type.ARRAY);
				num = con.receiveInt();
				if (count < 0) {
					count = num;
				}
				for (int j = 0; j < num; j++) {
					String buff = con.receiveStringData();
					if (buff != null) {
						if ((j >= from) && ((j - from) < count)) {
							listModel.addElement(buff);
						}
					}
				}
			} else {
				con.receiveDataTypeWithCheck(Type.ARRAY);
				manager.registerValue(widget, name, new Integer(from));
				num = con.receiveInt();
				if (count < 0) {
					count = num;
				}
				ListSelectionModel model = list.getSelectionModel();
				for (int j = 0; j < num; j++) {
					boolean fActive = con.receiveBooleanData();
					if ((j >= from) &&	((j - from) < count)) {
						if (fActive) {
							model.addSelectionInterval(j, j);
						} else {
							model.removeSelectionInterval(j, j);
						}
					}
				}
			}
		}
		return true;
	}

	synchronized boolean send(WidgetValueManager manager, String name, Component widget) throws IOException {
		Protocol con = manager.getProtocol();
		JList list = (JList)widget;
		ListSelectionModel model = list.getSelectionModel();
		ValueAttribute va = manager.getValue(name);
	
		for (int i = 0, rows = list.getModel().getSize(); i < rows; i++) {
			con.sendPacketClass(PacketClass.ScreenData);
			con.sendString(name + '.' + va.getValueName() + '[' + (Integer)va.getOpt() + ']');
			con.sendDataType(Type.BOOL);
			con.sendBoolean(model.isSelectedIndex(i));
		}
		return true;
	}
}

