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

package pspdash;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import pspdash.data.DataImporter;
import pspdash.data.DataRepository;
import pspdash.data.SimpleData;

public class DashController {

    static PSPDashboard dash = null;
    private static String localAddress = "127.0.0.1";

    public static void setDashboard(PSPDashboard dashboard) {
        dash = dashboard;
        try {
            localAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (IOException ioe) {}
    }

    public static void checkIP(Object remoteAddress) throws IOException {
        if (!"127.0.0.1".equals(remoteAddress) &&
            !localAddress.equals(remoteAddress))
            throw new IOException();
    }

    public static void raiseWindow() {
        if (dash.getState() == dash.ICONIFIED)
            dash.setState(dash.NORMAL);
        dash.show();
        dash.toFront();
    }

    public static void showTaskSchedule(String path) {
        // if no path was given, just display a chooser dialog to the user.
        if (path == null || path.length() == 0) {
            raiseWindow();
            new ChooserOpener(null);
            return;
        }

        // chop trailing "/" if it is present.
        if (path.endsWith("/")) path = path.substring(0, path.length()-1);

        // Make a list of data name prefixes that could indicate the
        // name of the task list for this path.
        ArrayList prefixList = new ArrayList();
        while (path != null) {
            prefixList.add(path);
            path = DataRepository.chopPath(path);
        }
        String[] prefixes = (String[]) prefixList.toArray(new String[0]);

        // Search the data repository for elements that begin with any of
        // the prefixes we just contructed.
        String dataName, prefix, ord_pref = "/"+EVTaskListData.TASK_ORDINAL_PREFIX;
        Iterator i = dash.data.getKeys();
        ArrayList taskLists = new ArrayList();

    DATA_ELEMENT_SEARCH:
        while (i.hasNext()) {
            dataName = (String) i.next();
            for (int j = prefixes.length;  j-- > 0; ) {
                prefix = prefixes[j];

                if (!dataName.startsWith(prefix))
                    // if the dataname doesn't start with this prefix, it
                    // won't start with any of the others either.  Go to the
                    // next data element.
                    continue DATA_ELEMENT_SEARCH;

                if (!dataName.regionMatches(prefix.length(), ord_pref,
                                            0, ord_pref.length()))
                    // If the prefix isn't followed by the ordinal tag
                    // "/TST_", try the next prefix.
                    continue;

                // we've found a match! Compute the resulting task list
                // name and add it to our list.
                dataName = dataName.substring
                    (prefix.length() + ord_pref.length());
                taskLists.add(dataName);
            }
        }

        raiseWindow();
        if (taskLists.size() == 1)
            TaskScheduleChooser.open(dash, (String) taskLists.get(0));
        else
            // open the chooser in a thread - it is likely to block by
            // opening a modal dialog.
            new ChooserOpener(taskLists);
    }
    private static class ChooserOpener extends Thread {
        private List taskLists;
        public ChooserOpener(List t) { taskLists = t; start(); }
        public void run() {
            if (taskLists == null)
                new TaskScheduleChooser(dash);
            else
                new TaskScheduleChooser
                    (dash, (String[]) taskLists.toArray(new String[0]));
        }
    }

    public static void exportData(String prefix) {
        String dataName = dash.data.createDataName
            (prefix, ImportExport.EXPORT_DATANAME);
        SimpleData filename = dash.data.getSimpleValue(dataName);
        if (filename != null && filename.test()) {
            Vector filter = new Vector();
            filter.add(prefix);
            ImportExport.export(dash, filter, new File(filename.format()));
        }
    }
    /*
    private static class ExportStarter extends Thread {
        String prefix, filename;
        public ExportStarter(String p, String f) {
            prefix = p;   filename = Settings.translateFile(f); }
        public void run() {
            Vector filter = new Vector();
            filter.add(prefix);
            ImportExport.exportInteractively
                (dash, dash, filter, new File(filename));
            raiseWindow();
        }
    }
    */


    public static void startTiming() { dash.pause_button.cont();  }
    public static void stopTiming()  { dash.pause_button.pause(); }
    public static boolean setPath(String path) {
        return dash.pause_button.setPath(path);
    }
    public static boolean setPhase(String phase) {
        return dash.pause_button.setPhase(phase);
    }

    public static void printNullDocument(PrintWriter out) {
        out.println("<HTML><HEAD><SCRIPT>");
        out.println("history.back();");
        out.println("</SCRIPT></HEAD><BODY></BODY></HTML>");
    }

    public static Map getTemplates() {
        Prop templates = dash.templates.pget(PropertyKey.ROOT);
        TreeMap result = new TreeMap();
        for (int i = templates.getNumChildren();   i-- > 0; ) {
            PropertyKey childKey = templates.getChild(i);
            Prop child = dash.templates.pget(childKey);
            result.put(child.getID(), Prop.unqualifiedName(childKey.name()));
        }
        return result;
    }

    public static boolean isHierarchyEditorOpen() {
        return dash.configure_button.isHierarchyEditorOpen();
    }

    public static void addTemplateDirToPath(String templateDir) {
        if (templateDir == null) return;
        templateDir = templateDir.replace('\\', '/');

        String templatePath = Settings.getVal(TEMPLATE_PATH);
        if (templatePath == null)
            InternalSettings.set(TEMPLATE_PATH, templateDir);

        else if (!templateSettingContainsDir(templatePath, templateDir)) {
            templatePath = templateDir + ";" + templatePath;
            InternalSettings.set(TEMPLATE_PATH, templatePath);
        }
    }
    private static final String TEMPLATE_PATH = "templates.directory";
    private static boolean templateSettingContainsDir(String setting,
                                                      String dir)
    {
        setting = ";" + setting + ";";
        dir     = ";" + dir     + ";";
        return setting.indexOf(dir) != -1;
    }

    public static boolean alterTemplateID(String prefix,
                                          String oldID,
                                          String newID) {
        PropertyKey key = dash.props.findExistingKey(prefix);
        String actualID = dash.props.getID(key);
        if (oldID == actualID || // handles "null == null" case
            (oldID != null && oldID.equals(actualID))) try {
            HierarchyAlterer a = new HierarchyAlterer(dash);
            a.addTemplate(prefix, newID);
            return true;
        } catch (Exception e) {}

        return false;
    }

    public static boolean loadNewTemplate(String jarfileName) {
        return dash.addTemplateJar(jarfileName);
    }

    public static HierarchyAlterer getHierarchyAlterer() {
        return new HierarchyAlterer(dash);
    }
    public static String getSettingsFileName() {
        return InternalSettings.getSettingsFileName();
    }
    public static void addImportSetting(String prefix, String importDir) {
        String importInstr = (prefix + "=>" + importDir);
        String imports = Settings.getVal(IMPORT_DIRS);
        if (imports == null)
            InternalSettings.set(IMPORT_DIRS, importInstr);
        else
            InternalSettings.set(IMPORT_DIRS, imports + "|" + importInstr);
        DataImporter.init(dash.data, importInstr);
    }

    public static void enableTeamSettings() {
        // enable earned value rollups.
        InternalSettings.set(EV_ROLLUP, "true");

        // listen on a repeatable port.
        String port = Settings.getVal(HTTP_PORT);
        if (port == null) {
            int portNum = getAvailablePort();
            InternalSettings.set(HTTP_PORT, Integer.toString(portNum));
            // start listening on that port.
            dash.changeHttpPort(portNum);
        }

    }

    private static int getAvailablePort() {
        for (int i = 0;   i < PORT_PATTERNS.length;   i++)
            for (int j = 2;   j < 10;   j++)
                if (isPortAvailable(i*j))
                    return i*j;
        return 3000;
    }

    private static boolean isPortAvailable(int port) {
        if (port < 1024) return false;
        boolean successful = false;
        try {
            ServerSocket a = new ServerSocket(port-1);
            ServerSocket b = new ServerSocket(port);
            successful = true;
            a.close();
            b.close();
        } catch (IOException ioe) {}
        return successful;
    }

    private static final String IMPORT_DIRS = "import.directories";
    private static final String EV_ROLLUP = "ev.enableRollup";
    private static final String HTTP_PORT = "http.port";
    private static final int[] PORT_PATTERNS = {
        1000, 1111, 1001, 1010, 1100, 1011, 1101, 1110 };

}
