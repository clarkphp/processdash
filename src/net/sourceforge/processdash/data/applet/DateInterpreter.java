// Copyright (C) 2000-2003 Tuma Solutions, LLC
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


package net.sourceforge.processdash.data.applet;

import java.util.Date;

import net.sourceforge.processdash.data.DateData;
import net.sourceforge.processdash.data.MalformedValueException;
import net.sourceforge.processdash.data.SimpleData;
import net.sourceforge.processdash.data.repository.Repository;


class DateInterpreter extends DataInterpreter {


    public DateInterpreter(Repository r, String name, boolean readOnly) {
        super(r, name, readOnly);
    }


    public void setBoolean(Boolean b) {
        value = (b.booleanValue() ? new DateData() : null);
    }


    public void setString(String s) throws MalformedValueException {
        value = DateData.create(s);
    }


    public SimpleData getNullValue() {
        return NULL_VALUE;
    }

    private static final DateData NULL_VALUE = new DateData(new Date(0), true);
    static { NULL_VALUE.setDefined(false); }

}
