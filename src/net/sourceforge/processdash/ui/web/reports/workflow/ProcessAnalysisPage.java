// Copyright (C) 2016 Tuma Solutions, LLC
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

package net.sourceforge.processdash.ui.web.reports.workflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sourceforge.processdash.data.DoubleData;
import net.sourceforge.processdash.data.util.ResultSet;
import net.sourceforge.processdash.tool.db.WorkflowHistDataHelper;
import net.sourceforge.processdash.tool.db.WorkflowHistDataHelper.Enactment;
import net.sourceforge.processdash.tool.db.WorkflowHistDataHelper.PhaseType;
import net.sourceforge.processdash.util.DataPair;

public class ProcessAnalysisPage extends AnalysisPage {

    public ProcessAnalysisPage() {
        super("Process", "Process.Title");
    }


    @Override
    protected void writeHtmlContent(HttpServletRequest req,
            HttpServletResponse resp, ChartData chartData)
            throws ServletException, IOException {
        boolean showProductivity = chartData.isLegitSize();

        if (showProductivity)
            writeChart(req, resp, chartData, "productivity");
        writeChart(req, resp, chartData, "overheadTime");
        writeChart(req, resp, chartData, "overheadPhases");
        writeChart(req, resp, chartData, "constrTime");
        writeChart(req, resp, chartData, "constrPhases");
        writeChart(req, resp, chartData, "appraisalTime");
        writeChart(req, resp, chartData, "appraisalPhases");
        writeChart(req, resp, chartData, "failureTime");
        writeChart(req, resp, chartData, "failurePhases");
        writeChart(req, resp, chartData, "typeTime");
        writeChart(req, resp, chartData, "typeToDate");
        writeChart(req, resp, chartData, "phaseTime");
        writeChart(req, resp, chartData, "phaseToDate");
        if (showProductivity) {
            writeChart(req, resp, chartData, "prodVsYield");
            writeChart(req, resp, chartData, "prodVsAfr");
            writeChart(req, resp, chartData, "prodVsFailure");
        }
    }


    @Chart(id = "productivity", type = "line", //
    titleKey = "Process.Productivity_Title")
    public ResultSet getProductivity(ChartData chartData) {
        ResultSet data = chartData.getEnactmentResultSet(1);
        writeProductivity(chartData, data, 1);
        return data;
    }


    @Chart(id = "overheadTime", type = "line", //
    titleKey = "Plan.Percent_Overhead_Time")
    public ResultSet getOverheadTime(ChartData chartData) {
        return getTypeTimePctSet(chartData, PhaseType.Overhead);
    }


    @Chart(id = "overheadPhases", type = "line", //
    titleKey = "Plan.Overhead_Phase_Time", //
    format = "units=${Percent_Time}\nnoSkipLegend=t")
    public ResultSet getOverheadTimeByPhase(ChartData chartData) {
        return getPhaseTimePctSet(chartData, PhaseType.Overhead);
    }


    @Chart(id = "constrTime", type = "line", //
    titleKey = "Plan.Percent_Construction_Time")
    public ResultSet getConstructionTime(ChartData chartData) {
        return getTypeTimePctSet(chartData, PhaseType.Construction);
    }


    @Chart(id = "constrPhases", type = "line", //
    titleKey = "Plan.Construction_Phase_Time", //
    format = "units=${Percent_Time}\nnoSkipLegend=t")
    public ResultSet getConstructionTimeByPhase(ChartData chartData) {
        return getPhaseTimePctSet(chartData, PhaseType.Construction);
    }


    @Chart(id = "appraisalTime", type = "line", //
    titleKey = "Plan.Percent_Appraisal_Time")
    public ResultSet getAppraisalTime(ChartData chartData) {
        return getTypeTimePctSet(chartData, PhaseType.Appraisal);
    }


    @Chart(id = "appraisalPhases", type = "line", //
    titleKey = "Plan.Appraisal_Phase_Time", //
    format = "units=${Percent_Time}\nnoSkipLegend=t")
    public ResultSet getAppraisalTimeByPhase(ChartData chartData) {
        return getPhaseTimePctSet(chartData, PhaseType.Appraisal);
    }


    @Chart(id = "failureTime", type = "line", //
    titleKey = "Plan.Percent_Failure_Time")
    public ResultSet getFailureTime(ChartData chartData) {
        return getTypeTimePctSet(chartData, PhaseType.Failure);
    }


    @Chart(id = "failurePhases", type = "line", //
    titleKey = "Plan.Failure_Phase_Time", //
    format = "units=${Percent_Time}\nnoSkipLegend=t")
    public ResultSet getFailureTimeByPhase(ChartData chartData) {
        return getPhaseTimePctSet(chartData, PhaseType.Failure);
    }


    @Chart(id = "typeTime", type = "area", //
    titleKey = "Process.Time_By_Type_Title", //
    format = "stacked=pct\n" + TYPE_COLORS)
    public ResultSet getTimeByPhaseType(ChartData chartData) {
        ResultSet data = chartData.getEnactmentResultSet(4);
        for (PhaseType type : PhaseType.values()) {
            int col = type.ordinal() + 1;
            data.setColName(col, getRes("Process.Type_" + type));
            writePhaseTimePct(data, col, type);
        }
        return data;
    }


    @Chart(id = "typeToDate", type = "pie", //
    titleKey = "Process.Time_By_Type_To_Date_Title", format = TYPE_COLORS)
    public ResultSet getTimeByPhaseTypeToDate(ChartData chartData) {
        ResultSet data = new ResultSet(4, 2);
        data.setColName(0, getRes("Process.Type_Header"));
        data.setColName(1, getRes("Hours"));
        data.setColName(2, getRes("Percent_Units"));
        data.setFormat(2, "100%");

        double totalTime = chartData.histData.getTime(null, null, true);
        for (PhaseType type : PhaseType.values()) {
            int row = type.ordinal() + 1;
            double time = chartData.histData.getTime(null, type, true);
            data.setRowName(row, getRes("Process.Type_" + type));
            data.setData(row, 1, num(time / 60));
            data.setData(row, 2, num(time / totalTime));
        }

        return data;
    }

    private static final String TYPE_COLORS = "c1=bf9f75\nc2=4444ff\nc3=00c000\nc4=ff0000";


    @Chart(id = "phaseTime", type = "area", //
    titleKey = "Plan.Phase_Time_Title", smallFmt = "hideLegend=t", //
    format = "colorScheme=consistent\nconsistentSkip=2\n"
            + "stacked=pct\nheaderComment=(${Hours})")
    public ResultSet getTimeByPhase(ChartData chartData) {
        List<String> phases = chartData.histData.getPhasesOfType();
        ResultSet data = chartData.getEnactmentResultSet(phases.size());
        writePhaseTime(data, false, 1, phases.toArray());
        return data;
    }


    @Chart(id = "phaseToDate", type = "pie", //
    titleKey = "Plan.Time_In_Phase_Title", smallFmt = "hideLegend=t", //
    format = "colorScheme=consistent\nconsistentSkip=2")
    public ResultSet getTimeInPhaseToDate(ChartData chartData) {
        Map<String, DataPair> time = chartData.histData.getTotalTimeInPhase();
        double totalTime = time.remove(WorkflowHistDataHelper.TOTAL_PHASE_KEY).actual;
        List<String> phases = new ArrayList(time.keySet());
        ResultSet data = new ResultSet(phases.size(), 2);
        data.setColName(0, getRes("Phase"));
        data.setColName(1, getRes("Hours"));
        data.setColName(2, getRes("Percent_Units"));
        data.setFormat(2, "100%");

        for (int row = phases.size(); row > 0; row--) {
            String onePhase = phases.get(row - 1);
            data.setRowName(row, onePhase);
            data.setData(row, 1, num(time.get(onePhase).actual / 60));
            data.setData(row, 2, num(time.get(onePhase).actual / totalTime));
        }
        return data;
    }


    @Chart(id = "prodVsYield", type = "xy", //
    titleKey = "Process.Productivity_Vs_Yield_Title")
    public ResultSet getProductivityVsYield(ChartData chartData) {
        ResultSet data = chartData.getEnactmentResultSet(2);
        QualityAnalysisPage.writeYield(data, 1);
        writeProductivity(chartData, data, 2);
        return data;
    }


    @Chart(id = "prodVsAfr", type = "xy", //
    titleKey = "Process.Productivity_Vs_AFR_Title")
    public ResultSet getProductivityVsAFR(ChartData chartData) {
        ResultSet data = chartData.getEnactmentResultSet(2);
        QualityAnalysisPage.writeAFR(data, 1);
        writeProductivity(chartData, data, 2);
        return data;
    }


    @Chart(id = "prodVsFailure", type = "xy", //
    titleKey = "Process.Productivity_Vs_Failure_Title")
    public ResultSet getProductivityVsFailure(ChartData chartData) {
        ResultSet data = chartData.getEnactmentResultSet(2);
        data.setColName(1, getRes("Plan.Percent_Failure_Time"));
        writePhaseTimePct(data, 1, PhaseType.Failure);
        writeProductivity(chartData, data, 2);
        return data;
    }



    private ResultSet getTypeTimePctSet(ChartData chartData, PhaseType type) {
        ResultSet data = chartData.getEnactmentResultSet("Percent_Units");
        writePhaseTimePct(data, 1, type);
        return data;
    }

    public static ResultSet getPhaseTimePctSet(ChartData chartData,
            PhaseType type) {
        List<String> phases = chartData.getPhases(type);
        if (phases.isEmpty())
            return null;
        ResultSet data = chartData.getEnactmentResultSet(phases.size());
        writePhaseTimePct(data, 1, phases.toArray());
        return data;
    }

    public static void writePhaseTimePct(ResultSet resultSet, int firstCol,
            Object... phases) {
        writePhaseTime(resultSet, true, firstCol, phases);
    }

    public static void writePhaseTime(ResultSet resultSet, boolean pct,
            int firstCol, Object... phases) {

        for (int i = phases.length; i-- > 0;) {
            int col = firstCol + i;
            Object onePhase = phases[i];
            if (onePhase instanceof String)
                resultSet.setColName(col, (String) onePhase);
            if (pct)
                resultSet.setFormat(col, "100%");

            for (int row = resultSet.numRows(); row > 0; row--) {
                Enactment e = (Enactment) resultSet.getRowObj(row);
                double phaseTime = e.actualTime(onePhase);
                double finalTime = phaseTime / (pct ? e.actualTime() : 60);
                resultSet.setData(row, col, new DoubleData(finalTime));
            }
        }
    }

    private void writeProductivity(ChartData chartData, ResultSet data, int col) {
        data.setColName(col, resources.format("Process.Productivity_Label_FMT",
            chartData.primarySizeUnits));
        for (int row = data.numRows(); row > 0; row--) {
            Enactment e = (Enactment) data.getRowObj(row);
            double size = e.actualSize(chartData.primarySizeUnits);
            double time = e.actualTime() / 60;
            data.setData(row, col, num(size / time));
        }
    }

}
