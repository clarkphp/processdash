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
// Foundation, Inc., 59 Temple Place -Suite 330, Boston, MA  02111-1307, USA.
//
// The author(s) may be contacted at:
// OO-ALC/TISHD
// Attn: PSP Dashboard Group
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC:  ken.raisor@hill.af.mil

package pspdash.data;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Stack;
import com.oroinc.text.perl.MalformedPerl5PatternException;


public class DataRepository implements Repository {

    public static final String anonymousPrefix = "///Anonymous";

        /** a mapping of data names (Strings) to data values (DataElements) */
        Hashtable data = new Hashtable(8000, (float) 0.5);

        /** a backwards mapping of the above hashtable for data values that happen
         *  to be DataListeners.  key is a DataListener, value is a String. */
        Hashtable activeData = new Hashtable(2000, (float) 0.5);

        PrefixHierarchy repositoryListenerList = new PrefixHierarchy();

        Vector datafiles = new Vector();

        RepositoryServer dataServer = null;

        Hashtable PathIDMap = new Hashtable(20);
        Hashtable IDPathMap = new Hashtable(20);

        HashSet dataElementNameSet = new HashSet();
        Set dataElementNameSet_ext =
            Collections.unmodifiableSet(dataElementNameSet);

        /** Sets the policy for auto-realization of deferred data. Possible values:
         *  Boolean.TRUE - auto realize all data
         *  Boolean.FALSE - don't auto realize any data
         *  a DataFile object - only autorealize data for this file. */
        Object realizeDeferredDataFor = Boolean.FALSE;

        private class DataRealizer extends Thread {
            Stack dataElements = null;
            boolean terminate = false;

            public DataRealizer() {
                super("DataRealizer");
                dataElements = new Stack();
                setPriority(MIN_PRIORITY);
            }

            // when adding an element to the data Realizer, also restart it.
            public void addElement(DataElement e) {
                dataElements.push(e);
                interrupt();
            }

            public void run() {
                // run this thread until ordered to terminate
                while (!terminate) {

                    // if there is no data to realize, suspend this thread
                    if (dataElements.isEmpty()) {
                        dataNotifier.highPriority();
                        try { sleep(Long.MAX_VALUE); } catch (InterruptedException i) {}
                    } else try { // otherwise realize the data
                        sleep(100);
                        ((DataElement) dataElements.pop()).maybeRealize();
                    } catch (Exception e) {}
                }

                // when terminating, clean up the dataElements stack
                while (!dataElements.isEmpty()){
                    dataElements.pop();
                }
            }

            // command the process to terminate, and resume just in case it is
            // suspended
            public void terminate() {
                terminate = true;
                interrupt();
            }

        }

        DataRealizer dataRealizer;

        public void setRealizationPolicy(String policy) {
            if ("full".equalsIgnoreCase(policy))
                realizeDeferredDataFor = Boolean.TRUE;
            else if ("min".equalsIgnoreCase(policy))
                realizeDeferredDataFor = "";
            else
                realizeDeferredDataFor = Boolean.FALSE;
        }


        private class DataSaver extends Thread {
            public DataSaver() { start(); }
            public void run() {
                while (true) try {
                    sleep(120000);         // save dirty datafiles every 2 minutes
                    saveAllDatafiles();
                } catch (InterruptedException ie) {}
            }
        }

        DataSaver dataSaver = new DataSaver();


        private class DataFile {
            String prefix = null;
            String inheritsFrom = null;
            File file = null;
            int dirtyCount = 0;
        }


        // The DataElement class tracks the state of a single piece of data.
        private class DataElement {

            // the value of this element.  When data elements are created but not
            // initialized, their value is set to null.  Elements with null values
            // will not be saved out to any datafile.
            //
            private SaveableData value = null;
            private boolean deferred = false;

            // the datafile to which this element should be saved.  If this value
            // is null, the element will not be saved out to any datafile.
            //
            DataFile datafile = null;

            // a list of objects that are interested in changes to the value of this
            // element.  SPECIAL MEANINGS:
            //    1) a null value indicates that no objects have *ever* expressed an
            //       interest in this data element.
            //    2) a Vector with objects in it is a list of objects that should be
            //       notified if the value of this data element changes.
            //    3) an empty Vector indicates that, although some object(s) once
            //       expressed interest in this data element, no objects are
            //       interested any longer.
            //
            Vector dataListenerList = null;

            public DataElement() {}

            public SaveableData getValue() {
                if (deferred) realize();
                return value;
            }

            public SimpleData getSimpleValue() {
                if (deferred) realize();
                return value.getSimpleValue();
            }

            public SaveableData getImmediateValue() {
                return value;
            }

            public synchronized void setValue(SaveableData d) {
                if (deferred = ((value = d) instanceof DeferredData))
                    if (realizeDeferredDataFor == datafile ||
                        realizeDeferredDataFor == Boolean.TRUE)
                        dataRealizer.addElement(this);
            }

            private synchronized void realize() {
                // since realize can be entered from several places, ensure someone
                // else didn't run it just before this call.
                if (deferred) {
                    deferred = false;
                    try {
                        value = ((DeferredData) value).realize();
                    } catch (ClassCastException e) {
                    } catch (MalformedValueException e) {
                        value = new MalformedData(value.saveString());
                    }
                }
            }

            public synchronized void disposeValue() {
                if (value != null) value.dispose();
                deferred = false;
            }

            public void maybeRealize() { if (deferred) realize(); }
        }

        private class DataNotifier extends Thread {

            /** A list of the notifications we need to perform.
             *
             * the <B>keys</B> in the hashtable are DataListeners that need to be
             * notified of changes in data.
             *
             * the <b>values</b> are separate hashtables.  The keys of these
             * subhashtables name data elements that have changed, which the
             * listener is interested in.  The values in these subhashtables
             * are the named DataElements.
             */
            Hashtable notifications = null;

            /** A list of active listeners.  (An active listener is one that is going
             * to perform a recalculation as soon as it is notified of a data change.
             * That recalculation will probably trigger other data notifications.)
             *
             * The <b>keys</b> in the hashtable are the names of the data elements
             * which will be recalculated when we notify the DataListener which
             * is stored as the <b>value</b> in the hashtable.
             *
             * This data structure is basically a backward mapping of the
             * DataRepository's <code>activeData</code> structure, for only those
             * DataListeners which appear in the <code>notifications</code> list
             * above.
             */
            Hashtable activeListeners = null;

            /** a list of misbehaved data which appears to be circularly defined. */
            Hashtable circularData = new Hashtable();

            private volatile boolean suspended = false;

            public DataNotifier() {
                super("DataNotifier");
                notifications = new Hashtable();
                activeListeners = new Hashtable();
                setPriority(MIN_PRIORITY);
            }

            public void highPriority() {
                setPriority(NORM_PRIORITY);
            }
            public void lowPriority()  {
                setPriority((MIN_PRIORITY + NORM_PRIORITY)/2);
            }

            /** Determine all the notifications that will need to be made as
             * a result of a change to given <code>DataElement</code> with
             * the given <code>name</code>, and add those notifications to
             * our internal data structures.
             */
            public void dataChanged(String name, DataElement d) {
                if (name == null) return;
                if (d == null) d = (DataElement) data.get(name);
                if (d == null) return;
                if (circularData.get(name) != null) return;

                Vector dataListenerList = d.dataListenerList;

                if (dataListenerList == null ||
                    dataListenerList.size() == 0)
                    return;

                DataListener dl;
                Hashtable elements;
                String listenerName;
                boolean notifyActiveListener;
                for (int i = dataListenerList.size();  i > 0; ) try {
                    dl = ((DataListener) dataListenerList.elementAt(--i));
                    listenerName = (String) activeData.get(dl);
                    if (listenerName == null)
                        notifyActiveListener = false;
                    else if (activeListeners.put(listenerName, dl) != null)
                        notifyActiveListener = false;
                    else
                        notifyActiveListener = true;
                    synchronized (notifications) {
                        elements = ((Hashtable) notifications.get(dl));
                        if (elements == null)
                            notifications.put(dl, elements =
                                              new Hashtable(2));
                    }
                    elements.put(name, d);
                    if (notifyActiveListener) dataChanged(listenerName, null);
                } catch (ArrayIndexOutOfBoundsException e) {
                    // Someone has been messing with dataListenerList while we're
                    // iterating through it.  No matter...the worst that can happen
                    // is that we will notify someone who doesn't care anymore, and
                    // that is harmless.
                }

                if (suspended) synchronized(this) { notify(); }
            }

            public void addEvent(String name, DataElement d, DataListener dl) {
                if (name == null || dl == null) return;

                String listenerName = (String) activeData.get(dl);
                if (listenerName != null)
                    activeListeners.put(listenerName, dl);

                Hashtable elements;
                synchronized (notifications) {
                    elements = ((Hashtable) notifications.get(dl));
                    if (elements == null)
                        notifications.put(dl, elements = new Hashtable());
                }
                elements.put(name, d);

                fireEvent(dl);
            }

            public void removeDataListener(String name, DataListener dl) {
                Hashtable h = (Hashtable) notifications.get(dl);
                if (h != null)
                    h.remove(name);
            }

            public void deleteDataListener(DataListener dl) {
                notifications.remove(dl);
                String listenerName = (String) activeData.get(dl);
                if (listenerName != null)
                    activeListeners.remove(listenerName);
            }

            private void fireEvent(DataListener dl) {
                if (dl == null) return;

                Hashtable elements = ((Hashtable) notifications.get(dl));
                if (elements == null) return;

                String listenerName = (String) activeData.get(dl);

                synchronized (elements) {
                    if (notifications.get(dl) == null) return;

                    Thread t = (Thread) elements.get(CIRCULARITY_TOKEN);

                    if (t == null)
                        elements.put(CIRCULARITY_TOKEN, Thread.currentThread());
                    else if (t != Thread.currentThread())
                        return;
                    else {
                        if (listenerName != null) {
                            System.err.println("Infinite recursion encountered while " +
                                               "recalculating " + listenerName +
                                               " - ABORTING");
                            circularData.put(listenerName, Boolean.TRUE);
                        }
                        return;
                    }
                }

                String name;
                DataElement d;
                DataListener activeListener;

                                        // run through the elements to see if any are
                                        // also expected to change, and do those first.
                Enumeration names = elements.keys();
                while (names.hasMoreElements()) {
                    name = (String) names.nextElement();
                    if (name == CIRCULARITY_TOKEN) continue;
                    d    = (DataElement) elements.get(name);
                    activeListener = (DataListener) activeListeners.get(name);
                    if (activeListener != null)
                        fireEvent(activeListener);
                }
                elements.remove(CIRCULARITY_TOKEN);

                                        // Build a list of data events to send
                elements = ((Hashtable) notifications.remove(dl));
                if (listenerName != null)
                    activeListeners.remove(listenerName);
                if (elements == null) return;

                Vector dataEvents = new Vector();
                names = elements.keys();
                while (names.hasMoreElements()) {
                    name = (String) names.nextElement();
                    d    = (DataElement) elements.get(name);
                    dataEvents.addElement(new DataEvent(DataRepository.this, name,
                                                        DataEvent.VALUE_CHANGED,
                                                        d.getValue() == null ? null :
                                                        d.getSimpleValue()));
                }

                                        // send the data events via dataValuesChanged()
                try {
                    dl.dataValuesChanged(dataEvents);
                } catch (RemoteException rem) {
                    System.err.println(rem.getMessage());
                    System.err.println("    when trying to notify a datalistener.");
                } catch (Exception e) {
                    // Various exceptions, most notably NullPointerException, can
                    // occur if we erroneously notify a DataListener of changes *after*
                    // it has unregistered for those changes.  Such mistakes can happen
                    // due to multithreading, but no harm is done as long as the
                    // exception is caught here.
                }
            }

            private boolean fireEvent() {
                try {
                    fireEvent((DataListener) notifications.keys().nextElement());
                    return true;
                } catch (java.util.NoSuchElementException e) {
                    return false;
                }
            }

            public void run() {
                while (true) {
                    if (fireEvent())
                        yield();
                    else
                        doWait();
                }
            }

            private synchronized void doWait() {
                suspended = true;
                try { wait(); } catch (InterruptedException i) {}
                suspended = false;
            }

            public void flush() {
                while (fireEvent()) {}
            }
        }
        private static final String CIRCULARITY_TOKEN = "CIRCULARITY_TOKEN";

        DataNotifier dataNotifier;



        private class DataFreezer extends Thread implements RepositoryListener,
                                                            DataConsistencyObserver
        {

            /** Keys in this hashtable are the String names of freeze tag
             * data elements.  Values are the FrozenDataSets to which they
             * refer. */
            private Hashtable frozenDataSets;

            /** A list of names of data elements which need to be frozen. */
            private SortedSet itemsToFreeze;

            /** A list of names of data elements which need to be thawed. */
            private SortedSet itemsToThaw;

            /** Flag indicating that we've received a request to terminate. */
            private volatile boolean terminate = false;

            public DataFreezer() {
                frozenDataSets = new Hashtable();
                itemsToFreeze = Collections.synchronizedSortedSet(new TreeSet());
                itemsToThaw = Collections.synchronizedSortedSet(new TreeSet());
                addRepositoryListener(this, "");
            }

            public void run() {
                // run this thread until ordered to terminate
                while (!terminate) {
                    // Wait until the data is consistent - don't freeze or thaw anything
                    // while files are being opened and closed.
                    addDataConsistencyObserver(this);

                    // Sleep until we're needed again.
                    try { sleep(Long.MAX_VALUE); } catch (InterruptedException i) {}
                }
            }

            public void dataIsConsistent() {
                // Perform all requested work.
                freezeAll();
                thawAll();
            }

            /** Freeze all waiting items. */
            private void freezeAll() {
                String item;
                while ((item = pop(itemsToFreeze)) != null)
                    performFreeze(item);
            }

            /** Thaw all waiting items. */
            private void thawAll() {
                String item;
                while ((item = pop(itemsToThaw)) != null)
                    performThaw(item);
            }

            /** Pop the first item off a sorted set, in a thread-safe fashion.
             * @return a item which has been removed from the set, or null if
             *  the set is empty.
             */
            private String pop(SortedSet set) {
                synchronized(set) {
                    if (set.isEmpty())
                        return null;
                    else {
                        String result = (String) set.first();
                        set.remove(result);
                        return result;
                    }
                }
            }

            public void terminate() {
                // Stop listening for events.
                removeRepositoryListener(this);

                // stop this thread (if the thread is currently awake, this will
                // not have any immediate effect.)
                terminate = true;
                interrupt();

                // make certain that all remaining work is completed.  We include
                // these lines here to implicitly require our caller to wait while
                // this task is completed.
                freezeAll();
                thawAll();
            }

            public void dataAdded(DataEvent e) {
                String dataName = e.getName();
                if (isFreezeFlagElement(dataName) &&
                    !frozenDataSets.containsKey(dataName))
                    frozenDataSets.put(dataName, new FrozenDataSet(dataName));
            }

            public void dataRemoved(DataEvent e) {
                String dataName = e.getName();
                if (!isFreezeFlagElement(dataName)) return;
                FrozenDataSet set = (FrozenDataSet) frozenDataSets.remove(dataName);
                if (set != null)
                    set.dispose();
            }

            private boolean isFreezeFlagElement(String dataName) {
                return (dataName.indexOf(FREEZE_FLAG_TAG) != -1);
            }

            /** Perform the work required to freeze a data value. */
            private void performFreeze(String dataName) {
                DataElement element = (DataElement) data.get(dataName);
                if (element == null) return;

                // Make certain no data values are currently in a state of flux
                dataNotifier.flush();

                // This will realize the value if it is deferred
                SaveableData value = element.getValue();

                // For now, lets add this in - don't doubly freeze data.  Supporting
                // double freezing of data might make it easier for the people who
                // write freeze flag expressions, but it makes things more confusing
                // for end users:
                //  * data items that are frozen by multiple freeze flags perplex
                //    the user: they toggle some boolean value and can't figure out
                //    why the data isn't thawing
                //  * sometimes it is possible for data accidentally to become doubly
                //    frozen by the SAME freeze flag.  Then users toggle the flag and
                //    their data toggles between frozen and doubly frozen.
                if (value instanceof FrozenData)
                    return;

                System.out.println("freezing " + dataName);

                // Determine the prefix of the data element.
                String prefix = "";
                if (element.datafile != null)
                    prefix = element.datafile.prefix;

                // Lookup the default value of this data element.
                String defVal = lookupDefaultValue(dataName, element);

                // Create the frozen version of the value.
                SaveableData frozenValue = null;
                if (value instanceof DoubleData)
                    frozenValue = new FrozenDouble
                        (dataName, (DoubleData)value, DataRepository.this, prefix, defVal);
                else if (value instanceof DateData)
                    frozenValue = new FrozenDate
                        (dataName, (DateData)value, DataRepository.this, prefix, defVal);
                else if (value instanceof StringData)
                    frozenValue = new FrozenString
                        (dataName, (StringData)value, DataRepository.this, prefix, defVal);
                else
                    // Eeek! This should hopefully never happen. It would mean there is
                    // a new basic form of data that no one told us about! Probably the
                    // best thing to do is keep our hands off and do nothing.
                    return;

                // Save the frozen value to the repository.
                putValue(dataName, frozenValue);
            }

            /** Perform the work required to thaw a data value. */
            private void performThaw(String dataName) {
                DataElement element = (DataElement) data.get(dataName);
                if (element == null) return;

                String defVal = lookupDefaultValue(dataName, element);

                SaveableData value = element.getImmediateValue(), thawedValue;
                if (value instanceof FrozenData) {
                    System.out.println("thawing " + dataName);
                    // Thaw the value.
                    thawedValue = ((FrozenData)value).thaw(defVal);

                    // Save the thawed value to the repository.
                    putValue(dataName, thawedValue);
                }
            }

            /** Register the named data element for freezing.
             *
             * The element is not frozen immediately, but rather added to a
             * queue for freezing sometime in the future.
             */
            public synchronized void freeze(String dataName) {
                if (itemsToThaw.remove(dataName) == false)
                    itemsToFreeze.add(dataName);
            }

            /** Register the named data element for thawing.
             *
             * The element is not thawed immediately, but rather added to a
             * queue for thawing sometime in the future.
             */
            public synchronized void thaw(String dataName) {
                if (itemsToFreeze.remove(dataName) == false)
                    itemsToThaw.add(dataName);
            }

            private class FrozenDataSet implements DataListener,
                                                   RepositoryListener,
                                                   DataConsistencyObserver {

                String freezeFlagName;
                String freezeRegexp;
                Set dataItems;
                int currentState = FDS_GRANDFATHERED;
                boolean observedFlagValue;
                volatile boolean initializing;
                Set tentativeFreezables;

                public FrozenDataSet(String freezeFlagName) {
                    this.freezeFlagName = freezeFlagName;

                    //System.out.println("creating FrozenDataSet for " + freezeFlagName);

                    // Fetch the prefix and the regular expression.
                    int pos = freezeFlagName.indexOf(FREEZE_FLAG_TAG);
                    if (pos == -1) return; // shouldn't happen!

                    String prefix = freezeFlagName.substring(0, pos+1);
                    this.freezeRegexp = "m\n^" + prefix +
                        freezeFlagName.substring(pos+FREEZE_FLAG_TAG.length()) + "$\n";

                    this.initializing = true;
                    this.tentativeFreezables = new HashSet();
                    this.dataItems = Collections.synchronizedSet(new HashSet());

                    addDataListener(freezeFlagName, this);

                    addRepositoryListener(this, prefix);
                }

                public synchronized void dispose() {
                    removeRepositoryListener(this);
                    dataItems.clear();
                    deleteDataListener(this);
                }

                private void freeze(String itemName) {
                    if (initializing) tentativeFreezables.add(itemName);
                    else DataFreezer.this.freeze(itemName);
                }

                private void freezeAll(Set dataItems) {
                    synchronized (dataItems) {
                        Iterator i = dataItems.iterator();
                        String itemName;
                        while (i.hasNext()) {
                            itemName = (String) i.next();
                            freeze(itemName);
                        }
                    }
                    interrupt();          // this interrupts the DataFreezer thread.
                }

                private void thawAll(Set dataItems) {
                    synchronized (dataItems) {
                        Iterator i = dataItems.iterator();
                        String itemName;
                        while (i.hasNext()) {
                            itemName = (String) i.next();
                            thaw(itemName);
                        }
                    }
                    interrupt();          // this interrupts the DataFreezer thread.
                }

                // The next two methods implement the DataListener interface.

                public void dataValueChanged(DataEvent e) {
                    if (! freezeFlagName.equals(e.getName())) return;
                    observedFlagValue = e.getValue().test();
                    addDataConsistencyObserver(this);
                }

                public void dataValuesChanged(Vector v) {
                    if (v == null || v.size() == 0) return;
                    for (int i = v.size();  i > 0; )
                        dataValueChanged((DataEvent) v.elementAt(--i));
                }

                /** Respond to a change in the value of the freeze flag.
                 *  The state transition diagram is: <PRE>
                 *
                 *     current
                 *     state     freeze flag = TRUE         freeze flag = FALSE
                 *     -------   ------------------         -----------------------
                 *     FROZEN    no change                  set to thawed; thaw all
                 *     GRAND     no change                  set to thawed
                 *     THAWED    set to frozen; freeze all  no change
                 *
                 * </PRE>
                 */
                public void dataIsConsistent() {
                    //System.out.println(freezeFlagName + " = "+ observedFlagValue);
                    synchronized (this) {
                        if (observedFlagValue == true) {
                            // data should be frozen or grandfathered.
                            if (currentState == FDS_THAWED) {
                                currentState = FDS_FROZEN;
                                freezeAll(dataItems);
                            }

                        } else {            // data should be thawed.
                            if (currentState == FDS_FROZEN && !initializing)
                                thawAll(dataItems);
                            currentState = FDS_THAWED;
                        }

                        if (initializing) {
                            initializing = false;
                            if (currentState == FDS_FROZEN)
                                freezeAll(tentativeFreezables);
                            tentativeFreezables = null;
                        }
                    }
                }

                /** Respond to a notification about a data element that has been
                 *  added to the repository.
                 *
                 *  (Note that this happens during initial opening of
                 *  datafiles as well as on an ongoing basis as new elements
                 *  are created.) The state transition diagram is: <PRE>
                 *
                 *     current
                 *     state     item = THAWED        item = FROZEN
                 *     -------   -------------------  -------------
                 *     FROZEN    freeze the item (1)  no action
                 *     GRAND     no action            set to frozen; freeze all
                 *     THAWED    no action            no action (2)
                 *
                 * </PRE>
                 * Notes:<P>
                 * (1) This situation would most likely occur as the result of
                 *     freezing a project, then installing a new definition for its
                 *     process. If the new process definition defines a new data
                 *     element, then this situation would be triggered; the best
                 *     course of action is to freeze it along with its colleagues.<P>
                 *
                 * (2) A single data item might belong to two distinct FreezeSets.
                 *     If both sets were frozen, it would be <b>doubly</b> frozen.
                 *     On the other hand, it might be frozen by one but not the
                 *     other, triggering this scenario.
                 */
                public void dataAdded(DataEvent e) {
                    String dataName = e.getName();
                    try {
                        if (isFreezeFlagElement(dataName))
                            return;           // don't freeze freeze flags!
                        if (!ValueFactory.perl.match(freezeRegexp, dataName))
                            return;
                    } catch (MalformedPerl5PatternException m) {
                        //The user has given a bogus pattern!
                        System.out.println("The regular expression for " + freezeFlagName +
                                           " is malformed.");
                        dispose();
                        return;
                    }
                    SaveableData value = getValue(dataName);
                    boolean valueIsFrozen = (value instanceof FrozenData);

                    synchronized (this) {
                        if (currentState == FDS_GRANDFATHERED && valueIsFrozen) {
                            freezeAll(dataItems);
                            currentState = FDS_FROZEN;
                        } else if (currentState == FDS_FROZEN && !valueIsFrozen) {
                            freeze(dataName);
                            interrupt();
                        }

                        dataItems.add(dataName);
                    }
                }

                public void dataRemoved(DataEvent e) {
                    dataItems.remove(e.getName());
                }
            }
        }
        private static final String FREEZE_FLAG_TAG = "/FreezeFlag/";
        private static int FDS_FROZEN = 0;
        private static int FDS_GRANDFATHERED = 1;
        private static int FDS_THAWED = 2;

        DataFreezer dataFreezer;

        public void disableFreezing() {
            if (dataFreezer != null) {
                dataFreezer.terminate();
                dataFreezer = null;
            }
        }



        URL [] templateURLs = null;


        public DataRepository() {
            includedFileCache.put("<dataFile.txt>", globalDataDefinitions);
            dataRealizer = new DataRealizer();
            dataNotifier = new DataNotifier();
            dataFreezer  = new DataFreezer();
            dataRealizer.start();
            dataNotifier.start();
            dataFreezer.start();
        }

        public void startServer(int port) {
            if (dataServer == null) {
                dataServer = new RepositoryServer(this, port);
                dataServer.start();
            }
        }

        public void saveAllDatafiles() {
            DataFile datafile;

            for (int i = datafiles.size();   i-- != 0; ) {
                datafile = (DataFile)datafiles.elementAt(i);
                if (datafile.dirtyCount > 0)
                    saveDatafile(datafile);
            }
        }

        public void finalize() {
            // Command the data freezer to terminate.
            if (dataFreezer != null) dataFreezer.terminate();
            dataFreezer = null;
            // Command data realizer to terminate, then wait for it to.
            dataRealizer.terminate();
            try {
                dataRealizer.join(6000);
            } catch (InterruptedException e) {}

            saveAllDatafiles();
            if (dataServer != null)
                dataServer.quit();
        }


        public void setDatafileSearchURLs(URL[] templateURLs) {
            this.templateURLs = templateURLs;
        }


        public synchronized void renameData (String oldPrefix, String newPrefix) {

            DataFile datafile = null;
            String datafileName = null;

                                      // find the datafile associated with 'prefix'
            for (int index = datafiles.size();  index-- > 0; ) {
                datafile = (DataFile) datafiles.elementAt(index);
                if (datafile.prefix.equals(oldPrefix)) {
                    datafileName = datafile.file.getPath();
                    break;
                }
            }

            if (datafileName != null) {

                // I'm commenting out this call, and the resume() call below, because they
                // are deprecated and no longer supported in JDK1.2;  But even worse, they
                // these two lines really had no effect in JDK1.1.  They look like they shut
                // down the dataServer, but in reality, all they do is prevent it from accepting
                // new connections from clients.  (None of the repositoryThreads are suspended.)
                // dataServer.suspend();

                remapIDs(oldPrefix, newPrefix);

                                        // close the datafile, then
                closeDatafile(oldPrefix);

                try {
                                        // open it again with the new prefix.
                    openDatafile(newPrefix, datafileName);
                } catch (Exception e) {
                    printError(e);
                }

                // dataServer.resume();

            } else {
                datafile = guessDataFile(oldPrefix+"/foo");
                if (datafile != null && datafile.prefix.length() == 0)
                    remapDataNames(oldPrefix, newPrefix);
            }
        }

        /** this renames data values in the global datafile. */
        private void remapDataNames(String oldPrefix, String newPrefix) {

            String name, newName;
            DataElement element;
            SaveableData value;

            oldPrefix = oldPrefix + "/";
            newPrefix = newPrefix + "/";
            int oldPrefixLen = oldPrefix.length();
            Iterator k = getKeys();
            while (k.hasNext()) {
                name = (String) k.next();
                if (!name.startsWith(oldPrefix))
                    continue;

                element = (DataElement) data.get(name);
                if (element.datafile == null ||
                    element.datafile.prefix == null ||
                    element.datafile.prefix.length() > 0)
                    // only remap data which lives in the global datafile.
                    continue;

                value = element.getImmediateValue();

                // At this point, we will not rename data elements unless they
                // are SimpleData.  Non-simple data (e.g., functions, etc) needs
                // to know its name and prefix, so it would be more complicated to
                // move - but none of that stuff should be moving.
                if (value instanceof SimpleData) {
                    newName = newPrefix + name.substring(oldPrefixLen);
                    System.out.println("renaming " + name + " to " + newName);
                    putValue(newName, value.getSimpleValue());
                    putValue(name, null);
                }
            }
        }


        public synchronized void dumpRepository (PrintWriter out, Vector filt) {
            Enumeration k = data.keys();
            String name, value;
            DataElement  de;
            SaveableData sd;

                                      // first, realize all elements.
            while (k.hasMoreElements()) {
                name = (String) k.nextElement();
                ((DataElement)data.get(name)).maybeRealize();
            }

                                      // next, print out all element values.
            k = data.keys();
            while (k.hasMoreElements()) {
                name = (String) k.nextElement();
                if (pspdash.Filter.matchesFilter(filt, name)) {
                    try {
                        de = (DataElement)data.get(name);
                        if (de.datafile != null) {
                            sd = de.getValue();
                            if (sd instanceof DateData) {
                                value = ((DateData)sd).formatDate();
                            } else if (sd instanceof StringData) {
                                value = ((StringData)sd).getString();
                            } else
                                value = de.getSimpleValue().toString();
                            if (value != null)
                                out.println(name + "," + value);
                        }
                    } catch (Exception e) {
//        System.err.println("Data error:"+e.toString()+" for:"+name);
                    }
                }
                Thread.yield();
            }
        }


        public synchronized void dumpRepository () {
            Enumeration k = data.keys();
            String name;
            DataElement element;

                                      // first, realize all elements.
            while (k.hasMoreElements()) {
                name = (String) k.nextElement();
                ((DataElement)data.get(name)).maybeRealize();
            }

                                      // next, print out all element values.
            k = data.keys();
            while (k.hasMoreElements()) {
                name = (String) k.nextElement();
                element = (DataElement)data.get(name);
                System.out.print(name);
                System.out.print("=" + element.getValue());
                if (element.dataListenerList != null)
                    System.out.print(", listeners=" + element.dataListenerList);
                System.out.println();
            }
        }

        public synchronized void closeDatafile (String prefix) {
            //System.out.println("closeDatafile("+prefix+")");

            startInconsistency();

            try {
                DataFile datafile = null;

                                        // find the datafile associated with 'prefix'
                Enumeration datafileList = datafiles.elements();
                while (datafileList.hasMoreElements()) {
                    DataFile file = (DataFile) datafileList.nextElement();
                    if (file.prefix.equals(prefix)) {
                        datafile = file;
                        break;
                    }
                }


                if (datafile != null) {

                    remapIDs(prefix, "///deleted//" + prefix);

                                          // save previous changes to the datafile.
                    if (datafile.dirtyCount > 0)
                        saveDatafile(datafile);

                    Iterator k = getKeys();
                    String name;
                    DataElement element;
                    DataListener dl;
                    Vector elementsToRemove = new Vector();
                    Hashtable affectedServerThreads = new Hashtable();

                                          // build a list of all the data elements of
                                          // this datafile.
                    while (k.hasNext()) {
                        name = (String) k.next();
                        element = (DataElement)data.get(name);
                        if (element != null && element.datafile == datafile) {
                            elementsToRemove.addElement(name);
                            elementsToRemove.addElement(element);
                        }
                    }

                                          // call the dispose() method on all the data
                                          // elements' values.
                    for (int i = elementsToRemove.size();  i > 0; ) {
                        element = (DataElement) elementsToRemove.elementAt(--i);
                        name    = (String) elementsToRemove.elementAt(--i);
                        element.disposeValue();
                        element.datafile = null;
                    }
                                          // remove the data elements.
                    for (int i = elementsToRemove.size();  i > 0; ) {
                        element = (DataElement) elementsToRemove.elementAt(--i);
                        name    = (String) elementsToRemove.elementAt(--i);
                        removeValue(name);
                    }
                                          // remove 'datafile' from the list of
                                          // datafiles in this repository.
                    datafiles.removeElement(datafile);
                }

            } catch (Exception e) {
                printError(e);
            } finally {
                finishInconsistency();
            }
        }

        private DataElement add(String name, SaveableData value, DataFile f,
                                boolean notify) {

                                    // Add the element to the table
            DataElement d = new DataElement();
            d.setValue(value);
            d.datafile = f;
            data.put(name, d);
            // System.out.println("DataRepository adding " + name + "=" +
            //                    (value == null ? "null" : value.saveString()));

            if (notify && !name.startsWith(anonymousPrefix))
                repositoryListenerList.dispatch
                    (new DataEvent(this, name, DataEvent.DATA_ADDED, null));

            return d;
        }


        /** remove the named data element.
         * @param name             the name of the element to remove.
         */
        public synchronized void removeValue(String name) {

            DataElement removedElement = (DataElement)data.get(name);

            // if the named object existed in the repository,
            if (removedElement != null) {

                SimpleData oldValue;

                if (removedElement.getImmediateValue() == null)
                    oldValue = null;
                else if (removedElement.getImmediateValue() instanceof DeferredData)
                    oldValue = null;
                else {
                    oldValue = removedElement.getSimpleValue();
                    removedElement.getValue().dispose();
                }

                                        // notify any repository listeners
                if (!name.startsWith(anonymousPrefix))
                    repositoryListenerList.dispatch
                        (new DataEvent(this, name, DataEvent.DATA_REMOVED, oldValue));

                            // flag the element's datafile as having been modified
                if (removedElement.datafile != null)
                    datafileModified(removedElement.datafile);

                                          // disown the element from its datafile,
                removedElement.datafile = null;
                if (removedElement.getValue() != null)
                    removedElement.getValue().dispose();
                removedElement.setValue(null);     // erase its previous value,
                maybeDelete(name, removedElement); // and discard if appropriate.
            }
        }



        private DataFile guessDataFile(String name) {

            DataFile datafile;
            DataFile result = null;

            if (name.indexOf("//") == -1)
                for (int i = datafiles.size();   i-- != 0; ) {
                    datafile = (DataFile)datafiles.elementAt(i);
                    if (!datafile.file.canWrite()) continue;
                    if (name.startsWith(datafile.prefix + "/") &&
                        ((result == null) ||
                         (datafile.prefix.length() > result.prefix.length())))
                        result = datafile;
                }

            return result;
        }



        public void maybeCreateValue(String name, String value, String prefix) {

            DataElement d = (DataElement)data.get(name);

            if (d == null || d.getValue() == null) try {
                SaveableData v = ValueFactory.create(name, value, this, prefix);
                if (d == null) {
                    DataFile f = guessDataFile(name);
                    d = add(name, v, f, true);
                    datafileModified(f);
                } else
                    putValue(name, v);
            } catch (MalformedValueException e) {
                d.setValue(new MalformedData(value));
            }
        }


//     private void maybeRealize(DataElement d) {
//
//       if ((d != null) && (d.value instanceof DeferredData))
//      synchronized (d) {
//        try {
//          d.value = ((DeferredData) d.value).realize();
//        } catch (ClassCastException e) {
//          // d.value isn't a DeferredData anymore .. someone beat us to it.
//        } catch (MalformedValueException e) {
//          printError(e);
//          d.value = null;
//        }
//      }
//     }


        public SaveableData getValue(String name) {

            DataElement d = (DataElement)data.get(name);
            if (d == null)
                return null;

            return d.getValue();
        }




        public final SimpleData getSimpleValue(String name) {
            DataElement d = (DataElement)data.get(name);
            if (d == null || d.getValue() == null)
                return null;
            else
                return d.getSimpleValue();
        }



        public SaveableData getInheritableValue(String prefix, String name) {
            String dataName = prefix + "/" + name;
            SaveableData result = getValue(dataName);
            int pos;
            while (result == null && prefix.length() > 0) {
                pos = prefix.lastIndexOf('/');
                if (pos == -1)
                    prefix = "";
                else
                    prefix = prefix.substring(0, pos);
                dataName = prefix + "/" + name;
                result = getValue(dataName);
            }
            return result;
        }



        private static final int MAX_RECURSION_DEPTH = 100;
        private int recursion_depth = 0;

        public void putValue(String name, SaveableData value) {


            if (recursion_depth < MAX_RECURSION_DEPTH) {
                recursion_depth++;
                DataElement d = (DataElement)data.get(name);

                if (d != null) {


                                        // change the value of the data element.
                    SaveableData oldValue = d.getValue();
                    d.setValue(value);

                                          // possibly mark the datafile as modified.
                    if (d.datafile != null &&
                        value != oldValue &&
                        (oldValue == null || value == null ||
                         !value.saveString().equals(oldValue.saveString())))
                        datafileModified(d.datafile);

                                            // possibly throw away the old value.
                    if (oldValue != null && oldValue != value)
                        oldValue.dispose();

                                            // notify any listeners registed for the change
                    dataNotifier.dataChanged(name, d);

                                          // check if this element is no longer needed.
                    maybeDelete(name, d);

                } else {
                    //  if the value was not already in the repository, add it.
                    DataFile f = guessDataFile(name);
                    add(name, value, f, true);
                    datafileModified(f);
                }

                recursion_depth--;
            } else {
                System.err.println
                    ("DataRepository detected circular dependency in data,\n" +
                     "    bailed out after " + MAX_RECURSION_DEPTH + " iterations.");
                new Exception().printStackTrace(System.err);
            }
        }


        private static final String includeTag = "#include ";
        private final Hashtable includedFileCache = new Hashtable();

        String lookupDefaultValue(String dataName, DataElement element) {
            // if the user didn't bother to look up the data element, look
            // it up for them.
            if (element == null) element = (DataElement)data.get(dataName);

            if (element == null ||                   // if there is no such element,
                element.datafile == null ||   // the element has no datafile, or its
                element.datafile.inheritsFrom == null)  // datafile doesn't inherit,
                return null;                        // then the default value is null.

            DataFile datafile = element.datafile;
            Map defaultValues = (Map) includedFileCache.get(datafile.inheritsFrom);
            if (defaultValues == null)
                return null;

            int prefixLength = datafile.prefix.length() + 1;
            String nameWithinDataFile = dataName.substring(prefixLength);
            return (String) defaultValues.get(nameWithinDataFile);
        }


        private InputStream findDatafile(String path, File currentFile) throws
            FileNotFoundException {
            InputStream result = null;
            File file = null;

                                      // find file in search path?
            if (path.startsWith("<")) {
                                              // strip <> chars
                path = path.substring(1, path.length()-1);

                URL u;
                URLConnection conn;
                                        // look in each template URL until we
                                        // find the named file
                for (int i = 0;  i < templateURLs.length;  i++) try {
                    u = new URL(templateURLs[i], path);
                    conn = u.openConnection();
                    conn.connect();
                    result = conn.getInputStream();
                    return result;
                } catch (IOException ioe) { }

                                        // couldn't find the file in any template
                                        // URL - give up.
                throw new FileNotFoundException("<" + path + ">");
            }

            if (path.startsWith("\""))
                path = path.substring(1, path.length()-1);

                                        // try opening the path as given.
            if ((file = new File(path)).exists()) return new FileInputStream(file);

                                      // if that fails, try opening it in the
                                      // same directory as currentFile.
            if (currentFile != null &&
                (file = new File(currentFile.getParent(), path)).exists())
                return new FileInputStream(file);

            throw new FileNotFoundException(path);    // fail.
        }


        // loadDatafile - opens the file passed to it and looks for "x = y" type
        // statements.  If one is found it associates x with y in the Hashtable
        // dest.  If an include statement is found on the first line, a recursive
        // call to loadDatafile is made, using the same Hashtable.  Return the
        // name of the include file, if one was found.

        private String loadDatafile(InputStream datafile, Map dest, boolean close)
            throws FileNotFoundException, IOException, InvalidDatafileFormat {

            // Initialize data, file, and read buffer.
            String inheritedDatafile = null;
            BufferedReader in = new BufferedReader(new InputStreamReader(datafile));
            String line, name, value;
            int equalsPosition;

            try {
                line = in.readLine();

                // if the first line is an include statement, load the data from
                // the file specified in the include statement.
                if (line != null && line.startsWith(includeTag)) {
                    inheritedDatafile = line.substring(includeTag.length()).trim();

                    // Add proper exception handling in case someone is somehow using
                    // the deprecated include syntax.
                    if (inheritedDatafile.startsWith("\"")) {
                        System.err.println("datafile #include directives with relative" +
                                           " paths are no longer supported.");
                        throw new InvalidDatafileFormat();
                    }

                    Map cachedIncludeFile =
                        (Map) includedFileCache.get(inheritedDatafile);

                    if (cachedIncludeFile == null) {
                        cachedIncludeFile = new HashMap();
                        // the null in the next line is a bug! it has no effect on
                        // #include <> statements, but effectively prevents #include ""
                        // statements from working (in other words, include directives
                        // relative to the current file.  Such directives are not
                        // currently used by the dashboard, so nothing will break.)
                        loadDatafile(findDatafile(inheritedDatafile, null),
                                     cachedIncludeFile, true);
                        cachedIncludeFile = Collections.unmodifiableMap(cachedIncludeFile);
                        includedFileCache.put(inheritedDatafile, cachedIncludeFile);
                    }
                    dest.putAll(cachedIncludeFile);
                    line = in.readLine();
                }

                // find a line with a valid = assignment and load its data into
                // the destination Hashtable
                for( ;  line != null;  line = in.readLine()) {
                    if (line.startsWith("=") || line.trim().length() == 0)
                        continue;

                    if ((equalsPosition = line.indexOf('=', 0)) == -1)
                        throw new InvalidDatafileFormat();

                    name = line.substring(0, equalsPosition);
                    value = line.substring(equalsPosition+1);
                    if (value.equals("null") || value.equals("=null"))
                        dest.remove(name);
                    else
                        dest.put(name, value);
                }
            }
            finally {
                if (close) in.close();
            }

            return inheritedDatafile;
        }

        private Hashtable globalDataDefinitions = new Hashtable();

        public void addGlobalDefinitions(InputStream datafile, boolean close)
            throws FileNotFoundException, IOException, InvalidDatafileFormat {
            loadDatafile(datafile, globalDataDefinitions, close);
        }

        /** Perform renaming operations found in the values map.
         *
         * A simple renaming operation is a mapping whose value begins
         * with "<=".  The key is the new name for the data, and the rest
         * of the value is the original name.  So the following lines in a
         * datafile: <pre>
         *    foo="bar
         *    baz=<=foo
         * </pre> would be equivalent to the single line `baz="bar'.
         * Simple renaming operations are correctly transitive, so <pre>
         *   foo=1
         *   bar=<=foo
         *   baz=<=bar
         * </pre> is equivalent to `baz=1'. This will work correctly, no matter
         * what order the lines appear in.
         *
         * Pattern match renaming operations are mappings whose value
         * begins with >~.  The key is a pattern to match, and the value
         * is the substitution expression.  So <pre>
         *    foo 1="one
         *    foo 2="two
         *    foo ([0-9])+=>~$1/foo
         * </pre> would be equivalent to the lines <pre>
         *    1/foo="one
         *    2/foo="two
         * </pre> The pattern must match the original name of the element - not
         * any renamed variant.  Therefore, pattern match renaming operations
         * <b>cannot</b> be chained.  A pattern match operation <b>can</b> be
         * the <b>first</b> renaming operation in a transitive chain, but will
         * neverbe used as the second or subsequent operations in a chain.
         *
         * Finally, renaming operations can influence dataFiles below them in
         * the datafile inheritance chain.  This is, in fact, the #1 reason for
         * the renaming mechanism.  It allows a process datafile to rename
         * elements that appear in end-user project datafiles.
         *
         * @return true if any renames took place.
         */
        private boolean performRenames(Hashtable values)
         throws InvalidDatafileFormat {
            boolean dataWasRenamed = false;
            Hashtable renamingOperations = new Hashtable(),
                patternRenamingOperations = new Hashtable();

            // Perform a pass through the value map looking for renaming operations.
            Iterator i = values.entrySet().iterator();
            String name, value;
            Map.Entry e;
            while (i.hasNext()) {
                e = (Map.Entry) i.next();
                name = (String) e.getKey();
                value = (String) e.getValue();

                if (value.startsWith(SIMPLE_RENAME_PREFIX)) {
                    renamingOperations.put
                        (name, value.substring(SIMPLE_RENAME_PREFIX.length()));
                    i.remove();
                } else if (value.startsWith(PATTERN_RENAME_PREFIX)) {
                    patternRenamingOperations.put
                        (name, value.substring(PATTERN_RENAME_PREFIX.length()));
                    i.remove();
                }
            }

            // For each pattern-style renaming operation, find data names that
            // match the pattern and add the corresponding renaming operation to
            // the regular naming operation list.
            i = patternRenamingOperations.entrySet().iterator();
            String re;
            while (i.hasNext()) {
                e = (Map.Entry) i.next();
                name = (String) e.getKey();
                value = (String) e.getValue();

                re = "s\n^" + name + "$\n" + value + "\n";
                // scan the value map for matching names.
                Enumeration valueNames = values.keys();
                String valueName, valueRename;
                while (valueNames.hasMoreElements()) {
                    valueName = (String) valueNames.nextElement();
                    try {
                        valueRename = ValueFactory.perl.substitute(re, valueName);
                        if (!valueName.equals(valueRename))
                            renamingOperations.put(valueRename, valueName);
                    } catch (MalformedPerl5PatternException mpe) {
                        System.err.println("Malformed renaming operation '" + name +
                                           "=" + PATTERN_RENAME_PREFIX + value + "'");
                        throw new InvalidDatafileFormat();
                    }
                }
            }

            // Now perform the renaming operations.
            String oldName, newName;
            i = renamingOperations.entrySet().iterator();
            while (!renamingOperations.isEmpty()) {
                newName = (String) renamingOperations.keySet().iterator().next();
                oldName = (String) renamingOperations.remove(newName);
                value   = (String) values.remove(oldName);
                while (value == null &&
                       (oldName = (String) renamingOperations.remove(oldName)) != null)
                    value = (String) values.remove(oldName);

                if (value != null) {
                    values.put(newName, value);
                    dataWasRenamed = true;
                }
            }
            return dataWasRenamed;
        }
        private static final String SIMPLE_RENAME_PREFIX = "<=";
        private static final String PATTERN_RENAME_PREFIX = ">~";


        public void openDatafile(String dataPrefix, String datafilePath)
            throws FileNotFoundException, IOException, InvalidDatafileFormat {

            // debug("openDatafile");

            Hashtable values = new Hashtable();

            DataFile dataFile = new DataFile();
            dataFile.prefix = dataPrefix;
            dataFile.file = new File(datafilePath);
            dataFile.inheritsFrom =
                loadDatafile(new FileInputStream(dataFile.file), values, true);
            boolean dataModified;
            boolean registerDataNames = (dataPrefix.length() > 0);

            // perform any renaming operations that were requested in the datafile
            dataModified = performRenames(values);

                                    // only add the datafile element if the
                                    // loadDatafile process was successful
            datafiles.addElement(dataFile);

            startInconsistency();

            try {
                if (dataPrefix.equals(realizeDeferredDataFor))
                    realizeDeferredDataFor = dataFile;

                boolean fileEditable = dataFile.file.canWrite();
                boolean dataEditable = true;

                String localName, name, value;
                SaveableData o;
                DataElement d;

                Enumeration dataNames = values.keys();
                while (dataNames.hasMoreElements()) {
                    localName = (String) dataNames.nextElement();
                    value = (String) values.get(localName);
                    name = dataPrefix + "/" + localName;

                    if (value.startsWith("=")) {
                        dataEditable = false;
                        value = value.substring(1);
                    } else
                        dataEditable = true;

                    if (value.equalsIgnoreCase("@now"))
                        dataModified = true;
                    try {
                        o = ValueFactory.createQuickly(name, value, this, dataPrefix);
                    } catch (MalformedValueException mfe) {
                        System.err.println("Data value for '"+dataPrefix+"/"+name+
                                           "' in file '"+datafilePath+"' is malformed.");
                        o = new MalformedData(value);
                    }
                    if (!fileEditable || !dataEditable)
                        if (o != null) o.setEditable(false);
                    d = (DataElement)data.get(name);
                    if (d == null) {
                        if (o != null) d = add(name, o, dataFile, true);
                    } else {
                                          // this prevents the putValue logic from
                        d.datafile = null;  // marking the datafile as modified
                        putValue(name, o);
                        d.datafile = dataFile;
                    }
                    // this is necessary because the mechanisms above which set the
                    // value of a DataElement do so AFTER setting the datafile.
                    if (dataFile == realizeDeferredDataFor && o instanceof DeferredData)
                        dataRealizer.addElement(d);

                    if (registerDataNames &&
                        (o instanceof DoubleData || o instanceof DeferredData))
                        dataElementNameSet.add(localName);
                }

                if (dataModified)
                    datafileModified(dataFile);

                // make a call to getID.  We don't need the resulting value, but
                // having made the call will cause an ID to be mapped for this
                // prefix.  This is necessary to allow users to bring up HTML pages
                // from their browser's history or bookmark list.
                //
                getID(dataPrefix);
                // debug("openDatafile done");
            } finally {
                finishInconsistency();
            }
        }

        private static final int MAX_DIRTY = 10;


        private void datafileModified(DataFile datafile) {
            if (datafile != null && ++datafile.dirtyCount > MAX_DIRTY)
                saveDatafile(datafile);
        }

        public Iterator getKeys() {
            ArrayList l = new ArrayList();
            synchronized (data) {
                l.addAll(data.keySet());
            }
            return l.iterator();
        }

        // saveDataFile - saves a set of data to the appropriate data file.  In
        // order to minimize data loss, data is first written to two temporary
        // files, out and backup.  Once this is successful, out is renamed to
        // the actual datafile.  Once the rename is successful, backup is
        // deleted.
        private void saveDatafile(DataFile datafile) {
            synchronized(datafile) {
                // debug("saveDatafile");

                String fileSep = System.getProperty("file.separator");

                // Create temporary files
                File tempFile = new File(datafile.file.getParent() +
                                         fileSep + "tttt_" + datafile.file.getName());
                File backupFile = new File(datafile.file.getParent() + fileSep +
                                           "tttt" + datafile.file.getName() );
                BufferedWriter out;
                BufferedWriter backup;

                try {
                    out = new BufferedWriter(new FileWriter(tempFile));
                    backup = new BufferedWriter(new FileWriter(backupFile));

                } catch (IOException e) {
                    System.err.println("IOException " + e + " while opening " +
                                       datafile.file.getPath() + "; save aborted");
                    return;
                }

                Map defaultValues;

                // if the data file has an include statement, write it to the
                // the two temporary output files.
                if (datafile.inheritsFrom != null) {
                    defaultValues = (Map) includedFileCache.get(datafile.inheritsFrom);
                    try {
                        out.write(includeTag + datafile.inheritsFrom);
                        out.newLine();
                        backup.write(includeTag + datafile.inheritsFrom);
                        backup.newLine();
                    } catch (IOException e) {}
                } else {
                    defaultValues = new HashMap();
                }

                // If the data file has a prefix, write it as a comment to the
                // two temporary output files.
                if (datafile.prefix != null && datafile.prefix.length() > 0) try {
                    out.write   ("= Data for " + datafile.prefix); out.newLine();
                    backup.write("= Data for " + datafile.prefix); backup.newLine();
                } catch (IOException e) {}

                datafile.dirtyCount = 0;

                Iterator k = getKeys();
                String name, valStr, defaultValStr;
                DataElement element;
                SaveableData value;
                int prefixLength = datafile.prefix.length() + 1;

                // write the data elements to the two temporary output files.
                while (k.hasNext()) {
                    name = (String)k.next();
                    element = (DataElement)data.get(name);

                    // Make a quick check on the element and datafile validity
                    // before taking the time to get the value
                    if ((element != null) && (element.datafile == datafile)) {
                        // don't realize the data if it is still deferred.
                        value = element.getImmediateValue();
                        if (value != null) {
                            try {
                                name = name.substring(prefixLength);

                                valStr = value.saveString();
                                if (!value.isEditable()) valStr = "=" + valStr;
                                defaultValStr = (String) defaultValues.get(name);
                                if (valStr.equals(defaultValStr))
                                    continue;

                                out.write(name);
                                out.write('=');
                                out.write(valStr);
                                out.newLine();

                                backup.write(name);
                                backup.write('=');
                                backup.write(valStr);
                                backup.newLine();
                            } catch (IOException e) {
                                System.err.println("IOException " + e + " while writing " +
                                                   name + " to " + datafile.file.getPath());
                            }
                        }
                    }
                }

                try {
                    // Close the temporary output files
                    out.flush();
                    out.close();

                    backup.flush();
                    backup.close();

                    // rename out to the real datafile
                    datafile.file.delete();
                    tempFile.renameTo(datafile.file);

                    // delete the backup
                    backupFile.delete();

                } catch (IOException e) {
                    System.err.println("IOException " + e + " while closing " +
                                       datafile.file.getPath());
                }

                System.err.println("Saved " + datafile.file.getPath());
                // debug("saveDatafile done");
            }
        }



        public String makeUniqueName(String baseName) {
            // debug("makeUniqueName");
                int id = 0;

                if (baseName == null) baseName = "///Internal_Name";

                while (data.get(baseName + id) != null) id++;

                putValue(baseName + id, null);

        // debug("makeUniqueName done");
            return (baseName + id);
        }




        public void addDataListener(String name, DataListener dl) {
            DataElement d;
            synchronized (data) {
                d = (DataElement)data.get(name);
                if (d == null)
                    d = add(name, null, guessDataFile(name), true);
            }

            if (d.dataListenerList == null) d.dataListenerList = new Vector();
            if (!d.dataListenerList.contains(dl))
                d.dataListenerList.addElement(dl);

            dataNotifier.addEvent(name, d, dl);
        }

        public void addActiveDataListener
            (String name, DataListener dl, String dataListenerName) {
            addDataListener(name, dl);
            activeData.put(dl, dataListenerName);
        }


        private void maybeDelete(String name, DataElement d) {

            if (d.dataListenerList == null) {
                if (d.getValue() == null)
                    data.remove(name);            // throw it away.

            } else if (d.dataListenerList.isEmpty())

                                              // if no one cares about this element,
                if (d.getValue() == null)       // and it has no value,
                    data.remove(name);            // throw it away.

                                                  // if no one cares about this element
                else if (d.datafile == null) {  // and it has no datafile,
                    data.remove(name);            // throw it away and
                    d.getValue().dispose();       // dispose of its value.
                }

        }


        public void removeDataListener(String name, DataListener dl) {
            // debug("removeDataListener");
                DataElement d = (DataElement)data.get(name);

                if (d != null)
                    if (d.dataListenerList != null) {

                        // remove the specified data listener from the list of data
                        // listeners for this element.  NOTE! We do not delete the
                        // dataListenerList here if it becomes empty...see the comments on
                        // the dataListenerList element of the DataElement construct.

                        d.dataListenerList.removeElement(dl);
                        dataNotifier.removeDataListener(name, dl);
                        maybeDelete(name, d);
                    }
                // debug("removeDataListener done");
            }


        public void deleteDataListener(DataListener dl) {
            // debug("deleteDataListener");

            Iterator dataElements = getKeys();
            String name = null;
            DataElement element = null;
            Vector listenerList = null;

                      // walk the hashtable, removing this datalistener.
            while (dataElements.hasNext()) {
                name = (String) dataElements.next();
                element = (DataElement) data.get(name);
                if (element != null) {
                    listenerList = element.dataListenerList;
                    if (listenerList != null && listenerList.removeElement(dl))
                        maybeDelete(name, element);
                }
            }
            dataNotifier.deleteDataListener(dl);
            activeData.remove(dl);
            // debug("deleteDataListener done");
        }



        public void addRepositoryListener(RepositoryListener rl, String prefix) {
            // debug("addRepositoryListener");

                                      // add the listener to our repository list.
                repositoryListenerList.addListener(rl, prefix);

                                        // notify the listener of all the elements
                                        // already in the repository.
                Iterator k = getKeys();
                String name;


                if (prefix != null && prefix.length() != 0)

                                        // if they have specified a prefix, notify them
                                        // of all the data beginning with that prefix.
                    while (k.hasNext()) {
                        if ((name = (String) k.next()).startsWith(prefix) &&
                            data.containsKey(name))
                            rl.dataAdded(new DataEvent(this, name,
                                                       DataEvent.DATA_ADDED, null));
                    }

                else                    // if they have specified no prefix, only
                                        // notify them of data that is NOT anonymous.
                    while (k.hasNext())
                        if (!(name = (String) k.next()).startsWith(anonymousPrefix) &&
                            data.containsKey(name))
                            rl.dataAdded(new DataEvent(this, name,
                                                       DataEvent.DATA_ADDED, null));

                // debug("addRepositoryListener done");
        }



        public void removeRepositoryListener(RepositoryListener rl) {
            // debug("removeRepositoryListener");
            repositoryListenerList.removeListener(rl);
            // debug("removeRepositoryListener done");
        }

        private volatile int inconsistencyDepth = 0;
        private Set consistencyListeners =
            Collections.synchronizedSet(new HashSet());

        public void addDataConsistencyObserver(DataConsistencyObserver o) {
            boolean callbackImmediately = false;
            synchronized (consistencyListeners) {
                if (inconsistencyDepth == 0)
                    callbackImmediately = true;
                else
                    consistencyListeners.add(o);
            }
            if (callbackImmediately) o.dataIsConsistent();
        }

        public void startInconsistency() {
            synchronized (consistencyListeners) { inconsistencyDepth++; }
        }

        public void finishInconsistency() {
            synchronized (consistencyListeners) {
                if (--inconsistencyDepth == 0 &&
                    !consistencyListeners.isEmpty()) {
                    ConsistencyNotifier notifier =
                        new ConsistencyNotifier(consistencyListeners);
                    consistencyListeners.clear();
                    notifier.start();
                }
            }
        }

        private class ConsistencyNotifier extends Thread {
            private Set listenersToNotify;

            public ConsistencyNotifier(Set listeners) {
                listenersToNotify = new HashSet(listeners);
            }

            public void run() {
                // give things a chance to settle down.
                System.out.println("waiting for notifier at " + new java.util.Date());
                dataNotifier.flush();
                System.out.println("notifier done at " + new java.util.Date());

                Iterator i = listenersToNotify.iterator();
                DataConsistencyObserver o;
                while (i.hasNext()) {
                    o = (DataConsistencyObserver) i.next();
                    o.dataIsConsistent();
                }
            }
        }

        public Vector listDataNames(String prefix) {
            Vector result = new Vector();
            Iterator names = getKeys();
            String name;
            while (names.hasNext()) {
                name = (String) names.next();
                if (name.startsWith(prefix))
                    result.addElement(name);
            }
            return result;
        }

        private void debug(String msg) {
            System.out.println(msg);
        }

        private void printError(Exception e) {
            System.err.println("Exception: " + e);
            e.printStackTrace(System.err);
        }

        public String getID(String prefix) {
            // if we already have a mapping for this prefix, return it.
            String ID = (String) PathIDMap.get(prefix);
            if (ID != null) return ID;

            // try to come up with a good ID Number for this prefix.  As a first
            // guess, use the hashCode of the path to the datafile for this prefix.
            // This way, with any luck, the same project will map to the same ID
            // Number each time the program runs (since the name of the datafile
            // will most likely never change after the project is created).

                                      // find the datafile associated with 'prefix'
            String datafileName = "null";
            for (int index = datafiles.size();  index-- > 0; ) {
                DataFile datafile = (DataFile) datafiles.elementAt(index);
                if (datafile.prefix.equals(prefix)) {
                    datafileName = datafile.file.getPath();
                    break;
                }
            }
                                      // compute the hash of the datafileName.
            int IDNum = datafileName.hashCode();
            ID = Integer.toString(IDNum);

                      // if that ID Number is taken,  increment and try again.
            while (IDPathMap.containsKey(ID))
                ID = Integer.toString(++IDNum);

                        // store the ID-path pair in the hashtables.
            PathIDMap.put(prefix, ID);
            IDPathMap.put(ID, prefix);
            return ID;
        }

        public String getPath(String ID) {
            return (String) IDPathMap.get(ID);
        }

        private void remapIDs(String oldPrefix, String newPrefix) {
            String ID = (String) PathIDMap.remove(oldPrefix);

            if (ID != null) {
                PathIDMap.put(newPrefix, ID);
                IDPathMap.put(ID, newPrefix);
            }

            if (dataServer != null)
                dataServer.deletePrefix(oldPrefix);
        }

        public Set getDataElementNameSet() { return dataElementNameSet_ext; }

}
