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

import javax.swing.*;
import javax.swing.event.*;
import java.awt.FileDialog;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Properties;

class ConfigureButton extends JMenuBar implements ActionListener {
    PSPDashboard  parent       = null;
    PropertyFrame prop_frame   = null;
    TaskTemplate  task_frame   = null;
    TimeLogEditor time_frame   = null;
    DefectEditor  defect_frame = null;
    ImportExport  impexp_frame = null;
    ProbeDialog   probe_dialog = null;

    static String FILE_SEP = null;
    static final String HELP_URL = ScriptButton.URL_PREFIX +"0/help/PSPDash.htm";
    static final String ABOUT_URL = ScriptButton.URL_PREFIX + "0/help/about.htm";

                                  // indices into menu labels
    static final int HIERARCHY_FRAME    = 0;
    static final int TIME_LOG_FRAME     = 1;
    static final int DEFECT_LOG_FRAME   = 2;
    static final int PROBE_DIALOG       = 3;
    //static final int TASK_DIALOG      = 4; // disabled
    static final int IMPORT_EXPORT      = 4;
    static final int HELP_FRAME         = 5;
    static final int ABOUT_DIALOG       = 6;
    static final int FIRST_HISTORY_ITEM = 6;
    static final int MAX_HISTORY_SIZE   = 0;

                                  // menu labels & cmd text (see above)
    static final String [] menuLabels =
        {new String ("Hierarchy"),
         new String ("Time Log"),
         new String ("Defect Log"),
         new String ("PROBE"),
         //     new String ("Task & Schedule"),
         new String ("Import/Export"),
         new String ("Help"),
         new String ("About")};

    ConfigureButton(PSPDashboard dash) {
        super();
        parent = dash;

        String    s;
        JMenu     menu = new JMenu("C");
        JMenuItem menuItem;
        add (menu);

        for (int ii = 0; ii < menuLabels.length; ii++) {
            menuItem = menu.add(new JMenuItem(menuLabels[ii]));
            menuItem.setActionCommand(menuLabels[ii]);
            menuItem.addActionListener(this);
        }

        //popup.addSeparator();
        //add history elements here? (up to MAX_HISTORY_SIZE)

        dash.getContentPane().add(this);

                                    // get needed system properties
        Properties prop = System.getProperties ();
        FILE_SEP = prop.getProperty ("file.separator");
    }

//  public void setHistoryItem(String label, String dest) {
//    update history portion of popup menu here (save where?????)
//  }

    protected void startPropertyFrame () {
        if (parent.getProperties() != null) {
            if (prop_frame != null)
                prop_frame.show();
            else
                prop_frame = new PropertyFrame(parent,
                                               this,
                                               parent.getProperties(),
                                               parent.getTemplateProperties());
        }
    }

    protected void startProbeDialog () {
        if (probe_dialog != null)
            probe_dialog.show();
        else
            probe_dialog = new ProbeDialog(parent);
    }

    public void startTimeLog() {
        if (parent.getProperties() != null) {
            if (time_frame != null)
                time_frame.show();
            else
                time_frame = new TimeLogEditor(parent,
                                               this,
                                               parent.getProperties());
        }
    }

    public void startDefectLog() {
        if (parent.getProperties() != null) {
            if (defect_frame != null)
                defect_frame.showIt();
            else
                defect_frame = new DefectEditor(parent,
                                                this,
                                                parent.getProperties());
        }
    }

    public void startImportExport() {
        if (parent.getProperties() != null) {
            if (impexp_frame != null)
                impexp_frame.show();
            else
                impexp_frame = new ImportExport(parent);
        }
    }

    public void addToTimeLogEditor (TimeLogEntry tle) {
        if (time_frame != null)
            time_frame.addRow (tle);
    }

    public void startTaskDialog() {
        if (parent.getProperties() != null) {
            if (task_frame != null)
                task_frame.show();
            else
                task_frame = new TaskTemplate(parent,
                                              this,
                                              parent.getProperties());
        }
    }

    public void startHelp() { Browser.launch(HELP_URL); }

    public void startAboutDialog() { new AboutDialog(parent, ABOUT_URL); }

    public void save() {
        if (task_frame != null)
            task_frame.save();
    }

    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();

        if (cmd.equals(menuLabels[HIERARCHY_FRAME])) {
            startPropertyFrame ();
        } else if (cmd.equals(menuLabels[TIME_LOG_FRAME])) {
            startTimeLog();
        } else if (cmd.equals(menuLabels[DEFECT_LOG_FRAME])) {
            startDefectLog();
        } else if (cmd.equals(menuLabels[PROBE_DIALOG])) {
            startProbeDialog ();
//  } else if (cmd.equals(menuLabels[TASK_DIALOG])) {
//    startTaskDialog ();
        } else if (cmd.equals(menuLabels[IMPORT_EXPORT])) {
            startImportExport ();
        } else if (cmd.equals(menuLabels[HELP_FRAME])) {
            startHelp ();
        } else if (cmd.equals(menuLabels[ABOUT_DIALOG])) {
            startAboutDialog ();
        }
    }

}
