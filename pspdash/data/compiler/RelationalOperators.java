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

package pspdash.data.compiler;

import pspdash.data.SimpleData;
import pspdash.data.DoubleData;
import pspdash.data.NumberData;

class RelationalOperators {

    static final SimpleData TRUE  = new DoubleData(1.0, false);
    static final SimpleData FALSE = new DoubleData(0.0, false);

    private RelationalOperators() {}


    public static final Instruction EQ = new BinaryRelationalOperator("==") {
        protected boolean calc(SimpleData left, SimpleData right) {
            return left.equals(right); } };

    public static final Instruction NEQ = new BinaryRelationalOperator("!=") {
        protected boolean calc(SimpleData left, SimpleData right) {
            return ! left.equals(right); } };

    public static final Instruction LT = new BinaryRelationalOperator("<") {
        protected boolean calc(SimpleData left, SimpleData right) {
            return left.lessThan(right); } };

    public static final Instruction LTEQ = new BinaryRelationalOperator("<=") {
        protected boolean calc(SimpleData left, SimpleData right) {
            return left.lessThan(right) || left.equals(right); } };

    public static final Instruction GT = new BinaryRelationalOperator(">") {
        protected boolean calc(SimpleData left, SimpleData right) {
            return left.greaterThan(right); } };

    public static final Instruction GTEQ = new BinaryRelationalOperator(">=") {
        protected boolean calc(SimpleData left, SimpleData right) {
            return left.greaterThan(right) || left.equals(right); } };
}


class BinaryRelationalOperator extends BinaryOperator {

    public BinaryRelationalOperator(String op) { super(op); }

    protected SimpleData operate(SimpleData left, SimpleData right) {
        if (left == null || right == null) return RelationalOperators.FALSE;
        return (calc(left, right) ? RelationalOperators.TRUE
                                  : RelationalOperators.FALSE);
    }

    protected boolean calc(SimpleData left, SimpleData right) {
        return false;
    }
}
