package teamdash.templates.setup;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.swing.tree.TreePath;

import net.sourceforge.processdash.data.ListData;
import net.sourceforge.processdash.data.SimpleData;
import net.sourceforge.processdash.data.StringData;
import net.sourceforge.processdash.data.repository.DataRepository;
import net.sourceforge.processdash.ev.EVTask;
import net.sourceforge.processdash.ev.EVTaskList;
import net.sourceforge.processdash.ev.EVTaskListRollup;
import net.sourceforge.processdash.hier.DashHierarchy;
import net.sourceforge.processdash.hier.PropertyKey;
import net.sourceforge.processdash.ui.web.TinyCGIBase;
import net.sourceforge.processdash.util.HTMLUtils;

public class EditSubprojectList extends TinyCGIBase {

    private static final String DISPLAY_ACTION = "display";

    private static final String ADD_ACTION = "add";

    private static final String EDIT_ACTION = "edit";

    private static final String REMOVE_ACTION = "remove";

    private static final String CANCEL_ACTION = "cancel";


    private static final String NUMBER = "subproject_number";

    private static final String PATH = "Hierarchy_Path";

    private static final String SHORT_NAME = "Short_Name";

    private static Logger logger = Logger.getLogger(EditSubprojectList.class
            .getName());


    /**
     * Data holder class, for storing information about a subproject.
     */
    private class Subproject {
        public String num;

        public String shortName;

        public String path;

        public Subproject(String num, String shortName, String path) {
            this.num = num;
            this.shortName = shortName;
            this.path = path;
        }
    }


    protected void writeContents() throws IOException {
        if (parameters.containsKey(DISPLAY_ACTION))
            showProjectList();
        else if (parameters.containsKey(ADD_ACTION))
            showAddPage();
        else if (parameters.containsKey(EDIT_ACTION))
            showEditPage();
        else if (parameters.containsKey(REMOVE_ACTION))
            showRemovePage();
        else if (parameters.containsKey(CANCEL_ACTION))
            writeCloseWindow(false);
        else {
            String handleAction = getParameter("do");
            if (ADD_ACTION.equals(handleAction))
                doAdd();
            else if (EDIT_ACTION.equals(handleAction))
                doEdit();
            else if (REMOVE_ACTION.equals(handleAction))
                doRemove();
            else
                writeCloseWindow(false);
        }
    }

    /*
     * Entry methods to show a particular page
     */

    private void showProjectList() {
        Map subprojects = getSubprojects();

        if (subprojects.isEmpty()) {
            out.println("<tr>");
            out.println("<td colspan='3' align='center'><i>This master "
                    + "project currently contains no subprojects</i></td>");
            out.println("</tr>");
            return;
        }

        for (Iterator i = subprojects.values().iterator(); i.hasNext();) {
            Subproject proj = (Subproject) i.next();
            out.println("<tr>");
            out.print("<td>");
            out.print(HTMLUtils.escapeEntities(proj.shortName));
            out.println("</td>");
            out.print("<td>");
            out.print(HTMLUtils.escapeEntities(proj.path));
            out.println("</td>");
            out.print("<td><form action='subprojectEdit' method='GET' "
                    + "target='popup'><input type='submit' name='edit' "
                    + "value='Edit...' onClick='popup();'>&nbsp;<input "
                    + "type='submit' name='remove' value='Remove...' "
                    + "onClick='popup();'><input type='hidden' "
                    + "name='subproject_number' value='");
            out.print(proj.num);
            out.println("'></form></td>");
            out.println("</tr>");
        }
    }

    private void showAddPage() {
        showAddPage("", null, "", null);
    }

    private void showAddPage(String shortName, String shortNameError,
            String path, String pathError) {
        ListData teamProjects = findValidSubprojectNames();
        if (teamProjects.size() == 0)
            showNothingToAddPage();
        else
            writeEntryForm("Add Subproject",
                    "Enter information for the subproject:", "#", shortName,
                    shortNameError, path, pathError, ADD_ACTION);
    }

    private void showNothingToAddPage() {
        writeFormStart("Add Subproject", NO_PROJECTS_FOUND_MESSAGE, "",
                CANCEL_ACTION);
        writeFormEnd(false);
    }

    private static final String NO_PROJECTS_FOUND_MESSAGE = ""
            + "This dashboard instance does not include any team projects "
            + "that are compatible with this master project.  Please create "
            + "subprojects using the Hierarchy Editor, and run the Team "
            + "Project Setup Wizard for each subproject.  Then return to "
            + "this page to add the subprojects to this master project.";

    private void showEditPage() {
        String num = getParameter(NUMBER);
        String shortName = getValue(num, SHORT_NAME);
        String path = getValue(num, PATH);
        if (!hasValue(num) || !hasValue(shortName)) {
            writeCloseWindow(true);
            return;
        }

        showEditPage(num, shortName, null, path, null);
    }

    private void showEditPage(String num, String shortName,
            String shortNameError, String path, String pathError) {
        writeEntryForm("Edit Subproject",
                "Edit information for the subproject:", num, shortName,
                shortNameError, path, pathError, EDIT_ACTION);
    }

    private void showRemovePage() {
        String num = getParameter(NUMBER);
        String shortName = getValue(num, SHORT_NAME);
        String path = getValue(num, PATH);
        if (!hasValue(num) || !hasValue(shortName)) {
            writeCloseWindow(true);
            return;
        }

        writeFormStart("Remove Subproject",
                "Are you certain you want to remove this subproject?", num,
                REMOVE_ACTION);

        out.println("<table cellpadding='8'>");

        out.println("<tr><td><b>Short Name</b></td>");
        out.print("<td>");
        out.print(HTMLUtils.escapeEntities(shortName));
        out.print("</td></tr>");

        out.println("<tr><td><b>Subproject Path</b></td>");
        out.print("<td>");
        out.print(HTMLUtils.escapeEntities(path));
        out.print("</td></tr>");

        out.println("</table>");

        writeFormEnd(true);
    }

    /*
     * Helper methods to write portions of pages
     */

    private void writeEntryForm(String title, String prompt, String num,
            String shortName, String shortNameError, String path,
            String pathError, String action) {

        writeFormStart(title, prompt, num, action);

        out.println("<table cellpadding='8'>");

        out.println("<tr><td><b>Short Name</b></td>");
        out.print("<td><input type='text' size='10' name='" + SHORT_NAME
                + "' value='");
        out.print(HTMLUtils.escapeEntities(shortName));
        out.print("'>");
        maybeWriteError(shortNameError);
        out.print("</td></tr>");

        out.println("<tr><td><b>Subproject Path</b></td>");
        out.print("<td>");
        writePathSelector(path);
        maybeWriteError(pathError);
        out.print("</td></tr>");

        out.println("</table>");

        writeFormEnd(true);
    }

    private void writeFormStart(String title, String prompt, String num,
            String action) {
        out.println("<html><head>");
        out.print("<title>");
        out.print(title);
        out.println("</title><style>");
        out.println("td { text-align: left; vertical-align:top }");
        out.println("</style></head><body>");
        out.print("<h2>");
        out.print(title);
        out.println("</h2><p>");
        out.println(prompt);
        out.println("</p><form action='subprojectEdit' method='GET'>");
        out.println("<input type='hidden' name='" + NUMBER + "' value='" + num
                + "'>");
        out.println("<input type='hidden' name='do' value='" + action + "'>");
    }

    private void maybeWriteError(String shortNameError) {
        if (shortNameError != null) {
            out.print("<br><span style='color:red; font-style:italic'>");
            out.print(shortNameError);
            out.print("</span>");
        }
    }

    private void writePathSelector(String selectedPath) {
        ListData existingProjects = findValidSubprojectNames();
        out.println("<select name='" + PATH + "'>");
        out.println("<option value=''>Choose a subproject...</option>");
        for (int i = 0; i < existingProjects.size(); i++) {
            String onePath = (String) existingProjects.get(i);
            if (onePath.equals(selectedPath))
                out.print("<option selected>");
            else
                out.print("<option>");
            out.print(HTMLUtils.escapeEntities(onePath));
            out.println("</option>");
        }
        out.println("</select>");
    }

    private void writeFormEnd(boolean showCancel) {
        out.print("<table width='100%'><tr><td style='text-align:right'>"
                + "<input type='submit' name='OK' value='OK'> ");
        if (showCancel)
            out.print("<input type='submit' name='" + CANCEL_ACTION
                    + "' value='Cancel'>");
        out.println("</td></tr></table></form></body></html>");
    }

    private void writeCloseWindow(boolean refreshParent) {
        out.println("<html><body><script>");
        if (refreshParent)
            out.println("window.opener.location.reload();");
        out.println("window.close();");
        out.println("</script></body></html>");
    }

    /*
     * Methods to handle values posted to this form
     */

    private void doAdd() {
        Map subprojects = getSubprojects();
        String shortName = getParameter(SHORT_NAME);
        String path = getParameter(PATH);

        String shortNameError = validateShortName(null, shortName, subprojects);
        String pathError = validatePath(null, path, subprojects);
        if (shortNameError != null || pathError != null) {
            // there are problems with the values entered.  Send the user
            // back to the add page to try again.
            showAddPage(shortName, shortNameError, path, pathError);
            return;
        }

        int i = 0;
        while (getValue(i, SHORT_NAME) != null)
            i++;
        String num = getNum(i);

        putValue(num, SHORT_NAME, shortName.trim());
        putValue(num, PATH, path);
        recalcDependentData();
        writeCloseWindow(true);
    }

    private void doEdit() {
        String num = getParameter(NUMBER);
        if (!hasValue(num)) {
            writeCloseWindow(true);
            return;
        }

        Map subprojects = getSubprojects();
        String shortName = getParameter(SHORT_NAME);
        String path = getParameter(PATH);

        String shortNameError = validateShortName(num, shortName, subprojects);
        String pathError = validatePath(num, path, subprojects);
        if (shortNameError != null || pathError != null) {
            // there are problems with the values entered.  Send the user
            // back to the add page to try again.
            showEditPage(num, shortName, shortNameError, path, pathError);
            return;
        }

        putValue(num, SHORT_NAME, shortName);
        putValue(num, PATH, path);
        recalcDependentData();
        writeCloseWindow(true);
    }

    private void doRemove() {
        String numToDelete = getParameter(NUMBER);
        if (hasValue(numToDelete)) {
            Map subprojects = getSubprojects();
            subprojects.remove(numToDelete);

            int n = 0;
            for (Iterator i = subprojects.values().iterator(); i.hasNext();) {
                Subproject proj = (Subproject) i.next();
                String num = getNum(n++);
                putValue(num, SHORT_NAME, proj.shortName);
                putValue(num, PATH, proj.path);
            }

            for (int i = 0; i < 5; i++) {
                String num = getNum(n++);
                putValue(num, SHORT_NAME, null);
                putValue(num, PATH, null);
            }

            recalcDependentData();
        }
        writeCloseWindow(true);
    }

    /*
     * Methods for validating user input
     */

    private String validateShortName(String num, String shortName,
            Map subprojects) {
        if (shortName == null || shortName.trim().length() == 0)
            return "You must enter a short name for the subproject.";

        if (!OK_NAME_CHARS.matcher(shortName).matches())
            return "The short name for the subproject can only contain letters.";

        for (Iterator i = subprojects.values().iterator(); i.hasNext();) {
            Subproject proj = (Subproject) i.next();
            if (proj.shortName.equals(shortName) && !proj.num.equals(num))
                return "That name has already been given to another subproject.";
        }

        return null;
    }

    Pattern OK_NAME_CHARS = Pattern.compile("[a-zA-Z ]+");

    private String validatePath(String num, String path, Map subprojects) {
        if (path == null || path.trim().length() == 0)
            return "You must choose a subproject.";

        for (Iterator i = subprojects.values().iterator(); i.hasNext();) {
            Subproject proj = (Subproject) i.next();
            if (proj.path.equals(path) && !proj.num.equals(num))
                return "That subproject has already been added to this master project.";
        }

        return null;
    }

    /*
     * Methods for updating dashboard state based on the changes to the
     * subproject list
     */

    private void recalcDependentData() {
        Map subprojects = getSubprojects();

        saveSubprojectList(subprojects);
        updateEVSchedule(subprojects);
        writeSettingsFile(subprojects);
    }

    private void saveSubprojectList(Map subprojects) {
        ListData pathList = new ListData();
        for (Iterator i = subprojects.values().iterator(); i.hasNext();) {
            Subproject proj = (Subproject) i.next();
            pathList.add(proj.path);
        }
        putValue("Subproject_Path_List", pathList);
    }

    private void updateEVSchedule(Map subprojects) {
        String teamScheduleName = getValue("Project_Schedule_Name");
        logger.log(Level.FINE, "Updating master schedule \"{0}\"",
                teamScheduleName);

        // open the master schedule
        EVTaskList schedule = EVTaskList.openExisting(teamScheduleName,
                getDataRepository(), getPSPProperties(), getObjectCache(),
                false);
        if (!(schedule instanceof EVTaskListRollup)) {
            logger.log(Level.WARNING,
                    "could not find a rollup schedule named \"{0}\"",
                    teamScheduleName);
            return;
        }

        // empty all the children from the current master schedule.
        EVTaskListRollup master = (EVTaskListRollup) schedule;
        for (int i = master.getChildCount(master.getRoot()); i-- > 0;) {
            EVTask task = (EVTask) master.getChild(master.getRoot(), i);
            master.removeTask(new TreePath(task.getPath()));
        }

        // now add the schedules of all the subprojects.
        for (Iterator i = subprojects.values().iterator(); i.hasNext();) {
            Subproject proj = (Subproject) i.next();
            String dataName = DataRepository.createDataName(proj.path,
                    "Project_Schedule_Name");
            SimpleData d = getDataRepository().getSimpleValue(dataName);
            if (d == null) {
                logger.warning("Could not find schedule for subproject "
                        + proj.path);
                continue;
            }
            String subscheduleName = d.format();
            if (!master.addTask(subscheduleName, getDataRepository(),
                    getPSPProperties(), getObjectCache(), false)) {
                logger.warning("Could not add schedule for subproject "
                        + proj.path);
            }
        }

        master.save();
        logger.fine("saved changed task list");
    }

    private void writeSettingsFile(Map subprojects) {
        // TODO Auto-generated method stub
        // still need to write a settings.xml file for this project
        // that includes information about all subprojects
    }

    /*
     * Methods for getting data about this and other projects
     */

    private ListData findValidSubprojectNames() {
        ListData result = new ListData();

        String processID = getValue("Team_Process_PID");
        if (processID != null) {
            String templateID = processID + "/TeamRoot";
            findValidSubprojectNames(result, PropertyKey.ROOT, templateID);
        }

        return result;
    }

    private void findValidSubprojectNames(ListData result, PropertyKey key,
            String templateID) {
        DashHierarchy hier = getPSPProperties();
        String id = hier.getID(key);
        if (templateID.equals(id))
            result.add(key.path());
        else {
            int numChildren = hier.getNumChildren(key);
            for (int i = 0; i < numChildren; i++)
                findValidSubprojectNames(result, hier.getChildKey(key, i),
                        templateID);
        }
    }

    private Map getSubprojects() {
        LinkedHashMap result = new LinkedHashMap();
        int nullCount = 0;
        for (int i = 0; nullCount < 5; i++) {
            String shortName = getValue(i, SHORT_NAME);
            String path = getValue(i, PATH);
            if (hasValue(shortName)) {
                String num = getNum(i);
                result.put(num, new Subproject(num, shortName, path));
            } else
                nullCount++;
        }
        return result;
    }

    /*
     * Generic methods for interacting with data
     */

    private boolean hasValue(String s) {
        return s != null && s.length() > 0;
    }

    protected void putValue(String num, String attrName, String value) {
        putValue("Subproject_" + num + "/" + attrName, value == null ? null
                : StringData.create(value));
    }

    protected void putValue(String name, SimpleData dataValue) {
        DataRepository data = getDataRepository();
        String prefix = getPrefix();
        if (prefix == null)
            prefix = "";
        String dataName = DataRepository.createDataName(prefix, name);
        data.putValue(dataName, dataValue);
    }

    protected String getValue(int n, String attrName) {
        return getValue("Subproject_" + getNum(n) + "/" + attrName);
    }

    private String getNum(int n) {
        String num = Integer.toString(n);
        if (num.length() == 1)
            num = "0" + num;
        return num;
    }

    protected String getValue(String num, String attrName) {
        return getValue("Subproject_" + num + "/" + attrName);
    }

    /** Get a value from the data repository as a String. */
    protected String getValue(String name) {
        SimpleData d = getSimpleValue(name);
        return (d == null ? null : d.format());
    }

    /** Get a value from the data repository. */
    protected SimpleData getSimpleValue(String name) {
        DataRepository data = getDataRepository();
        String prefix = getPrefix();
        if (prefix == null)
            prefix = "";
        String dataName = DataRepository.createDataName(prefix, name);
        SimpleData d = data.getSimpleValue(dataName);
        return d;
    }

}
