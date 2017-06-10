// Copyright (C) 2017 Tuma Solutions, LLC
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

package net.sourceforge.processdash.i18n;

import java.net.URL;

import net.sourceforge.processdash.util.StringUtils;


/**
 * Finds template resources that appear in the packaged TeamTools.jar file
 */
public class WBSRuntimeTemplateClassLoader
        extends AbstractMergingTemplateClassLoader {

    @Override
    protected URL[] lookupUrlsForResource(String resourceName) {
        String templateName = mapToTemplates(resourceName);

        // look for the resource within the TeamTools.jar file. Files copied
        // from the dashboard are in a "resources/dash" subdirectory.
        String localName = templateName;
        if (!localName.startsWith("Templates/resources/WBSEditor"))
            localName = StringUtils.findAndReplace(localName, "/resources/",
                "/resources/dash/");
        URL localResult = WBSRuntimeTemplateClassLoader.class.getClassLoader()
                .getResource(localName);

        return new URL[] { localResult };
    }

}
