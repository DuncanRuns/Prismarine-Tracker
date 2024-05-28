package xyz.duncanruns.prismarinetracker.gui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import xyz.duncanruns.julti.gui.JultiGUI;
import xyz.duncanruns.julti.gui.PluginsGUI;
import xyz.duncanruns.julti.util.ExceptionUtil;
import xyz.duncanruns.julti.util.FileUtil;
import xyz.duncanruns.prismarinetracker.PlaySession;
import xyz.duncanruns.prismarinetracker.PrismarineTracker;
import xyz.duncanruns.prismarinetracker.util.FormattingUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PrismarineTrackerGUI extends JFrame {
    private static PrismarineTrackerGUI instance = null;
    private static final Gson GSON = new Gson();
    private boolean closed = false;
    private long displayedSession = 0;
    private final List<Long> allSessions = new ArrayList<>();
    private JTextArea statsArea;
    private JPanel mainPanel;
    private JLabel sessionNameLabel;
    private JButton previousButton;
    private JButton nextButton;

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    public PrismarineTrackerGUI() {
        Pattern numberPattern = Pattern.compile("\\d+");
        try {
            List<Long> a = Files.list(PrismarineTracker.FOLDER_PATH)
                    .map(Path::getFileName)
                    .map(Object::toString)
                    .filter(s -> s.endsWith(".json"))
                    .map(s -> s.substring(0, s.length() - 5))
                    .filter(s -> numberPattern.matcher(s).matches()).map(Long::parseLong).sorted().collect(Collectors.toList());
            allSessions.addAll(a);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        showCurrentSession();
        nextButton.addActionListener(a -> {
            int index = allSessions.indexOf(displayedSession);
            showSession(allSessions.get(Math.min(index + 1, allSessions.size() - 1)));
        });
        previousButton.addActionListener(a -> {
            int index = allSessions.indexOf(displayedSession);
            showSession(allSessions.get(Math.max(index - 1, 0)));
        });
        setupWindow();
    }

    public static PrismarineTrackerGUI open() {
        if (instance == null || instance.isClosed()) {
            instance = new PrismarineTrackerGUI();
        }
        instance.requestFocus();
        return instance;
    }

    public static PrismarineTrackerGUI get() {
        return instance;
    }

    private static String constructInformation(PlaySession session) {
        StringBuilder builder = new StringBuilder();

        long sessionLength = session.lastActivity - session.sessionStartTime;
        long timePlayed = sessionLength;
        for (long b : session.breaks) {
            timePlayed -= b;
        }

        builder.append("Time Played: ").append(FormattingUtil.formatSeconds(timePlayed / 1000));
        builder.append(" (Session Length: ").append(FormattingUtil.formatSeconds(sessionLength / 1000)).append(")");

        builder.append("\nBreaks Taken: ").append(session.breaks.size());
        if (!session.breaks.isEmpty()) {
            builder.append(" (Average Break Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.breaks) / 1000)).append(")");
        }

        builder.append("\nResets: ").append(session.resets);

        if (session.runsWithGold > 0) {
            builder.append("\n\nMonuments Mined: ").append(session.runsWithGold);
            if (!session.goldBlockPickupTimes.isEmpty()) {
                builder.append(" (Average Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.goldBlockPickupTimes) / 1000)).append(")");
            }
            builder.append("\nMonuments per hour: ").append(FormattingUtil.getPerHour(session.runsWithGold, timePlayed));
        }

        if (session.runsWithVillage > 0) {
            builder.append("\n\nVillages entered: ").append(session.runsWithVillage);
            if (!session.villageEnterTimes.isEmpty()) {
                builder.append(" (Average Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.villageEnterTimes) / 1000)).append(")");
            }
            builder.append("\nVillages per hour: ").append(FormattingUtil.getPerHour(session.runsWithVillage, timePlayed));
            if (session.runsWithTrading > 0) {
                builder.append("\nVillages with Trading: ").append(session.runsWithTrading).append(" (Runs with 10 pearls: ").append(session.runsWith10Pearls).append(")");
            }
        }

        if (session.runsWithNether > 0) {
            builder.append("\n\nNethers entered: ").append(session.runsWithNether);
            if (!session.netherEnterTimes.isEmpty()) {
                builder.append(" (Average Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.netherEnterTimes) / 1000)).append(")");
            }
        }
        if (session.runsWithFort > 0) {
            builder.append("\nFortresses entered: ").append(session.runsWithFort);
            if (!session.fortressEnterTimes.isEmpty()) {
                builder.append(" (Average Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.fortressEnterTimes) / 1000)).append(")");
            }
        }
        if (session.runsWithNetherExit > 0) {
            builder.append("\nNethers exited: ").append(session.runsWithNetherExit);
            if (!session.netherExitTimes.isEmpty()) {
                builder.append(" (Average Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.netherExitTimes) / 1000)).append(")");
            }
        }

        if (session.runsWithStronghold > 0) {
            builder.append("\n\nStrongholds entered: ").append(session.runsWithStronghold);
            if (!session.strongholdEnterTimes.isEmpty()) {
                builder.append(" (Average Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.strongholdEnterTimes) / 1000)).append(")");
            }
        }
        if (session.runsWithEndEnter > 0) {
            builder.append("\nEnds entered: ").append(session.runsWithEndEnter);
            if (!session.endEnterTimes.isEmpty()) {
                builder.append(" (Average Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.endEnterTimes) / 1000)).append(")");
            }
        }


        if (session.runsFinished > 0) {
            builder.append("\n\nRuns finished: ").append(session.runsFinished);
            if (!session.runFinishTimes.isEmpty()) {
                builder.append(" (Average Time: ").append(FormattingUtil.formatSeconds(FormattingUtil.getAverageTime(session.runFinishTimes) / 1000)).append(")");
            }
        }

        return builder.toString();
    }

    private static long getCurrentSessionStartTime() {
        return PrismarineTracker.getCurrentSession().sessionStartTime;
    }

    private void showSession(Long l) {
        if (l == getCurrentSessionStartTime()) {
            showCurrentSession();
            return;
        }
        displayedSession = l;
        revalidateButtons();

        Path path = PrismarineTracker.FOLDER_PATH.resolve(l + ".json");
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
        revalidateDate();
    }

    private void revalidateDate() {
        String s = FormattingUtil.formatDate(displayedSession);
        this.sessionNameLabel.setText(s);
    }

    private void setupWindow() {
        PluginsGUI pluginsGUI = PluginsGUI.getGUI();
        Point location = pluginsGUI == null || pluginsGUI.isClosed() ? JultiGUI.getJultiGUI().getLocation() : PluginsGUI.getGUI().getLocation();
        this.setLocation(location.x, location.y + 30);
        this.setTitle("Prismarine Tracker Stats");
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                PrismarineTrackerGUI.this.onClose();
            }
        });
        this.pack();
        this.setVisible(true);
        this.setSize(350, 400);
        this.setContentPane(mainPanel);
    }

    private void showCurrentSession() {
        PlaySession s = PrismarineTracker.getCurrentSession();
        displayedSession = s.sessionStartTime;
        statsArea.setText(constructInformation(s));

        if (!allSessions.contains(s.sessionStartTime)) {
            allSessions.add(s.sessionStartTime);
        }
        revalidateButtons();
        this.sessionNameLabel.setText("Current Session");
    }

    private void revalidateButtons() {
        int index = allSessions.indexOf(displayedSession);
        previousButton.setEnabled(index > 0);
        nextButton.setEnabled(index < allSessions.size() - 1);
    }

    private void onClose() {
        this.closed = true;
    }

    public boolean isClosed() {
        return closed;
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
        mainPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        sessionNameLabel = new JLabel();
        sessionNameLabel.setText("Today's Session");
        panel1.add(sessionNameLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        previousButton = new JButton();
        previousButton.setEnabled(false);
        previousButton.setText("Previous");
        panel1.add(previousButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        nextButton = new JButton();
        nextButton.setEnabled(false);
        nextButton.setText("Next");
        panel1.add(nextButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        mainPanel.add(scrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        statsArea = new JTextArea();
        statsArea.setEditable(false);
        statsArea.setText("");
        scrollPane1.setViewportView(statsArea);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
