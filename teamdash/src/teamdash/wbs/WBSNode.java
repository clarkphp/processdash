
package teamdash.wbs;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import teamdash.XMLUtils;

/** This class represents a node in the work breakdown structure hierarchy.
 */
public class WBSNode implements Cloneable {

    /** The WBSMode to which this node belongs. */
    private WBSModel wbsModel;
    /** The name of this node */
    private String name;
    /** A number uniquely identifying this node */
    private int uniqueID;
    /** The type of this node */
    private String type;
    /** The indentation depth of this node */
    private int indentLevel;
    /** True if this node is expanded, false if it is collapsed */
    private boolean expanded;
    /** True if this node is read only */
    private boolean readOnly;
    /** A collection of attributes containing the data for this node */
    private Map attributes = new HashMap();



    /** Create a new WBSNode with given characteristics. */
    public WBSNode(WBSModel model, String name, String type,
                   int level, boolean expanded) {
        this.wbsModel = model;
        setUniqueID(-1); // not very unique - but let WBSModel fix it.
        setName(name);
        setType(type);
        setIndentLevel(level);
        setExpanded(expanded);
        setReadOnly(false);
    }



    /** Create a new WBSNode with information from an XML Element. */
    public WBSNode(WBSModel model, Element e) {
        this.wbsModel = model;
        setName(e.getAttribute(NAME_ATTR));
        setUniqueID(XMLUtils.getXMLInt(e, ID_ATTR));
        setType(e.getAttribute(TYPE_ATTR));
        setIndentLevel(XMLUtils.getXMLInt(e, INDENT_ATTR));
        setExpanded(XMLUtils.hasValue(e.getAttribute(EXPAND_ATTR)));
        setReadOnly(XMLUtils.hasValue(e.getAttribute(READ_ONLY_ATTR)));

        NodeList nodeAttributes = e.getElementsByTagName(ATTR_ELEM_NAME);
        int len = nodeAttributes.getLength();
        for (int i = 0;   i < len;   i++)
            setXMLAttribute((Element) nodeAttributes.item(i));
    }

    // Getter/setter methods

    /** Returns the WBSModel that this node belongs to.
     * @return Returns the WBSModel that this node belongs to.  */
    public WBSModel getWbsModel() {
        return wbsModel;
    }

    /** Get the name of this node.
     * @return The name of this node.  */
    public String getName() { return name; }

    /** Set the name of this node.
     * @param newName the new name for this node.  */
    public void setName(String newName) { this.name = newName; }

    /** Get the unique ID of this node.
     * @return the unique ID of this node.  */
    public int getUniqueID() {
        return uniqueID;
    }

    /** Set the unique ID of this node.
     * @param uniqueID the new ID for this node. */
    public void setUniqueID(int uniqueID) {
        this.uniqueID = uniqueID;
    }

    /** Get the type of this node.
     * @return the type of this node.  */
    public String getType() { return type; }

    /** Set the type of this node
     * @param newType the new type for this node.  */
    public void setType(String newType) { this.type = newType; }


    /** Get the indentation level of this node.
     * @return the indentation level of this node.  */
    public int getIndentLevel() { return indentLevel; }

    /** Set the indentation level of this node.
     * @param newLevel the new indentation level for this node.  */
    public void setIndentLevel(int newLevel) { this.indentLevel = newLevel; }


    /** Returns true if this node is currently expanded.
     * @return true if this node is expanded.  */
    public boolean isExpanded() { return expanded; }

    /** Expand or collapse this node.
     * @param expanded <code>true</code> to expand this node,
     *    <code>false</code> to collapse this node.  */
    public void setExpanded(boolean expanded) { this.expanded = expanded; }


    /** Returns true if this node is read only.
     * @return true if this node is read only.  */
    public boolean isReadOnly() {  return readOnly; }

    /** Set the read only status of this node.
     * @param readOnly the new read only status for this node.  */
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }



    // Methods to get/set data attributes of the node


    /** Get an attribute of type <code>Object</code> */
    public Object getAttribute(String attrName) {
        return attributes.get(attrName);
    }
    /** Set an attribute of type <code>Object</code> */
    public void setAttribute(String attrName, Object value) {
        attributes.put(attrName, value);
    }
    /** Get a list of the attributes on this node */
    public Set listAttributeNames() {
        return Collections.unmodifiableSet(attributes.keySet());
    }


    /** Get a numeric attribute.
     * @return <code>Double.NaN</code> if the named attribute is not set,
     *   or is not a numeric attribute. */
    public double getNumericAttribute(String attrName) {
        Object value = getAttribute(attrName);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();

        } else if (value instanceof String) {
            try {
                double doubleValue = Double.parseDouble((String) value);
                setAttribute(attrName, doubleValue);
                return doubleValue;
            } catch (NumberFormatException nfe) {
            }
        }

        return Double.NaN;
    }
    /** Set a numeric attribute */
    public void setNumericAttribute(String attrName, double value) {
        setAttribute(attrName, new Double(value));
    }



    // Methods for conversion to/from XML


    /** Extract an attribute name/value pair from the given XML Element,
     * and store it in the attribute map.
     *
     * Note: this stores values in the attribute map as <code>String</code>s,
     * so all getXXXAttribute methods must be capable of automatically
     * interpreting <code>String</code> values. */
    protected void setXMLAttribute(Element attrElement) {
        String name = attrElement.getAttribute(NAME_ATTR);
        String value = attrElement.getAttribute(VALUE_ATTR);
        setAttribute(name, value);
    }


    /** Write an XML representation of this node to the given
     * <code>Writer</code> object. */
    public void getAsXML(Writer out) throws IOException {
        getAsXML(out, false);
    }
    /** Write an XML representation of this node to the given
     * <code>Writer</code> object.
     * @param out the Writer to write the XML to
     * @param full <code>true</code> to dump all attributes, including
     * transient attributes;  <code>false</code> to dump only "authoritative"
     * attributes. */
    public void getAsXML(Writer out, boolean full) throws IOException {
        // write the opening wbsNode tag.
        String indentation = SPACES.substring
            (0, Math.min(2 * (indentLevel + 1), SPACES.length()));
        out.write(indentation);
        out.write("<"+ELEMENT_NAME+" "+NAME_ATTR+"='");
        out.write(XMLUtils.escapeAttribute(getName()));
        out.write("' "+ID_ATTR+"='");
        out.write(Integer.toString(uniqueID));
        if (getType() != null) {
            out.write("' "+TYPE_ATTR+"='");
            out.write(XMLUtils.escapeAttribute(getType()));
        }
        out.write("' "+INDENT_ATTR+"='");
        out.write(Integer.toString(getIndentLevel()));
        if (isExpanded()) out.write("' "+EXPAND_ATTR+"='true");
        if (isReadOnly()) out.write("' "+READ_ONLY_ATTR+"='true");
        out.write("'");         // don't close tag yet

        // write out a tag for each attribute.
        Iterator i = attributes.entrySet().iterator();
        Map.Entry e;
        Object v;
        String name, value;
        boolean wroteAttribute = false;
        while (i.hasNext()) {
            e = (Map.Entry) i.next();
            name = (String) e.getKey();
            // attributes with an underscore in their name are
            // calculated values that need not be saved unless the
            // "full" parameter is true.
            if (!full && name.indexOf('_') != -1) continue;

            v = e.getValue();
            if (v == null) continue;
            value = v.toString();
            if (value == null || value.length() == 0) continue;

            out.write(">\n");   // close previous tag
            out.write(indentation);
            out.write("  <"+ATTR_ELEM_NAME+" "+NAME_ATTR+"='");
            out.write(XMLUtils.escapeAttribute(name));
            out.write("' "+VALUE_ATTR+"='");
            out.write(XMLUtils.escapeAttribute(value));
            out.write("'/");
            wroteAttribute = true;
        }


        if (!wroteAttribute)
            // if this node had no attributes, we can get away with simply
            // closing the original <wbsNode> tag.
            out.write("/>\n");
        else {
            out.write(">\n");        // close final <attr> tag
            out.write(indentation);  // write the closing wbsNode tag.
            out.write("</"+ELEMENT_NAME+">\n");
        }
    }



    /** Make a copy of this WBSNode. */
    protected Object clone() {
        try {
            WBSNode result = (WBSNode) super.clone();

            // clone the attributes Map
            result.attributes = (Map) ((HashMap) result.attributes).clone();
            // remove "transient" attributes from the copied Map.  These
            // attributes will be recalculated as necessary.
            Iterator i = result.attributes.keySet().iterator();
            while (i.hasNext()) {
                String attrName = (String) i.next();
                if (attrName.indexOf('_') != -1 && attrName.indexOf('@') == -1)
                    i.remove();
            }
            return result;
        } catch (CloneNotSupportedException cnse) {
            return null;        // can't happen?
        }
    }



    /** Make a deep copy of a list of WBSNodes */
    public static List cloneNodeList(List nodesToCopy) {
        List result = new ArrayList();
        Iterator i = nodesToCopy.iterator();
        while (i.hasNext())
            result.add(((WBSNode) i.next()).clone());
        return result;
    }


    // constants used in creating/parsing XML
    public static final String ELEMENT_NAME = "wbsNode";
    private static final String NAME_ATTR = "name";
    private static final String ID_ATTR = "id";
    private static final String TYPE_ATTR = "type";
    private static final String READ_ONLY_ATTR = "readOnly";
    private static final String INDENT_ATTR = "indentLevel";
    private static final String EXPAND_ATTR = "expanded";
    private static final String ATTR_ELEM_NAME = "attr";
    private static final String VALUE_ATTR = "value";
    private static final String SPACES =
        "                                                            ";

    public static final String UNKNOWN_TYPE = "Unknown Task";
}