// Copyright (C) 2002-2010 Tuma Solutions, LLC
// Team Functionality Add-ons for the Process Dashboard
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 3
// of the License, or (at your option) any later version.
//
// Additional permissions also apply; see the README-license.txt
// file in the project root directory for more information.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, see <http://www.gnu.org/licenses/>.
//
// The author(s) may be contacted at:
//     processdash@tuma-solutions.com
//     processdash-devel@lists.sourceforge.net


package teamdash.wbs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import net.sourceforge.processdash.util.RobustFileWriter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import teamdash.XMLUtils;


public class WorkflowLibrary {

    private static final String WORKFLOW_LIBRARY_TAG = "workflowLibrary";
    private static final String PROCESS_NAME_ATTR = "processName";
    private static final String PROCESS_VERSION_ATTR = "processVersion";

    private File file;
    private String processName;
    private String processVersion;
    private WorkflowWBSModel workflows;



    public WorkflowLibrary(File f) throws IOException {
        this.file = f;
        load();
    }

    public WorkflowLibrary(File f, TeamProcess process) throws IOException {
        if (f.exists())
            throw new IOException("File already exists.");

        this.file = f;
        this.processName = process.getProcessName();
        this.processVersion = process.getProcessVersion();
        this.workflows = new WorkflowWBSModel("Archived Workflows");
        // The constructor for WBSModel will create a 'default' WBS which we
        // don't want.  Delete those contents, leaving only the root.
        WBSNode[] children = this.workflows.getDescendants(this.workflows.getRoot());
        this.workflows.deleteNodes(Arrays.asList(children));
    }

    public String getProcessName() {
        return processName;
    }

    public String getProcessVersion() {
        return processVersion;
    }

    public WorkflowWBSModel getWorkflows() {
        return workflows;
    }

    public boolean isValid() {
        return (processName != null && processVersion != null && workflows != null);
    }

    public boolean compatible(TeamProcess process) {
        return getProcessName().equals(process.getProcessName()) &&
            getProcessVersion().equals(process.getProcessVersion());
    }


    private void load() throws IOException {
        try {
            Document doc = XMLUtils.parse(new FileInputStream(file));
            Element xml = doc.getDocumentElement();
            if (WORKFLOW_LIBRARY_TAG.equals(xml.getTagName())) {
                processName = xml.getAttribute(PROCESS_NAME_ATTR);
                processVersion = xml.getAttribute(PROCESS_VERSION_ATTR);
                Element wbsElement = (Element) xml.getElementsByTagName
                    (WBSModel.WBS_MODEL_TAG).item(0);
                workflows = new WorkflowWBSModel(wbsElement);
            }
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            IOException ioe = new IOException("Unable to load workflow library.");
            ioe.initCause(e);
            throw ioe;
        }
    }

    public void save() throws IOException {
        try {
            RobustFileWriter out = new RobustFileWriter(file, "UTF-8");
            out.write("<" + WORKFLOW_LIBRARY_TAG + " ");
            out.write(PROCESS_NAME_ATTR + "='" + XMLUtils.escapeAttribute(processName) + "' ");
            out.write(PROCESS_VERSION_ATTR + "='" + XMLUtils.escapeAttribute(processVersion) + "'>\n");
            workflows.getAsXML(out);
            out.write("</" + WORKFLOW_LIBRARY_TAG + ">");
            out.close();
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            IOException ioe = new IOException("Unable to save workflow library.");
            ioe.initCause(e);
            throw ioe;
        }
    }

    public String getFileName() {
        return file.getName();
    }

}