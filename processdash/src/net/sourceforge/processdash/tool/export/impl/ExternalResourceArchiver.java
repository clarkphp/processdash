// Copyright (C) 2007 Tuma Solutions, LLC
// Process Dashboard - Data Automation Tool for high-maturity processes
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// The author(s) may be contacted at:
// Process Dashboard Group
// c/o Ken Raisor
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC: processdash-devel@lists.sourceforge.net

package net.sourceforge.processdash.tool.export.impl;

import java.io.IOException;
import java.util.zip.ZipOutputStream;

import net.sourceforge.processdash.tool.export.mgr.ImportInstructionDispatcher;

public interface ExternalResourceArchiver extends ImportInstructionDispatcher {

    /**
     * Archive external resources by adding them to a ZIP file.
     *
     * Before this method is called, all ImportInstructions will be dispatched
     * to this object, so it can make a note of the external data that needs
     * archiving.
     * 
     * @param out the ZIP file to write data to. This method will NOT close this
     *        stream (the caller created/opened the stream and must be
     *        responsible for closing it).
     */
    public void export(ZipOutputStream out) throws IOException;

}
