// Copyright (C) 2009 Tuma Solutions, LLC
// Process Dashboard - Data Automation Tool for high-maturity processes
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// The author(s) may be contacted at:
// Process Dashboard Group
// c/o Ken Raisor
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC:  processdash-devel@lists.sourceforge.net

package net.sourceforge.processdash.ui.lib.binding;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;

/**
 * A Listener used to determine if a Component should be enabled or disabled
 *  depending on the nature of propertyChange events.
 */
public class EnablePropertyListener implements PropertyChangeListener {

    /** The properties that trigger the enabling and disabling the the widget */
    private String enablingProperty = null;
    private String disablingProperty = null;

    /** The widget to enable/disable */
    JComponent widget = null;

    public EnablePropertyListener(JComponent widget,
                                  String enablingProperty,
                                  String disablingProperty) {

        this.enablingProperty = enablingProperty;
        this.disablingProperty = disablingProperty;
        this.widget = widget;
    }

    public void propertyChange(PropertyChangeEvent evt) {
        String property = evt.getPropertyName();

        Object newValue = evt.getNewValue();
        boolean enabled = (newValue != null && newValue != Boolean.FALSE &&
                            !newValue.equals("false") && !newValue.equals(""));

        Object widgetLabel = widget.getClientProperty("labeledBy");

        boolean shouldEnable = (property.equals(this.disablingProperty) && !enabled)
                                || (property.equals(this.enablingProperty) && enabled);

        widget.setEnabled(shouldEnable);

        if (widgetLabel instanceof JComponent)
            ((JComponent) widgetLabel).setEnabled(shouldEnable);
    }

}
