package xyz.duncanruns.jingle.exampleplugin;

import com.google.common.io.Resources;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.JingleAppLaunch;
import xyz.duncanruns.jingle.exampleplugin.gui.ExamplePluginPanel;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.plugin.PluginEvents;
import xyz.duncanruns.jingle.plugin.PluginHotkeys;
import xyz.duncanruns.jingle.plugin.PluginManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;

public class ExamplePlugin {
    public static void main(String[] args) throws IOException {
        // This is only used to test the plugin in the dev environment
        // ExamplePlugin.main itself is never used when users run Jingle

        JingleAppLaunch.launchWithDevPlugin(args, PluginManager.JinglePluginData.fromString(
                Resources.toString(Resources.getResource(ExamplePlugin.class, "/jingle.plugin.json"), Charset.defaultCharset())
        ), ExamplePlugin::initialize);
    }

    public static void initialize() {
        // This gets run once when Jingle launches

        JingleGUI.addPluginTab("Example Plugin", new ExamplePluginPanel().mainPanel);
        PluginHotkeys.addHotkeyAction("My Awesome Hotkey", () -> Jingle.log(Level.INFO, "(Example Plugin) Awesome hotkey pressed!!!"));

        AtomicLong timeTracker = new AtomicLong(System.currentTimeMillis());

        PluginEvents.END_TICK.register(() -> {
            // This gets run every tick (1 ms)
            long currentTime = System.currentTimeMillis();
            if (currentTime - timeTracker.get() > 3000) {
                // This gets ran every 3 seconds
                // Jingle.log(Level.INFO, "Example Plugin ran for another 3 seconds.");
                timeTracker.set(currentTime);
            }
        });

        PluginEvents.STOP.register(() -> {
            // This gets run when Jingle is shutting down
            Jingle.log(Level.INFO, "Example plugin shutting down...");
        });

        PluginEvents.ENTER_WORLD.register(() -> {
            Jingle.log(Level.INFO, "ExamplePlugin: World has been entered!");
        });
        Jingle.log(Level.INFO, "Example Plugin Initialized");
    }
}
