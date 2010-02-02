/*
 *  $Id$
 *  IzPack
 *  Copyright (C) 2001-2003 Julien Ponge
 *
 *  File :               Pack.java
 *  Description :        Contains informations about a pack.
 *  Author's email :     julien@izforge.com
 *  Author's Website :   http://www.izforge.com
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.izforge.izpack;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.List;

import com.izforge.izpack.installer.ResourceManager;

/**
 *  Represents a Pack.
 *
 * @author     Julien Ponge
 */
public class Pack implements Serializable
{
    /**  The pack name. */
    public String name;

    /**  The pack id. */
    public String id;

    /**  The pack description. */
    public String description;

    /**  The target operation system of this pack */
    public List osConstraints = null;

    /**  True if the pack is required. */
    public boolean required;

    /**  The bumber of bytes contained in the pack. */
    public long nbytes;

    /**  Whether this pack is suggested (preselected for installation). */
    public boolean preselected;

    /**
     *  The constructor.
     *
     * @param  name         The pack name.
     * @param  description  The pack description.
     * @param  targetOs    Description of the Parameter
     * @param  required     Indicates wether the pack is required or not.
     */
    public Pack(String name, String id, String description, List osConstraints, boolean required, boolean preselected)
    {
        this.name = name;
        this.id = id;
        this.description = description;
        this.osConstraints = osConstraints;
        this.required = required;
        this.preselected = preselected;
        nbytes = 0;
    }


    /**
     *  To a String (usefull for JLists).
     *
     * @return    The String representation of the pack.
     */
    public String toString()
    {
        return getDisplayName() + " (" + getDisplayDescription() + ")";
    }


    /** Get a (potentially localized) name of this pack.
     */
    public String getDisplayName() {
        if (displayName == null)
            displayName = getDisplayString("name", name);

        return displayName;
    }
    private transient String displayName = null;


    /** Get a (potentially localized) description for this pack.
     */
    public String getDisplayDescription() {
        if (displayDescription == null)
            displayDescription = getDisplayString("description", description);

        return displayDescription;
    }
    private transient String displayDescription = null;


    private String getDisplayString(String suffix, String defaultVal) {
        if (id == null)
            return defaultVal;
        try {
            String resName = "pack." + id + "." + suffix;
            return ResourceManager.getInstance().getTextResource(resName);
        } catch (Exception e) {
            return defaultVal;
        }
    }


    /**  Used of conversions. */
    private final static double KILOBYTES = 1024.0;

    /**  Used of conversions. */
    private final static double MEGABYTES = 1024.0 * 1024.0;

    /**  Used of conversions. */
    private final static double GIGABYTES = 1024.0 * 1024.0 * 1024.0;

    /**  Used of conversions. */
    private final static DecimalFormat formatter = new DecimalFormat("#,###.##");

    /**
     *  Convert bytes into appropiate mesaurements.
     *
     * @param  bytes  A number of bytes to convert to a String.
     * @return        The String-converted value.
     */
    public static String toByteUnitsString(int bytes)
    {
        if (bytes < KILOBYTES)
            return String.valueOf(bytes) + " bytes";
        else if (bytes < (MEGABYTES))
        {
            double value = bytes / KILOBYTES;
            return formatter.format(value) + " KB";
        }
        else if (bytes < (GIGABYTES))
        {
            double value = bytes / MEGABYTES;
            return formatter.format(value) + " MB";
        }
        else
        {
            double value = bytes / GIGABYTES;
            return formatter.format(value) + " GB";
        }
    }
}
