// Process Dashboard - Data Automation Tool for high-maturity processes
// Copyright (C) 1998-2007 Software Process Dashboard Initiative
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
// Process Dashboard Group
// c/o Ken Raisor
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC:  processdash-devel@lists.sourceforge.net


package net.sourceforge.processdash.log.defects;

import java.util.*;
import java.text.*;

import net.sourceforge.processdash.util.FormatUtil;
import net.sourceforge.processdash.util.XMLUtils;

public class Defect {

    public static final String UNSPECIFIED = "Unspecified";

    public Date date;
    public String number, defect_type, phase_injected, phase_removed,
                  fix_time, fix_defect, description;

    public Defect() {}

    public Defect(String s) throws ParseException {
        if (s == null) throw new ParseException("Null pointer passed in", 0);
        StringTokenizer tok = new StringTokenizer(s.replace('','\n'), "\t");
        try {
            number = tok.nextToken();
            defect_type = tok.nextToken();
            phase_injected = tok.nextToken();
            phase_removed = tok.nextToken();
            fix_time = tok.nextToken();
            fix_defect = tok.nextToken();
            description = tok.nextToken();
            date = FormatUtil.parseDate(tok.nextToken());
        } catch (NoSuchElementException e) {
            System.out.println("NoSuchElementException: " + e);
            throw new ParseException("Poor defect formatting", 0);
        }
    }

    private String token(String s, boolean multiline) {
        if      (s == null) return " ";
        else if (s.length() == 0) return " ";
        else if (multiline) return s.replace('\t', ' ').replace('\n','');
        else return s.replace('\t', ' ').replace('\n',' ');
    }

    public String toString() {
        String tab = "\t";
        String dateStr = "";
        if (date != null) dateStr = XMLUtils.saveDate(date);
        return (token(number, false) + tab +
                token(defect_type, false) + tab +
                token(phase_injected, false) + tab +
                token(phase_removed, false) + tab +
                token(fix_time, false) + tab +
                token(fix_defect, false) + tab +
                token(description, true) + tab +
                token(dateStr, false) + tab);
    }

}
