// Copyright (C) 2016 Tuma Solutions, LLC
// Process Dashboard - Data Automation Tool for high-maturity processes
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

package net.sourceforge.processdash.team.group;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.event.EventListenerList;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlSerializer;

import net.sourceforge.processdash.DashController;
import net.sourceforge.processdash.DashboardContext;
import net.sourceforge.processdash.ProcessDashboard;
import net.sourceforge.processdash.Settings;
import net.sourceforge.processdash.data.ListData;
import net.sourceforge.processdash.data.SimpleData;
import net.sourceforge.processdash.data.StringData;
import net.sourceforge.processdash.data.repository.DataRepository;
import net.sourceforge.processdash.i18n.Resources;
import net.sourceforge.processdash.security.DashboardPermission;
import net.sourceforge.processdash.tool.bridge.client.DirectoryPreferences;
import net.sourceforge.processdash.tool.db.DatabasePlugin;
import net.sourceforge.processdash.tool.db.QueryRunner;
import net.sourceforge.processdash.tool.db.QueryUtils;
import net.sourceforge.processdash.util.RobustFileOutputStream;
import net.sourceforge.processdash.util.XMLUtils;

public class UserGroupManager {

    private static UserGroupManager INSTANCE = new UserGroupManager();

    public static UserGroupManager getInstance() {
        return INSTANCE;
    }



    private EventListenerList listeners;

    private String readOnlyCode;

    private DataRepository dataRepository;

    private DatabasePlugin databasePlugin;

    private QueryRunner query;

    private File sharedFile, customFile;

    private Set<Boolean> needsSave;

    private Map<String, UserGroup> groups;

    static final Resources resources = Resources
            .getDashBundle("ProcessDashboard.Groups");

    private UserGroupManager() {
        listeners = new EventListenerList();
        if (Settings.isReadOnly())
            readOnlyCode = "Read_Only";
    }

    public void init(DashboardContext ctx) {
        // ensure calling code has permission to perform initialization
        PERMISSION.checkPermission();

        // user groups are only relevant for team dashboards, so only perform
        // minimal setup if we are running in a personal dashboard.
        if (!Settings.isTeamMode()) {
            groups = Collections.EMPTY_MAP;
            return;
        }

        // save the data repository for future use
        dataRepository = ctx.getData();

        // retrieve an object for querying the database
        databasePlugin = QueryUtils.getDatabasePlugin(ctx.getData());
        query = databasePlugin.getObject(QueryRunner.class);

        // determine the file for storage of shared groups
        File wd = ((ProcessDashboard) ctx).getWorkingDirectory().getDirectory();
        sharedFile = new File(wd, "groups.dat");

        // determine the file for storage of custom/personal groups
        File appDir = DirectoryPreferences.getApplicationDirectory();
        File customDir = new File(appDir, "groups");
        String datasetID = DashController.getDatasetID().toLowerCase();
        String customFilename = "groups-" + datasetID + ".xml";
        customFile = new File(customDir, customFilename);

        // load group data from both files
        groups = new HashMap<String, UserGroup>();
        reloadGroups(false);
        reloadGroups(true);
        needsSave = new HashSet<Boolean>();
    }

    /**
     * Shared groups should not be editable in certain circumstances (for
     * example, when the team dashboard is in read-only mode). This method
     * indicates whether shared groups are read only.
     * 
     * @return null if shared groups can be edited; otherwise, a reason code
     *         explaining why they cannot
     */
    public String getReadOnlyCode() {
        return readOnlyCode;
    }

    public void addUserGroupEditListener(UserGroupEditListener l) {
        listeners.add(UserGroupEditListener.class, l);
    }

    public void removeUserGroupEditListener(UserGroupEditListener l) {
        listeners.remove(UserGroupEditListener.class, l);
    }

    private void fireUserGroupEditEvent(UserGroupEditEvent event) {
        for (UserGroupEditListener l : listeners
                .getListeners(UserGroupEditListener.class)) {
            try {
                l.userGroupEdited(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public Map<String, UserGroup> getGroups() {
        return Collections.unmodifiableMap(groups);
    }


    public void storeIndivFilter(UserGroupMember m) {
        saveDataElements(m);
    }


    public void saveGroup(UserGroup g) {
        prepareForModification(g);

        // if this is a new group, assign it a unique group ID
        if (g.getId() == null)
            g.id = generateUniqueID(g.isCustom());

        // add or replace the given group, and save the changes.
        groups.put(g.getId(), g);
        try {
            saveFile(g.isCustom());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        groupWasSaved(g);
    }

    private String generateUniqueID(boolean custom) {
        StringBuilder result = new StringBuilder();

        if (custom)
            result.append(CUSTOM_ID_PREFIX);
        int i = Math.abs((new Random()).nextInt());
        result.append(Integer.toString(i, Character.MAX_RADIX)).append('.');
        result.append(Long.toString(System.currentTimeMillis(),
            Character.MAX_RADIX));

        return result.toString();
    }

    private void groupWasSaved(UserGroup g) {
        // save data elements for this group
        saveDataElements(g);

        // notify listeners about the change
        fireUserGroupEditEvent(new UserGroupEditEvent(this, g, false));
    }

    private void saveDataElements(UserFilter f) {
        saveDataElement(f.getId(), NAME_SUFFIX, StringData.create(f.toString()));

        ListData datasetIDs = new ListData();
        for (String oneID : f.getDatasetIDs())
            datasetIDs.add(oneID);
        if (datasetIDs.test() == false)
            datasetIDs.add("*empty*");
        saveDataElement(f.getId(), DATASET_IDS_SUFFIX, datasetIDs);
    }


    public void deleteGroup(UserGroup g) {
        prepareForModification(g);

        // delete the requested group, and save the changes.
        if (groups.remove(g.getId()) != null) {
            try {
                saveFile(g.isCustom());
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            groupWasDeleted(g);
        }
    }

    private void groupWasDeleted(UserGroup g) {
        // discard data elements for this group
        saveDataElement(g.getId(), NAME_SUFFIX, null);
        saveDataElement(g.getId(), DATASET_IDS_SUFFIX, null);

        // notify listeners about the change
        fireUserGroupEditEvent(new UserGroupEditEvent(this, g, true));
    }

    private void saveDataElement(String id, String suffix, SimpleData value) {
        String dataName = DATA_PREFIX + id + suffix;
        saveDataElement(dataName, value);
    }

    private void saveDataElement(String name, SimpleData value) {
        SimpleData oldValue = dataRepository.getSimpleValue(name);
        if (!simpleDataIsEqual(oldValue, value)) {
            dataRepository.putValue(name, value);
            if (oldValue == null)
                dataRepository.pinElement(name);
        }
    }

    private boolean simpleDataIsEqual(SimpleData a, SimpleData b) {
        if (a == b)
            return true;
        else if (a == null || b == null)
            return false;
        else
            return a.equals(b);
    }


    public boolean saveAll() {
        if (needsSave == null || needsSave.isEmpty())
            return true;

        for (Boolean custom : needsSave) {
            try {
                saveFile(custom);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        return needsSave.isEmpty();
    }

    private void prepareForModification(UserGroup g) {
        // ensure calling code has permission to make changes
        PERMISSION.checkPermission();

        // Groups are only used in the Team Dashboard. If a caller tries to make
        // changes within a personal dashboard, throw an exception.
        if (needsSave == null)
            throw new IllegalStateException("Group modification not allowed");

        // validate that the ID of this group is appropriate
        String id = g.getId();
        if ((id != null && (id.startsWith(CUSTOM_ID_PREFIX) != g.isCustom()))
                || UserGroup.EVERYONE_ID.equals(id))
            throw new IllegalArgumentException("Invalid group ID");

        // don't allow modification of non-custom groups in read-only mode
        if (!g.isCustom() && readOnlyCode != null)
            throw new IllegalStateException("Shared groups are read-only: "
                    + readOnlyCode);

        // custom groups could be altered simultaneously by different processes.
        // to be on the safe side, try reloading the custom groups file before
        // we modify it.
        if (g.isCustom())
            reloadGroups(true);
    }

    private void reloadGroups(boolean custom) {
        File targetFile = custom ? customFile : sharedFile;
        if (!targetFile.isFile())
            return;

        try {
            // read the groups from the file
            Map<String, UserGroup> groupsRead = readFile(targetFile, custom);

            // delete in-memory groups that are no longer in the file
            for (Iterator i = new HashSet(groups.values()).iterator(); i
                    .hasNext();) {
                UserGroup oneGroup = (UserGroup) i.next();
                String oneID = oneGroup.getId();
                if (oneGroup.isCustom() == custom
                        && !groupsRead.containsKey(oneID)) {
                    groups.remove(oneID);
                    groupWasDeleted(oneGroup);
                }
            }

            // replace in-memory groups with those just read
            for (UserGroup oneGroup : groupsRead.values()) {
                UserGroup oldGroup = groups.put(oneGroup.getId(), oneGroup);
                if (!oneGroup.equals(oldGroup))
                    groupWasSaved(oneGroup);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, UserGroup> readFile(File f, boolean custom)
            throws SAXException, IOException {
        // open the file and parse as XML
        InputStream in = new BufferedInputStream(new FileInputStream(f));
        Element xml = XMLUtils.parse(in).getDocumentElement();

        // extract each of the groups from the XML file
        Map<String, UserGroup> result = new HashMap();
        NodeList nl = xml.getElementsByTagName(UserGroup.GROUP_TAG);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            UserGroup oneGroup = new UserGroup(e, custom);
            String id = oneGroup.getId();
            if (custom == id.startsWith(CUSTOM_ID_PREFIX))
                result.put(id, oneGroup);
        }

        return result;
    }

    private void saveFile(boolean custom) throws IOException {
        // make a note that this file needs saving.
        needsSave.add(custom);

        // find the file that should be modified
        File targetFile = custom ? customFile : sharedFile;
        targetFile.getParentFile().mkdirs();
        OutputStream out = new BufferedOutputStream(new RobustFileOutputStream(
                targetFile));

        // start an XML document
        XmlSerializer xml = XMLUtils.getXmlSerializer(true);
        xml.setOutput(out, "UTF-8");
        xml.startDocument("UTF-8", null);
        xml.startTag(null, GROUPS_TAG);

        // write XML for each group
        List<UserGroup> groups = new ArrayList<UserGroup>(this.groups.values());
        Collections.sort(groups);
        for (UserGroup oneGroup : groups) {
            if (oneGroup.isCustom() == custom)
                oneGroup.getAsXml(xml);
        }

        // end the document and close the file
        xml.endTag(null, GROUPS_TAG);
        xml.endDocument();
        out.close();

        // if we saved the file successfully, clear its dirty flag
        needsSave.remove(custom);
    }


    /**
     * At times, there is a need to capture the concept of "everyone." This
     * method returns a group object to serve that purpose. Its meaning is
     * symbolic, because its set of members will be empty; but its name will be
     * a language-appropriate version of "Everyone," and its ID will be
     * {@link UserGroup#EVERYONE_ID}.
     */
    public static UserGroup getEveryonePseudoGroup() {
        // create a group object to hold the information, and return it
        String groupName = resources.getString("Everyone");
        UserGroup result = new UserGroup(groupName, UserGroup.EVERYONE_ID,
                false, Collections.EMPTY_SET);
        return result;
    }


    /**
     * @return the list of all people known to this Team Dashboard. <b>Note:</b>
     *         this list cannot be generated until all project data has been
     *         loaded; so if this method is called shortly after startup in a
     *         large Team Dashboard, it may take a long time to return.
     */
    public Set<UserGroupMember> getAllKnownPeople() {
        // query the database for all known people, and add them to the group
        QueryUtils.waitForAllProjects(databasePlugin);
        List<Object[]> rawData = query.queryHql(EVERYONE_QUERY);
        Set<UserGroupMember> members = new HashSet();
        for (Object[] row : rawData) {
            String userName = (String) row[1];
            String datasetID = (String) row[2];
            UserGroupMember m = new UserGroupMember(userName, datasetID);
            members.add(m);
        }
        return members;
    }

    private static final String EVERYONE_QUERY = //
    "select p.person.key, p.person.encryptedName, p.value.text "
            + "from PersonAttrFact as p " //
            + "where p.versionInfo.current = 1 "
            + "and p.attribute.identifier = 'person.pdash.dataset_id'";

    private static final String CUSTOM_ID_PREFIX = "c.";

    private static final String GROUPS_TAG = "groups";

    private static final String DATA_PREFIX = "User_Group/";

    private static final String NAME_SUFFIX = "//Name";

    private static final String DATASET_IDS_SUFFIX = "//Dataset_IDs";

    private static DashboardPermission PERMISSION = new DashboardPermission(
            "userGroupManager");

}
