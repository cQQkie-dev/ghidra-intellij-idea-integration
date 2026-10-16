package com.cqqkie.ghidra.idea;

import ghidra.app.CorePluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.framework.main.ApplicationLevelOnlyPlugin;
import ghidra.framework.options.ToolOptions;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;

//@formatter:off
@PluginInfo(
    status = PluginStatus.STABLE,
    packageName = CorePluginPackage.NAME,
    category = PluginCategoryNames.COMMON,
    shortDescription = "IntelliJ IDEA Integration Options",
    description = "Options for IntelliJ IDEA Integration."
)
//@formatter:on
public class IdeaIntegrationOptionsPlugin extends Plugin
        implements ApplicationLevelOnlyPlugin {

    public static final String PLUGIN_OPTIONS_NAME = "IntelliJ IDEA Integration";

    public static final String SCRIPT_EDITOR_PORT_OPTION = "Script Editor Port";

    private static final String SCRIPT_EDITOR_PORT_DESCRIPTION =
        "The loopback port used to request script opening in the IntelliJ IDEA Ghidra plug-in. " +
            "It must match Settings > Tools > Ghidra Script Editor in IntelliJ IDEA.";

    public static final int DEFAULT_SCRIPT_EDITOR_PORT = 12321;

    public IdeaIntegrationOptionsPlugin(PluginTool tool) {
        super(tool);
    }

    @Override
    public void init() {
        super.init();
        registerOptions(tool.getOptions(PLUGIN_OPTIONS_NAME));
    }

    /**
     * Registers shared application-level IDEA Integration options.
     *
     * This is also called by the CodeBrowser integration plug-in so the option is available
     * after the user enables only the Script Manager action plug-in.
     */
    public static void registerOptions(ToolOptions options) {
        options.registerOption(
            SCRIPT_EDITOR_PORT_OPTION,
            DEFAULT_SCRIPT_EDITOR_PORT,
            null,
            SCRIPT_EDITOR_PORT_DESCRIPTION
        );
    }
}