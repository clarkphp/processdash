// PSP Dashboard - Data Automation Tool for PSP-like processes
// Copyright (C) 1999  United States Air Force
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
// OO-ALC/TISHD
// Attn: PSP Dashboard Group
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC:  ken.raisor@hill.af.mil


package pspdash.data;


import java.util.Vector;
import netscape.javascript.JSObject;


class NSFieldManager implements HTMLFieldManager, DataListener {

    JSObject window = null;
    Vector inputListeners = null;
    Repository data = null;
    String dataPath = null;
    NSDelayedNotifier notifier = null;
    boolean isRunning, unlocked;
    private DataApplet applet = null;



    NSFieldManager(DataApplet a) throws Exception {
        isRunning = true;
        inputListeners = new Vector();
        unlocked = a.unlocked();
        this.applet = a;

        notifier = new NSDelayedNotifier();
        notifier.setDaemon(true);
        notifier.start();

        // First order of business: get the current browser window object.
        // Sometimes this will fail if the browser is slow in coming up,
        // so we will try repeatedly until we succeed.

        window = null;
        for (int i = 50;   isRunning && (i != 0);   i--) try {
            window = JSObject.getWindow(a);
            break;
        } catch (Exception e) {
            try {                     // Pause before retrying...
                Thread.currentThread().sleep(100);
            } catch (InterruptedException ie) {}
        }

        if (window == null)
            throw new Exception("Javascript not available in this window.");
    }



    public void initialize(Repository data, String dataPath) {
        debug("initializing...");
        if (!isRunning) return; // abort if we have been terminated.

        this.data = data;
        this.dataPath = dataPath;

        JSObject document = (JSObject) window.getMember("document");
        JSObject formList = (JSObject) document.getMember("forms");

        // Build an internal list of all the elements on the form. (This
        // is necessary because otherwise Netscape 6 has a nasty habit of
        // reordering the list of elements in the form as we initialize
        // them.)
        Vector allElements = new Vector();
        if (formList != null) {
            int numForms = intValue(formList.getMember("length"));
            for (int formIdx = 0;   formIdx < numForms; formIdx++) {
                JSObject form = (JSObject) formList.getSlot(formIdx);
                JSObject elementList = (JSObject) form.getMember("elements");
                int numElements = intValue(elementList.getMember("length"));
                for (int elementIdx = 0;  elementIdx < numElements;  elementIdx++) {
                    if (!isRunning) return; // abort if we have been terminated
                    allElements.addElement(elementList.getSlot(elementIdx));
                }
            }
        }

        // Now walk through our list of elements and initialize them.
        for (int elemNum = 0;   elemNum < allElements.size();   elemNum++) {
            if (!isRunning) return; // abort if we have been terminated
            reinititializeFormElement
                ((JSObject)allElements.elementAt(elemNum), elemNum);
        }

        debug("initialization complete.");
    }


    public void dispose(boolean repositoryExists) {
        isRunning = false;
        if (!repositoryExists) data = null;

        try {
            // debug("erasing listeners...");
            for (int i = inputListeners.size();   i-- > 0; )
                destroyInputListener(i);

        } catch (Exception e) { printError(e); }
        window = null;
        inputListeners = null;
        data = null;
        dataPath = null;
    }


    private void destroyInputListener(int pos) {
        NSField f = null;
        try {
            f = (NSField) inputListeners.elementAt(pos);
            inputListeners.setElementAt(null, pos);
        } catch (ArrayIndexOutOfBoundsException e) {}

        if (f != null)
            f.dispose(data != null);
    }


    public void reinititializeFormElement(JSObject element, int pos) {
        destroyInputListener(pos);
        HTMLField f = null;

        try {
            String elementType = (String)element.getMember("type");
            // debug("Initializing a "+elementType+" element named "+element.getMember("name"));
            if ("text".equalsIgnoreCase(elementType) ||
                "hidden".equalsIgnoreCase(elementType) ||
                "textarea".equalsIgnoreCase(elementType))
                {
                    if (!"requiredTag".equalsIgnoreCase
                        ((String)element.getMember("name")))
                        f = new NSTextField(element, data, dataPath);
                }
            else if ("checkbox".equalsIgnoreCase(elementType))
                f = new NSCheckboxField(element, data, dataPath);

            else if ("select-one".equalsIgnoreCase(elementType))
                f = new NSSelectField(element, data, dataPath);

            // etc.

            if (f != null) {
                while (inputListeners.size() < pos+1)
                    inputListeners.addElement(null);
                inputListeners.setElementAt(f, pos);
                element.setMember(INDEX_ATTR, Integer.toString(pos));
                if (unlocked) f.unlock();
                if (f.i.isActive()) f.i.setChangeListener(this);
            }
        } catch (Exception e) {
            printError(e);
        }
    }
    private static final String INDEX_ATTR = "id";


    public void notifyListener(Object element) {
        // debug("notifyListener called");
        NSField f = null;

        int idx = -1;
        try {
            Object pos = ((JSObject) element).getMember(INDEX_ATTR);
            idx = intValue(pos);
        } catch (Exception e) {
            printError(e);
        }

        if (idx >= 0 && idx < inputListeners.size())
            f = (NSField) inputListeners.elementAt(idx);

        notifier.addField(f);
    }

    public void dataValuesChanged(Vector v) { dataValueChanged(null); }
    public void dataValueChanged(DataEvent e) { applet.refreshPage(); }

    public static int intValue(Object o) {
        if (o == null) return -1;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {}
        return -1;
    }

    protected void printError(Exception e) {
        System.err.println("Exception: " + e);
        e.printStackTrace(System.err);
    }
    private void debug(String s) {
        // System.out.println("NSFieldManager."+s);
    }
}
