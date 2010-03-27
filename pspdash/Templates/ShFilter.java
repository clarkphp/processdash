// PSP Dashboard - Data Automation Tool for PSP-like processes
// Copyright (C) 1999  United States Air Force
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
// OO-ALC/TISHD
// Attn: PSP Dashboard Group
// 6137 Wardleigh Road
// Hill AFB, UT 84056-5843
//
// E-Mail POC:  ken.raisor@hill.af.mil


import pspdash.AbstractLanguageFilter;

import java.io.PrintWriter;

public class ShFilter extends AbstractLanguageFilter {

    private static final String[] COMMENT_STARTERS = { "#" };
    protected String[] getCommentStarters() { return COMMENT_STARTERS; }

    private static final String[] COMMENT_ENDERS   = { "\n" };
    protected String[] getCommentEnders()   { return COMMENT_ENDERS; }

    private static final String[] FILENAME_ENDINGS = {
        ".pl", ".sh", ".bash" };
    protected String[] getDefaultFilenameEndings() {
        return FILENAME_ENDINGS;
    }

    protected int doubleCheckFileContents(String contents, int match) {
        // if C programmers begin their program with a compiler
        // directive instead of a comment, that compiler directive
        // could be misinterpreted as a sh-style comment.  Detect that
        // scenario and return LANGUAGE_MISMATCH.
        if (contents.startsWith("#define") ||
            contents.startsWith("#include") ||
            contents.startsWith("#if"))
            return LANGUAGE_MISMATCH;
        else
            return match;
    }
}