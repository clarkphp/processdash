// Copyright (C) 2006-2013 Tuma Solutions, LLC
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

package net.sourceforge.processdash.util.glob;

/*
 * Build note: this class uses many classes that are autogenerated during the
 * ant build process.  If your IDE or compiler is giving you error messages
 * about missing classes, you need to run ant!  See the file "README-build.txt"
 * in the root directory of this project for more information.
 */

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.sourceforge.processdash.util.glob.analysis.DepthFirstAdapter;
import net.sourceforge.processdash.util.glob.node.AAndExpression;
import net.sourceforge.processdash.util.glob.node.AExpression;
import net.sourceforge.processdash.util.glob.node.AGlobMatchTerm;
import net.sourceforge.processdash.util.glob.node.AUnaryNotUnaryExpr;
import net.sourceforge.processdash.util.glob.node.POrClause;
import net.sourceforge.processdash.util.glob.node.PUnaryExpr;

class GlobSearchEvaluator extends DepthFirstAdapter implements
        GlobEngineConstants {

    Set allValues;
    Map taggedValues;
    TaggedDataMapSource deferredData;

    Set result;

    public GlobSearchEvaluator(Map taggedData, TaggedDataMapSource deferredData) {
        this.taggedValues = taggedData;
        this.deferredData = deferredData;

        allValues = new HashSet();
        for (Iterator i = taggedData.values().iterator(); i.hasNext();) {
            Collection c = (Collection) i.next();
            for (Object oneValue : c)
                if (!isGlobEngineInstruction(oneValue))
                    allValues.add(oneValue);
        }

        result = allValues;
    }

    public Set getResult() {
        return result;
    }

    public void caseAGlobMatchTerm(AGlobMatchTerm node) {
        GlobPattern glob = new GlobPattern(node.getMatchTerm().getText());

        Set result = new HashSet();
        for (Iterator i = taggedValues.entrySet().iterator(); i.hasNext();) {
            Map.Entry e = (Map.Entry) i.next();
            String tag = (String) e.getKey();
            if (glob.test(tag))
                result.addAll(expandDeferredValues(tag,
                    (Collection) e.getValue()));
        }

        this.result = result;
    }

    private Collection expandDeferredValues(String tag, Collection values) {
        if (deferredData == null || !values.contains(DEFERRED_DATA_MARKER))
            return values;

        Set newValues = new HashSet();
        for (Object oneValue : values) {
            String token = extractDeferredToken(oneValue);
            if (token != null && token.length() > 0) {
                Map lazyData = deferredData.getTaggedData(token);
                if (lazyData != null) {
                    Collection matchingData = (Collection) lazyData.get(tag);
                    if (matchingData != null)
                        newValues.addAll(matchingData);
                }

            } else if (!isGlobEngineInstruction(oneValue)){
                newValues.add(oneValue);
            }
        }
        return newValues;
    }

    private String extractDeferredToken(Object obj) {
        return extractTextAfter(obj, DEFERRED_TOKEN_PREFIX);
    }

    private boolean isGlobEngineInstruction(Object obj) {
        return extractTextAfter(obj, INSTRUCTION_PREFIX) != null;
    }

    private String extractTextAfter(Object obj, String prefix) {
        if (obj instanceof String) {
            String s = (String) obj;
            if (s.startsWith(prefix))
                return s.substring(prefix.length());
        }
        return null;
    }


    public void caseAUnaryNotUnaryExpr(AUnaryNotUnaryExpr node) {
        super.caseAUnaryNotUnaryExpr(node);

        Set result = new HashSet(allValues);
        result.removeAll(this.result);
        this.result = result;
    }

    public void caseAAndExpression(AAndExpression node) {
        Iterator terms = node.getUnaryExpr().iterator();

        // evaluate the first term in the AND clause.
        ((PUnaryExpr) terms.next()).apply(this);

        // evaluate each of the remaining terms in the AND clause, and
        // perform the intersection of each with the final result.
        while (terms.hasNext()) {
            Set currentResult = this.result;
            ((PUnaryExpr) terms.next()).apply(this);
            this.result.retainAll(currentResult);
        }
    }

    public void caseAExpression(AExpression node) {
        node.getAndExpression().apply(this);

        for (Iterator i = node.getOrClause().iterator(); i.hasNext();) {
            Set currentResult = this.result;
            ((POrClause) i.next()).apply(this);
            this.result.addAll(currentResult);
        }
    }

}
