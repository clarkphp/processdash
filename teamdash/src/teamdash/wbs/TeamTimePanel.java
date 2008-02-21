package teamdash.wbs;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.EventHandler;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import teamdash.team.TeamMember;
import teamdash.team.TeamMemberList;
import teamdash.wbs.columns.TeamActualTimeColumn;
import teamdash.wbs.columns.TeamMemberTimeColumn;
import teamdash.wbs.columns.UnassignedTimeColumn;

/** Displays a panel containing dynamic bar charts for each team member. The
 * bars indicate the approximate bottom-up duration of the schedule for each
 * team member.  A separate indicator shows the approximate duration of the
 * balanced team schedule.
 */
public class TeamTimePanel extends JPanel implements TableModelListener {

    /** The list of team members on this project. */
    private TeamMemberList teamList;
    /** The data model containing time data. */
    private DataTableModel dataModel;
    /** The layout object managing this panel */
    private GridBagLayout layout;
    /** A list of the bar charts for each individual (each is a
     * TeamMemberBar object). */
    private List<TeamMemberBar> teamMemberBars;
    /** The column number holding unassigned time data */
    private int unassignedTimeColumn;
    /** The point in time when the first person is starting */
    private long teamStartTime;
    /** The team effective date for actual metrics collected so far */
    private Date teamEffectiveDate;
    /** The point in time represented by the left edge of this panel */
    private long leftTimeBoundary;
    /** The number of milliseconds between the left time boundary and the
     * latest finish date */
    private double maxScheduleLength;
    /** The amount of time in schedules for team members whose end date
     * precedes the team effective date */
    private double historicalTeamMemberCollateralTime;
    /** The number of milliseconds between the left time boundary and the
     * balanced completion date.  If no balanced date can be computed, -1 */
    private long balancedLength = -1;
    /** The indicator for the balanced team duration */
    private JPanel balancedBar;
    /** Should the balanced bar be shown, or hidden */
    private boolean showBalancedBar;
    /** Should the bars show total project data, or just remaining project data */
    private boolean showRemainingWork;
    /** Should the balanced bar include unassigned work? */
    private boolean includeUnassigned;
    /** The position of the balanced bar, in pixels from the left edge of the
     * area where colored bars are drawn */
    private int balancedBarPos;
    /** The font to use when drawing labels on colored bars */
    private Font labelFont;
    /** The color to use for depicting overtasked time in a colored bar */
    private Color overtaskedColor = Color.red;
    /** A timer used to recalc once after receiving multiple TableModelEvents */
    private Timer recalcTimer;

    /** Create a team time panel.
     * @param teamList the list of team members to display.
     * @param dataModel the data model containing time data.
     */
    public TeamTimePanel(TeamMemberList teamList, DataTableModel dataModel) {
        this.teamList = teamList;
        this.dataModel = dataModel;
        this.teamMemberBars = new ArrayList<TeamMemberBar>();
        this.showBalancedBar = true;
        this.showRemainingWork = false;
        this.includeUnassigned = true;
        this.unassignedTimeColumn = dataModel
                .findColumn(UnassignedTimeColumn.COLUMN_ID);

        this.recalcTimer = new Timer(100, EventHandler.create(
            ActionListener.class, this, "recalc"));
        this.recalcTimer.setRepeats(false);

        setLayout(layout = new GridBagLayout());
        rebuildPanelContents();
        recalc();

        dataModel.addTableModelListener(this);
        teamList.addTableModelListener(this);
    }

    public boolean isShowBalancedBar() {
        return showBalancedBar;
    }

    public void setShowBalancedBar(boolean showBalancedBar) {
        this.showBalancedBar = showBalancedBar;
    }

    public boolean isShowRemainingWork() {
        return showRemainingWork;
    }

    public void setShowRemainingWork(boolean showRemaining) {
        if (this.showRemainingWork != showRemaining) {
            this.showRemainingWork = showRemaining;
            rebuildPanelContents();
            recalc();
        }
    }

    public boolean isIncludeUnassigned() {
        return includeUnassigned;
    }

    public void setIncludeUnassigned(boolean includeUnassigned) {
        if (this.includeUnassigned != includeUnassigned) {
            this.includeUnassigned = includeUnassigned;
            recalc();
        }
    }

    private void rebuildPanelContents() {
        removeAll();  // remove all components from this container.
        teamMemberBars.clear();
        labelFont = null;

        // create the indicator for the balanced team duration. It is
        // added to the container first, so it will display on top of
        // other components.
        balancedBar = new JPanel();
        balancedBar.setBorder
            (BorderFactory.createBevelBorder(BevelBorder.RAISED));
        balancedBar.setBackground(Color.darkGray);
        add(balancedBar);
        // give the balanced bar a max/min/pref size of 0,0 so it will
        // not influence panel layout.
        Dimension d = new Dimension(0, 0);
        balancedBar.setMaximumSize(d);
        balancedBar.setMinimumSize(d);
        balancedBar.setPreferredSize(d);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 2; c.gridy = 0;
        layout.setConstraints(balancedBar, c);

        teamEffectiveDate = (Date) dataModel.getWBSModel().getRoot()
            .getAttribute(WBSSynchronizer.EFFECTIVE_DATE_ATTR);
        if (teamEffectiveDate == null)
            teamEffectiveDate = A_LONG_TIME_AGO;
        historicalTeamMemberCollateralTime = 0;

        List teamMembers = teamList.getTeamMembers();
        // create a constraints object for the name labels.
        GridBagConstraints nc = new GridBagConstraints();
        nc.gridx = 0;
        nc.anchor = GridBagConstraints.WEST;
        nc.insets.left = nc.insets.right = 5;
        nc.insets.top = nc.insets.bottom = 0;
        // create a constraints object for the horizontal bars.
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 1;
        bc.fill = GridBagConstraints.BOTH;
        bc.insets.left = bc.insets.right = 5;
        bc.insets.top = bc.insets.bottom = 0;
        bc.weightx = bc.weighty = 1;
        int row = 0;
        for (int i = 0;   i < teamMembers.size();   i++) {
            // for each team member, create a name label and a horizontal
            // progress bar.
            TeamMember m = (TeamMember) teamMembers.get(i);

            // if we're only showing remaining time, and this team member's
            // schedule ends before the effective date, don't show a bar for
            // this individual.
            if (showRemainingWork) {
                Date endDate = m.getSchedule().getEndDate();
                if (endDate != null && endDate.before(teamEffectiveDate)) {
                    historicalTeamMemberCollateralTime +=
                        m.getSchedule().getEffortForDate(teamEffectiveDate);
                    continue;
                }
            }

            TeamMemberBar bar = new TeamMemberBar(m);
            teamMemberBars.add(bar);

            JLabel name = new JLabel(m.getName());
            nc.gridy = row;
            add(name);
            layout.setConstraints(name, nc);

            bc.gridy = row;
            add(bar);
            layout.setConstraints(bar, bc);

            row++;
        }
    }

    public void doLayout() {
        super.doLayout();

        // we override doLayout so we can set the position of the
        // balanced bar.  The call to super.doLayout() will position
        // it somewhere meaningless and allocate it no space.  We
        // resize it to be as high as this panel, and reposition it to
        // properly indicate the calculated team duration.
        if (showBalancedBar
                && maxScheduleLength > 0
                && balancedLength <= maxScheduleLength
                && teamMemberBars.size() > 0) {
            Rectangle r = ((TeamMemberBar) teamMemberBars.get(0)).getBounds();
            balancedBarPos = (int) (r.width * balancedLength / maxScheduleLength);
            int pos = r.x + balancedBarPos;
            balancedBar.setBounds(pos-BBHW, 1, BALANCED_BAR_WIDTH, getHeight());
        } else {
            balancedBarPos = -100;
        }
    }



    public void recalc() {
        recalcStartDate();
        double totalTime = recalcIndividuals() + getUnassignedTime();
        recalcTeam(totalTime);
        revalidate();
        repaintIndividuals();
        repaint();
    }


    /** Recalculate the start dates for the team schedule.
     */
    protected void recalcStartDate() {
        // find out how when the overall team is starting work.
        Date teamStartDate = teamList.getDateForEffort(0);
        teamStartTime = teamStartDate.getTime();

        // select the time value to use for the left edge of the panel
        if (showRemainingWork)
            leftTimeBoundary = Math.max(teamStartTime,
                teamEffectiveDate.getTime());
        else
            leftTimeBoundary = teamStartTime;
    }


    /** Recalculate the horizontal bars for each team member.
     * @return
     */
    protected double recalcIndividuals() {
        double totalTime = historicalTeamMemberCollateralTime;
        long maxLen = 0;
        // recalculate each team member's schedule. Keep track of the longest
        // duration we've seen so far, and the total effective time.
        for (TeamMemberBar tmb : teamMemberBars) {
            tmb.recalc();
            totalTime += tmb.getTotalHours();
            maxLen = Math.max(maxLen, tmb.getFinishTime());
        }
        maxScheduleLength = maxLen;
        return totalTime;
    }

    /** Retrieve the total amount of unassigned time in the WBS, if the user
     * wants it to be included in the calculation; otherwise return 0.
     */
    protected double getUnassignedTime() {
        if (includeUnassigned == false || unassignedTimeColumn == -1)
            return 0;
        NumericDataValue unassignedTime =
            (NumericDataValue) dataModel.getValueAt(0, unassignedTimeColumn);
        if (unassignedTime != null)
            return unassignedTime.value;
        else
            return 0;
    }

    /** Recalculate the duration of a balanced team schedule.
     */
    protected void recalcTeam(double totalHours) {
        // calculate the optimal finish time
        Date balancedDate = teamList.getDateForEffort(totalHours);
        if (balancedDate == null) {
            balancedLength = -1;
        } else {
            balancedLength = balancedDate.getTime() - leftTimeBoundary;
            maxScheduleLength = Math.max(maxScheduleLength, balancedLength);
            balancedBar.setToolTipText("Balanced Team Duration - " +
                dateFormat.format(balancedDate));
        }
    }

    /** Repaint the horizontal bars for each team member.
     */
    protected void repaintIndividuals() {
        // Now, go back and adjust the bar for each individual based upon
        // their schedule duration and the longest duration
        Iterator i = teamMemberBars.iterator();
        while (i.hasNext())
            ((TeamMemberBar) i.next()).update();
    }


    /** Listen for and respond to changes in the data or the team list.
     */
    public void tableChanged(TableModelEvent e) {
        if (e.getSource() == teamList)
            // if the list of team members changed, we need to discard
            // and rebuild the contents of this panel from scratch.
            rebuildPanelContents();

        // whenever data changes, recalculate and redisplay.
        recalcTimer.restart();
    }


    /** This class performs the calculations and the display of a
     * horizontal bar for a single team member.
     */
    private class TeamMemberBar extends JPanel {

        /** The TeamMember we are displaying data for */
        private TeamMember teamMember;

        /** The column in the data model holding time for our team member */
        private int columnNumber;

        /** True if our colored bar has a dark color */
        private boolean barIsDark;

        /** True if we should paint the label with a light color */
        private boolean labelIsLight;

        /** When we're showing remaining time, how many hours should we add to
         * account for the portion of the schedule that has already passed? */
        private double effectivePastHours;

        /** The total number of effective hours in this individual's schedule */
        private double totalHours;

        /** Millis between the team start and the start date for this person */
        private long lagTime;

        /** Millis between the team start and the finish date for this person */
        private long finishTime;

        /** Millis between the team start and the date this person is leaving
         * the project. (-1 if they aren't leaving the project) */
        private long endTime;

        /** The label to display on the bar */
        private String label;

        /** A tooltip to display for the start of the bar */
        private String startTooltip;

        /** The pixel position of the start of the bar */
        private int startPos;


        public TeamMemberBar(TeamMember teamMember) {
            this.teamMember = teamMember;
            this.columnNumber = findTimeColumn();

            effectivePastHours = teamMember.getSchedule().getEffortForDate(
                teamEffectiveDate);

            setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
            // use the color associated with the given team member.
            setForeground(teamMember.getColor());

            int rgb = teamMember.getColor().getRGB();
            int gray = (int) (0.30 * ((rgb >> 16) & 0xff) +
                    0.59 * ((rgb >> 8) & 0xff) +
                    0.11 * (rgb & 0xff));
            barIsDark = (gray < 128);
        }

        /**
         * Recalculate the schedule duration for this team member, and return
         * the number of milliseconds in their schedule (including lag time at
         * the beginning of the schedule)
         */
        public void recalc() {
            Date startDate = teamMember.getStartDate();
            lagTime = startDate.getTime() - leftTimeBoundary;
            if (lagTime < 0) {
                startTooltip = teamMember.getName()
                        + " - Schedule started previously on "
                        + dateFormat.format(startDate);
            } else {
                startTooltip = teamMember.getName() + " - Schedule Start Date "
                        + dateFormat.format(startDate);
            }

            Date endDate = teamMember.getEndDate();
            if (endDate == null)
                endTime = -1;
            else
                endTime = endDate.getTime() - leftTimeBoundary;

            totalHours = (showRemainingWork
                    ? getEffectiveRemainingHours() : getTotalAssignedHours());
            Date finishDate = teamMember.getSchedule().getDateForEffort(totalHours);

            if (finishDate == null) {
                finishTime = -1;
                String hoursString = NumericDataValue.format(totalHours + 0.049);
                setLabel(hoursString + " total hours");
            } else {
                finishTime = finishDate.getTime() - leftTimeBoundary;
                String dateString = dateFormat.format(finishDate);
                if (endTime > 0 && finishTime > endTime)
                    dateString = dateString + " - OVERTASKED";
                setLabel(dateString);
            }
        }

        public double getTotalHours() {
            return totalHours;
        }

        public long getFinishTime() {
            return finishTime;
        }

        private void setLabel(String message) {
            this.label = message;
            setToolTipText(teamMember.getName() + " - " + message);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            if (Math.abs(event.getX() - startPos) < 5)
                return startTooltip;
            else
                return super.getToolTipText(event);
        }

        private double getEffectiveRemainingHours() {
            String attrName = TeamActualTimeColumn.getRemainingTimeAttr(
                teamMember);
            double remainingTime = dataModel.getWBSModel().getRoot()
                    .getNumericAttribute(attrName);
            return remainingTime + effectivePastHours;
        }

        private double getTotalAssignedHours() {
            if (columnNumber == -1) {
                columnNumber = findTimeColumn();
                // if we can't find a column for this team member, return
                // a total time of 0 hours.
                if (columnNumber == -1) return 0;
            }
            // retrieve the total planned time for all tasks assigned to
            // this team member.
            NumericDataValue totalTime =
                (NumericDataValue) dataModel.getValueAt(0, columnNumber);
            if (totalTime != null)
                return totalTime.value;
            else
                return 0;
        }

        /** Look up the time column for this team member. */
        private int findTimeColumn() {
            String columnID = TeamMemberTimeColumn.getColumnID(teamMember);
            return dataModel.findColumn(columnID);
        }

        /** Alter the horizontal position of this bar.
         *
         * It should depict the percentage obtained by dividing this team
         * member's schedule by the longest existing schedule.
         */
        public void update() {
            repaint();
        }

        public void paint(Graphics g) {
            // this will paint the background and the insets.
            super.paint(g);

            if (finishTime > 0 && maxScheduleLength > 0) {
                // now paint the bar.
                double leftPos = Math.max(lagTime, 0) / maxScheduleLength;
                double rightPos = finishTime / maxScheduleLength;

                Rectangle bounds = getBounds();
                Insets insets = getInsets();
                int totalWidth = bounds.width - insets.left - insets.right;
                int barHeight = bounds.height - insets.top - insets.bottom;
                int barLeft = (int) (totalWidth * leftPos) + insets.left;
                int barRight = (int) (totalWidth * rightPos) + insets.left;
                int barWidth = barRight - barLeft;
                g.setColor(getForeground());
                g.fillRect(barLeft, insets.top, barWidth, barHeight);

                if (endTime > 0 && finishTime > endTime) {
                    double endPos = endTime / maxScheduleLength;
                    int overageLeft = (int) (totalWidth * endPos) + insets.left;
                    int overageWidth = barRight - overageLeft;
                    g.setColor(overtaskedColor);
                    g.fillRect(overageLeft, insets.top, overageWidth, barHeight);
                }

                if (label != null && label.length() > 0) {
                    if (labelFont == null)
                        labelFont = createPlainFont(barHeight - 2);
                    int labelWidth = SwingUtilities.computeStringWidth(
                        getFontMetrics(labelFont), label);
                    int labelPos = calcLabelPos(barLeft, barRight, barWidth,
                        labelWidth, totalWidth);
                    g.setFont(labelFont);
                    g.setColor(labelIsLight ? Color.white : Color.black);
                    g.drawString(label, labelPos, barHeight + insets.top - 2);
                }

                this.startPos = barLeft;

                // in "show remaining work" mode, if this team member started
                // before the effective date, draw a jagged left edge for their
                // colored bar to indicate that the full schedule continues
                // to the left
                if (lagTime < 0 && showRemainingWork) {
                    g.setColor(getBackground());
                    int ll = insets.left, tt = insets.top, d = barHeight/4;
                    int[] xx = new int[] { ll, ll + d, ll, ll+d, ll };
                    int[] yy = new int[] { tt, tt+d, tt+d*2, tt+d*3, tt+barHeight };
                    g.fillPolygon(xx, yy, xx.length);
                }
            }
        }

        public Font createPlainFont(float height) {
            return UIManager.getFont("Table.font").deriveFont(Font.BOLD).deriveFont(height);
        }

        private int calcLabelPos(int barLeft, int barRight, int barWidth,
                int labelWidth, int totalWidth) {
            // first preference: right aligned inside the colored bar
            if (labelWidth + 2 * PAD < barWidth) {
                int labelPos = barRight - labelWidth - PAD;
                if (!collidesWithBalancedBar(labelPos, labelWidth)) {
                    labelIsLight = barIsDark;
                    return labelPos;
                }
            }

            // second preference: left aligned to the right of the colored bar
            if (barRight + 2 * PAD + labelWidth < totalWidth) {
                int labelPos = barRight + PAD;
                if (!collidesWithBalancedBar(labelPos, labelWidth)) {
                    labelIsLight = false;
                    return labelPos;
                }
            }

            // third preference: inside colored bar, to the left of balanced bar
            if (barLeft + 2 * PAD + labelWidth + BBHW < balancedBarPos) {
                int labelPos = balancedBarPos - BBHW - PAD - labelWidth;
                if (labelPos + labelWidth + PAD < barRight) {
                    labelIsLight = barIsDark;
                    return labelPos;
                }
            }

            // fourth preference: right aligned to the left of the colored bar
            if (barLeft > 2 * PAD + labelWidth) {
                int labelPos = barLeft - PAD - labelWidth;
                if (!collidesWithBalancedBar(labelPos, labelWidth)) {
                    labelIsLight = false;
                    return labelPos;
                }
            }

            // fifth preference: to the right of the balanced bar
            if (balancedBarPos > 0 && barRight < balancedBarPos + BBHW) {
                int labelPos = balancedBarPos + BBHW + PAD;
                if (labelPos + labelWidth + PAD < totalWidth) {
                    labelIsLight = false;
                    return labelPos;
                }
            }

            // abort: draw at the left of the team member bar.
            if (barLeft < labelWidth / 2)
                labelIsLight = barIsDark;
            else
                labelIsLight = false;
            return PAD;
        }

        private boolean collidesWithBalancedBar(int labelPos, int labelWidth) {
            if (balancedBarPos < 0)
                return false;
            int leftEdge = labelPos - PAD - BBHW;
            int rightEdge = labelPos + labelWidth + PAD + BBHW;
            return (leftEdge < balancedBarPos && balancedBarPos < rightEdge);
        }

    }

    private DateFormat dateFormat = DateFormat.getDateInstance();

    private static final int BALANCED_BAR_WIDTH = 8;
    private static final int BBHW = BALANCED_BAR_WIDTH / 2;
    private static final int PAD = 3;
    private static final Date A_LONG_TIME_AGO = new Date(0);

}