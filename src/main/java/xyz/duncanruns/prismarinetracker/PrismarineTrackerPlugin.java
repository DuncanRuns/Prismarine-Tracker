package xyz.duncanruns.prismarinetracker;

import com.google.common.io.Resources;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.julti.Julti;
import xyz.duncanruns.julti.JultiAppLaunch;
import xyz.duncanruns.julti.plugin.PluginEvents;
import xyz.duncanruns.julti.plugin.PluginInitializer;
import xyz.duncanruns.julti.plugin.PluginManager;

import java.io.IOException;
import java.nio.charset.Charset;

public class PrismarineTrackerPlugin implements PluginInitializer {
    public static void main(String[] args) throws IOException {
        JultiAppLaunch.launchWithDevPlugin(args, PluginManager.JultiPluginData.fromString(
                Resources.toString(Resources.getResource(PrismarineTrackerPlugin.class, "/julti.plugin.json"), Charset.defaultCharset())
        ), new PrismarineTrackerPlugin());
    }

    @Override
    public void initialize() {
        PrismarineTracker.load();
        PluginEvents.RunnableEventType.STOP.register(PrismarineTracker::stop);
        Julti.log(Level.INFO, "Prismarine Tracker Initialized");
    }

    @Override
    public void onMenuButtonPress() {
    }
}
