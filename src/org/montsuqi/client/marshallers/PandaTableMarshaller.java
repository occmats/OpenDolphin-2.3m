/*      PANDA -- a simple transaction monitor

 Copyright (C) 1998-1999 Ogochan.
 2000-2003 Ogochan & JMA (Japan Medical Association).
 2002-2006 OZAWA Sakuro.

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
package org.montsuqi.client.marshallers;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import org.montsuqi.client.PacketClass;
import org.montsuqi.client.Protocol;
import org.montsuqi.client.Type;
import org.montsuqi.widgets.PandaTable;

/**
 * <p>A class to send/receive CList data.</p>
 */
class PandaTableMarshaller extends WidgetMarshaller {

    private static List widgetList;

    static {
        widgetList = new ArrayList();
    }

    public synchronized void receive(WidgetValueManager manager, Component widget) throws IOException {
        Protocol con = manager.getProtocol();
        PandaTable table = (PandaTable) widget;

        widget.setFocusable(true);
        widget.setVisible(false);
        TableColumnModel columnModel = table.getColumnModel();
        String[] labels = new String[columnModel.getColumnCount()];
        for (int i = 0, n = columnModel.getColumnCount(); i < n; i++) {
            labels[i] = (String) columnModel.getColumn(i).getHeaderValue();
        }

        con.receiveDataTypeWithCheck(Type.RECORD);

        int trow = 0;
        double rowattrw = 0.0;
        int tcolumn = 0;

        for (int i = 0, n = con.receiveInt(); i < n; i++) {
            String name = con.receiveName();
            if (handleCommonAttribute(manager, widget, name)) {
                continue;
            } else if ("trow".equals(name)) { //$NON-NLS-1$
                trow = con.receiveIntData() - 1;
                if (trow < 0) {
                    trow = 0;
                }
            } else if ("tcolumn".equals(name)) { //$NON-NLS-1$
                tcolumn = con.receiveIntData() - 1;
                if (tcolumn < 0) {
                    tcolumn = 0;
                }
            } else if ("trowattr".equals(name)) { //$NON-NLS-1$
                int rowattr = con.receiveIntData();
                switch (rowattr) {
                    case 1: // DOWN
                        rowattrw = 1.0;
                        break;
                    case 2: // MIDDLE
                        rowattrw = 0.5;
                        break;
                    case 3: // QUATER
                        rowattrw = 0.25;
                        break;
                    case 4: // THREE QUATER
                        rowattrw = 0.75;
                        break;
                    default:
                        rowattrw = 0.0; // [0] TOP
                        break;
                }
            } else if ("tvalue".equals(name)) { //$NON-NLS-1$
                /*
                 * String dummy =
                 */ con.receiveStringData();
            } else if ("rowdata".equals(name)) { //$NON-NLS-1$                
                con.receiveDataTypeWithCheck(Type.ARRAY);
                int nrows = con.receiveInt();
                ArrayList cellNameList = new ArrayList<String>();
                for (int j = 0; j < nrows; j++) {
                    con.receiveDataTypeWithCheck(Type.RECORD);
                    int ncols = con.receiveInt();
                    for (int k = 0; k < ncols; k++) {
                        String colName = con.receiveName();
                        con.receiveDataTypeWithCheck(Type.RECORD);
                        int nitems = con.receiveInt();
                        for (int l = 0; l < nitems; l++) {
                            String itemName = con.receiveName();
                            if ("celldata".equals(itemName)) {
                                String data = con.receiveStringData();
                                table.setCell(j, k, data);
                                String cellName = widget.getName() + ".rowdata[" + j + "]." + colName + ".celldata";
                                cellNameList.add(cellName);
                            } else if ("fgcolor".equals(itemName)) {
                                String color = con.receiveStringData();
                                table.setFGColor(j, k, color);
                            } else if ("bgcolor".equals(itemName)) {
                                String color = con.receiveStringData();
                                table.setBGColor(j, k, color);
                            } else {
                                con.receiveStringData();
                                System.out.println("does not reach here");
                            }
                        }
                    }
                }
                manager.registerValue(widget, name, cellNameList);
            }
        }
        widget.validate();
        if (trow >= 0 && tcolumn >= 0) {

            /*
             * Windows7+Java 1.7で初回表示時にセル指定すると微妙にスクロールする問題のため 初回だけ0,0にセル指定する
             */
            if (widgetList.contains(widget.getName())) {
                table.changeSelection(trow, tcolumn, false, false);
            } else {
                widgetList.add(widget.getName());
                table.changeSelection(0, 0, false, false);
            }

            JScrollBar vScroll = getVerticalScrollBar(table);
            if (vScroll != null) {
                BoundedRangeModel model = vScroll.getModel();
                int max = model.getMaximum();
                int min = model.getMinimum();
                int rows = table.getModel().getRowCount();
                int value = (int) (((trow) * 1.0 / (rows)) * (max - min)) + min;
                if (rowattrw == 1.0) {
                    value += (int) ((1.0 / (rows)) * (max - min));
                }
                value -= rowattrw * model.getExtent();
                if (value < 0) {
                    value = 0;
                }
                model.setValue(value);
            }
        }
        con.addAlwaysSendWidget(widget);
        widget.setVisible(true);
    }

    public synchronized void send(WidgetValueManager manager, String name, Component widget) throws IOException {
        Protocol con = manager.getProtocol();
        PandaTable table = (PandaTable) widget;
        DefaultTableModel tableModel = (DefaultTableModel) table.getModel();

        con.sendPacketClass(PacketClass.ScreenData);
        con.sendName(name + ".trow"); //$NON-NLS-1$
        con.sendIntegerData(Type.INT, table.getChangedRow() + 1);

        con.sendPacketClass(PacketClass.ScreenData);
        con.sendName(name + ".tcolumn"); //$NON-NLS-1$
        con.sendIntegerData(Type.INT, table.getChangedColumn() + 1);

        con.sendPacketClass(PacketClass.ScreenData);
        con.sendName(name + ".tvalue"); //$NON-NLS-1$
        con.sendStringData(Type.VARCHAR, table.getChangedValue());

        int k = 0;
        ArrayList<String> cellNameList = (ArrayList<String>) manager.getValueOpt(name);
        for (int i = 0; i < table.getRows(); i++) {
            for (int j = 0; j < table.getColumns(); j++) {
                String cellName = cellNameList.get(k);
                if (cellName != null) {
                    con.sendPacketClass(PacketClass.ScreenData);
                    con.sendName(cellName);
                    con.sendStringData(Type.VARCHAR, (String) tableModel.getValueAt(i, j));
                }
                k += 1;
            }
        }
        table.setFocusable(false);
    }

    private JScrollBar getVerticalScrollBar(JTable table) {
        Container parent = table.getParent();
        if (parent instanceof JViewport) {
            parent = parent.getParent();
            if (parent instanceof JScrollPane) {
                JScrollPane scroll = (JScrollPane) parent;
                return scroll.getVerticalScrollBar();
            }
        }
        return null;
    }
}