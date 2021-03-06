// Copyright (C) 2015 Tuma Solutions, LLC
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

package teamdash.hist;

import java.util.Date;

import net.sourceforge.processdash.util.HTMLUtils;

import teamdash.wbs.WBSNode;

public abstract class ProjectWbsChange extends ProjectChange {

    private WBSNode node;

    protected ProjectWbsChange(WBSNode node, String author, Date timestamp) {
        super(author, timestamp);
        this.node = node;
    }

    public WBSNode getNode() {
        return node;
    }

    protected static String fmt(WBSNode node) {
        return node.getFullName().substring(1);
    }

    protected static String getNameHtml(WBSNode node) {
        String name = node.getName();
        if (name.trim().length() == 0)
            return "<i>(empty)</i>";
        else
            return HTMLUtils.escapeEntities(name);
    }

}
