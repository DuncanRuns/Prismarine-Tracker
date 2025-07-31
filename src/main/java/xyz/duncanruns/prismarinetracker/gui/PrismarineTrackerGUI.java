package xyz.duncanruns.prismarinetracker.gui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.util.ExceptionUtil;
import xyz.duncanruns.jingle.util.FileUtil;
import xyz.duncanruns.prismarinetracker.CompletedRun;
import xyz.duncanruns.prismarinetracker.PlaySession;
import xyz.duncanruns.prismarinetracker.PrismarineTracker;
import xyz.duncanruns.prismarinetracker.util.FormattingUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PrismarineTrackerGUI extends JPanel {
    private static final Gson GSON = new Gson();
    private boolean closed = false;
    private String displayType = "sessions";
    private long displayed = 0;

    private final List<Long> allSessions = new ArrayList<>();
    private final List<Long> allRuns = new ArrayList<>();

    private JTextArea statsArea;
    private JPanel mainPanel;
    private JLabel nameLabel;
    private JButton previousButton;
    private JButton nextButton;
    private JButton showTypeButton;

    public PrismarineTrackerGUI() {
        this.add(mainPanel);
        nextButton.addActionListener(a -> onNextButtonPress());
        previousButton.addActionListener(a -> onPreviousButtonPress());
        showTypeButton.addActionListener(a -> onShowTypeButtonPress());
        refresh();
    }

    private static String constructInformation(CompletedRun run) {
        StringBuilder builder = new StringBuilder();

        CompletedRun.FormattedTimes formattedTimes = run.formatTimes();

        builder.append("Monument Mine: ").append(formattedTimes.mineMonument);
        builder.append("\n\nVillage Enter: ").append(formattedTimes.villageEnter);
        builder.append("\nFirst Trade: ").append(formattedTimes.firstTrade);
        builder.append("\n\nNether Enter: ").append(formattedTimes.netherEnter);
        builder.append("\nFortress Enter: ").append(formattedTimes.fortressEnter);
        builder.append("\nNether Exit: ").append(formattedTimes.netherExit);
        builder.append("\n\nStronghold Enter: ").append(formattedTimes.strongholdEnter);
        builder.append("\nEnd Enter: ").append(formattedTimes.endEnter);
        builder.append("\n\nFinal RTA: ").append(formattedTimes.completionRTA);
        builder.append("\nFinal IGT: ").append(formattedTimes.completionIGT);
        builder.append("\nFinal Retime: ").append(formattedTimes.completionRetime);
        return builder.toString();
    }

    private static String constructInformation(PlaySession session) {
        StringBuilder builder = new StringBuilder();

        PlaySession.CalculatedStats cs = session.toCalculatedStats();

        builder.append("Time Played: ").append(cs.timePlayedFormatted);
        builder.append(" (Session Length: ").append(cs.sessionLengthFormatted).append(")");

        builder.append("\nBreaks Taken: ").append(cs.breaks);
        if (cs.breaks != 0) {
            builder.append(" (Average Break Time: ").append(cs.averageBreakFormatted).append(")");
        }

        builder.append("\nResets: ").append(cs.resets);
        builder.append("\nWorlds Entered: ").append(cs.worldsEntered);

        if (cs.runsWithMonument != 0) {
            builder.append("\n\nMonuments Mined: ").append(cs.runsWithMonument);
            if (cs.averageMonumentMillis != 0) {
                builder.append(" (Average Time: ").append(cs.averageMonumentFormatted).append(")");
            }
            builder.append("\nMonuments per hour: ").append(cs.monumentsPerHour);
        }

        if (cs.runsWithVillage != 0) {
            builder.append("\n\nVillages entered: ").append(cs.runsWithVillage);
            if (cs.averageVillageMillis != 0) {
                builder.append(" (Average Time: ").append(cs.averageVillageFormatted).append(")");
            }
            builder.append("\nVillages per hour: ").append(cs.villagesPerHour);
            if (cs.runsWithTrading != 0) {
                builder.append("\nVillages with Trading: ").append(cs.runsWithTrading).append(" (Runs with 10 pearls: ").append(cs.runsWith10Pearls).append(")");
            }
        }

        if (cs.runsWithNether > 0) {
            builder.append("\n\nNethers entered: ").append(cs.runsWithNether);
            if (cs.averageFortressMillis != 0) {
                builder.append(" (Average Time: ").append(cs.averageNetherFormatted).append(")");
            }
        }
        if (cs.runsWithFortress > 0) {
            builder.append("\nFortresses entered: ").append(cs.runsWithFortress);
            if (cs.averageFortressMillis != 0) {
                builder.append(" (Average Time: ").append(cs.averageFortressFormatted).append(")");
            }
        }
        if (cs.runsWithNetherExit > 0) {
            builder.append("\nNethers exited: ").append(cs.runsWithNetherExit);
            if (cs.averageNetherExitMillis != 0) {
                builder.append(" (Average Time: ").append(cs.averageNetherExitFormatted).append(")");
            }
        }

        if (cs.runsWithStronghold > 0) {
            builder.append("\n\nStrongholds entered: ").append(cs.runsWithStronghold);
            if (cs.averageStrongholdMillis != 0) {
                builder.append(" (Average Time: ").append(cs.averageStrongholdFormatted).append(")");
            }
        }
        if (cs.runsWithEnd > 0) {
            builder.append("\nEnds entered: ").append(cs.runsWithEnd);
            if (cs.averageEndMillis != 0) {
                builder.append(" (Average Time: ").append(cs.averageEndFormatted).append(")");
            }
        }


        if (cs.runsWithFinish > 0) {
            builder.append("\n\nRuns finished: ").append(cs.runsWithFinish);
            if (cs.averageFinishMillis != 0) {
                builder.append(" (Average Time: ").append(cs.averageFinishFormatted).append(")");
            }
        }

        return builder.toString();
    }

    private static long getCurrentSessionStartTime() {
        return PrismarineTracker.getCurrentSession().sessionStartTime;
    }

    private void retrieveRuns() {
        if (!Files.exists(PrismarineTracker.RUNS_DIR)) {
            return;
        }
        Pattern numberPattern = Pattern.compile("\\d+");
        try {
            List<Long> runLongs = Files.list(PrismarineTracker.RUNS_DIR)
                    .map(Path::getFileName)
                    .map(Object::toString)
                    .filter(s -> s.endsWith(".json"))
                    .map(s -> s.substring(0, s.length() - 5))
                    .filter(s -> numberPattern.matcher(s).matches())
                    .map(Long::parseLong)
                    .sorted()
                    .collect(Collectors.toList());
            allRuns.addAll(runLongs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void retrieveSessions() {
        if (!Files.exists(PrismarineTracker.SESSIONS_DIR)) {
            return;
        }
        Pattern numberPattern = Pattern.compile("\\d+");
        try {
            List<Long> sessionLongs = Files.list(PrismarineTracker.SESSIONS_DIR)
                    .map(Path::getFileName)
                    .map(Object::toString)
                    .filter(s -> s.endsWith(".json"))
                    .map(s -> s.substring(0, s.length() - 5))
                    .filter(s -> numberPattern.matcher(s).matches())
                    .map(Long::parseLong)
                    .sorted()
                    .collect(Collectors.toList());
            allSessions.addAll(sessionLongs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void onShowTypeButtonPress() {
        if (displayType.equals("sessions")) {
            displayType = "runs";
            showTypeButton.setText("Show Sessions");
            showLatestRun();
        } else { // displaying.equals("sessions")
            displayType = "sessions";
            showTypeButton.setText("Show Completed Runs");
            showCurrentSession();
        }
    }

    private void showLatestRun() {
        displayed = allRuns.get(allRuns.size() - 1);
        showRun(displayed);
    }

    private void showRun(long toDisplay) {
        displayed = toDisplay;
        CompletedRun cr;
        try {
            cr = GSON.fromJson(FileUtil.readString(PrismarineTracker.RUNS_DIR.resolve(toDisplay + ".json")), CompletedRun.class);
        } catch (IOException e) {
            statsArea.setText("Failed to read file!\n" + ExceptionUtil.toDetailedString(e));
            return;
        }

        statsArea.setText(constructInformation(cr));
        revalidateButtons();
        revalidateLabel();
    }

    private void onNextButtonPress() {
        boolean showingRuns = displayType.equals("runs");
        List<Long> allThings = showingRuns ? allRuns : allSessions;
        int index = allThings.indexOf(displayed);
        if (showingRuns) {
            showRun(allThings.get(Math.min(index + 1, allThings.size() - 1)));
        } else {
            showSession(allThings.get(Math.min(index + 1, allThings.size() - 1)));
        }
    }

    private void onPreviousButtonPress() {
        boolean showingRuns = displayType.equals("runs");
        List<Long> allThings = showingRuns ? allRuns : allSessions;
        int index = allThings.indexOf(displayed);
        if (showingRuns) {
            showRun(allThings.get(Math.max(index - 1, 0)));
        } else {
            showSession(allThings.get(Math.max(index - 1, 0)));
        }
    }

    private void showSession(Long l) {
        if (l == getCurrentSessionStartTime()) {
            showCurrentSession();
            return;
        }
        displayed = l;
        revalidateButtons();

        Path path = PrismarineTracker.SESSIONS_DIR.resolve(l + ".json");
        if (!Files.exists(path)) {
            statsArea.setText("File for the session doesn't exist!\nIt must have been deleted while this GUI was open...");
            return;
        }

        try {
            String s = FileUtil.readString(path);
            PlaySession playSession = GSON.fromJson(s, PlaySession.class);
            statsArea.setText(constructInformation(playSession));
        } catch (IOException | JsonSyntaxException e) {
            statsArea.setText("Failed to read file!\n" + ExceptionUtil.toDetailedString(e));
        }
        revalidateLabel();
    }

    private void revalidateLabel() {
        String s = FormattingUtil.formatDate(displayed);
        this.nameLabel.setText(s);
    }

    private void showCurrentSession() {
        PlaySession s = PrismarineTracker.getCurrentSession();
        displayed = s.sessionStartTime;
        statsArea.setText(constructInformation(s));

        if (!allSessions.contains(s.sessionStartTime)) {
            allSessions.add(s.sessionStartTime);
        }
        revalidateButtons();
        this.nameLabel.setText("Current Session");
    }

    private void revalidateButtons() {
        if (displayType.equals("runs")) {
            int index = allRuns.indexOf(displayed);
            previousButton.setEnabled(index > 0);
            nextButton.setEnabled(index < allRuns.size() - 1);
        } else {
            int index = allSessions.indexOf(displayed);
            previousButton.setEnabled(index > 0);
            nextButton.setEnabled(index < allSessions.size() - 1);
        }
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        nameLabel = new JLabel();
        nameLabel.setText("Today's Session");
        panel1.add(nameLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        previousButton = new JButton();
        previousButton.setEnabled(false);
        previousButton.setText("Previous");
        panel1.add(previousButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        nextButton = new JButton();
        nextButton.setEnabled(false);
        nextButton.setText("Next");
        panel1.add(nextButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        mainPanel.add(scrollPane1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        statsArea = new JTextArea();
        statsArea.setEditable(false);
        statsArea.setText("");
        scrollPane1.setViewportView(statsArea);
        showTypeButton = new JButton();
        showTypeButton.setText("Show Completed Runs");
        mainPanel.add(showTypeButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

    public void refresh() {
        Jingle.log(Level.INFO, "Refreshing Prismarine Tracker GUI");
        closed = false;
        displayType = "sessions";
        displayed = 0;
        allRuns.clear();
        allSessions.clear();
        retrieveSessions();
        retrieveRuns();
        showCurrentSession();
        showTypeButton.setEnabled(!allRuns.isEmpty());
    }
}
