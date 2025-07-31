package xyz.duncanruns.prismarinetracker;

import com.google.common.io.Resources;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.JingleAppLaunch;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.plugin.PluginEvents;
import xyz.duncanruns.jingle.plugin.PluginManager;
import xyz.duncanruns.jingle.util.ExceptionUtil;
import xyz.duncanruns.prismarinetracker.gui.PrismarineTrackerGUI;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.Charset;

public class PrismarineTrackerPlugin {
    public static void main(String[] args) throws IOException {
        JingleAppLaunch.launchWithDevPlugin(args, PluginManager.JinglePluginData.fromString(
                Resources.toString(Resources.getResource(PrismarineTrackerPlugin.class, "/jingle.plugin.json"), Charset.defaultCharset())
        ), PrismarineTrackerPlugin::initialize);
    }

    public static void initialize() {
        PrismarineTracker.init();
        PluginEvents.STOP.register(PrismarineTracker::stop);
        Jingle.log(Level.INFO, "Prismarine Tracker initialized!");

        PrismarineTrackerGUI panel = new PrismarineTrackerGUI();
        JingleGUI.addPluginTab("Primsarine Tracker", panel, panel::refresh);

        JButton clearQAB = JingleGUI.makeButton("Clear 1.15 Session", () -> {
            try {
                PrismarineTracker.clearSession();
            } catch (IOException e) {
                Jingle.log(Level.ERROR, "Failed to clear session: " + ExceptionUtil.toDetailedString(e));
            }
        }, () -> JingleGUI.get().openTab(panel), "Clears the Prismarine Tracker Session", true);
        JingleGUI.get().registerQuickActionButton(0, () -> clearQAB);
    }
}
