/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cqqkie.ghidra.idea;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.table.TableModel;

import docking.ActionContext;
import docking.ComponentProvider;
import docking.action.DockingAction;
import docking.action.DockingActionIf;
import docking.action.MenuData;
import docking.action.ToolBarData;
import docking.action.builder.ActionBuilder;
import docking.widgets.table.GTable;
import docking.widgets.table.RowObjectTableModel;
import generic.jar.ResourceFile;
import ghidra.app.CorePluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.ProgramPlugin;
import ghidra.app.services.GhidraScriptService;
import ghidra.framework.main.AppInfo;
import ghidra.framework.options.ToolOptions;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.util.Msg;

/**
 * Adds an IntelliJ IDEA Script Manager action that opens the selected script through the
 * GhidraDev-compatible IntelliJ loopback listener.
 */
//@formatter:off
@PluginInfo(
    status = PluginStatus.STABLE,
    packageName = CorePluginPackage.NAME,
    category = PluginCategoryNames.COMMON,
    shortDescription = "IntelliJ IDEA Integration",
    description = "Opens Script Manager scripts in IntelliJ IDEA through a loopback listener.",
    servicesRequired = { GhidraScriptService.class }
)
//@formatter:on
public class IdeaIntegrationPlugin extends ProgramPlugin {

    private static final String LOOPBACK_ADDRESS = "127.0.0.1";
    private static final int CONNECT_TIMEOUT_MILLIS = 1_500;
    private static final String SCRIPT_MANAGER_OWNER = "GhidraScriptMgrPlugin";

    private static final String BASIC_EDITOR_ACTION_NAME = "Edit";
    private static final String ECLIPSE_EDITOR_ACTION_NAME = "EditEclipse";
    private static final String VSCODE_EDITOR_ACTION_NAME = "EditVSCode";

    private static final String BASIC_EDITOR_SUBGROUP = "10";
    private static final String ECLIPSE_EDITOR_SUBGROUP = "20";
    private static final String IDEA_EDITOR_SUBGROUP = "30";
    private static final String VSCODE_EDITOR_SUBGROUP = "40";
    private static final Icon INTELLIJ_IDEA_ICON = new ImageIcon(
        IdeaIntegrationPlugin.class.getResource(
            "/images/intellij-idea-16.png"
        )
    );

    private ToolOptions options;

    private ComponentProvider scriptManagerProvider;
    private DockingAction scriptManagerToolbarAction;

    private final Map<DockingActionIf, String> originalToolbarSubgroups =
        new IdentityHashMap<>();

    private final Map<DockingActionIf, String> originalPopupSubgroups =
        new IdentityHashMap<>();

    public IdeaIntegrationPlugin(PluginTool tool) {
        super(tool);
    }

    @Override
    public void init() {
        super.init();

        options = AppInfo.getFrontEndTool().getOptions(
            IdeaIntegrationOptionsPlugin.PLUGIN_OPTIONS_NAME
        );

        IdeaIntegrationOptionsPlugin.registerOptions(options);

        new ActionBuilder("Edit with IntelliJ IDEA", getName())
            .popupMenuPath("Edit with IntelliJ IDEA")
            .popupMenuIcon(INTELLIJ_IDEA_ICON)
            .popupMenuGroup(null, IDEA_EDITOR_SUBGROUP)
            .description("Open the selected script in IntelliJ IDEA")
            .popupWhen(this::isScriptFileContext)
            .enabledWhen(this::isScriptFileContext)
            .onAction(this::openSelectedScriptInIdea)
            .buildAndInstall(tool);

        installScriptManagerToolbarAction();
    }

    /**
     * Adds an IntelliJ IDEA toolbar button to Ghidra's Script Manager.
     *
     * The existing global action provides the Script Manager right-click popup item. A separate
     * local action is required for the Script Manager toolbar so it does not appear in the
     * CodeBrowser global toolbar or duplicate the popup entry.
     */
    /**
     * Adds an IntelliJ IDEA toolbar button to Ghidra's Script Manager.
     *
     * The existing global action provides the Script Manager right-click popup item. A separate
     * local action is required for the Script Manager toolbar so it does not appear in the
     * CodeBrowser global toolbar or duplicate the popup entry.
     */
    private void installScriptManagerToolbarAction() {
        scriptManagerProvider =
            tool.getComponentProvider("Script Manager");

        if (scriptManagerProvider == null) {
            Msg.warn(
                this,
                "Unable to add the IntelliJ IDEA toolbar button because the " +
                    "Script Manager component provider was not found."
            );
            return;
        }

        /*
         * Set built-in action ordering first. Adding the IDEA action afterward causes Ghidra to
         * rebuild the Script Manager toolbar using the desired subgroups immediately, including
         * when this plug-in is disabled and re-enabled while Script Manager remains open.
         */
        organizeScriptManagerEditToolbar(scriptManagerProvider);

        scriptManagerToolbarAction =
            new ActionBuilder("Edit with IntelliJ IDEA Toolbar", getName())
                .toolBarIcon(INTELLIJ_IDEA_ICON)
                .toolBarGroup(null, IDEA_EDITOR_SUBGROUP)
                .description("Edit Script with IntelliJ IDEA")
                .enabledWhen(this::isScriptFileContext)
                .onAction(this::openSelectedScriptInIdea)
                .buildAndInstallLocal(scriptManagerProvider);
    }

    /**
     * Groups the built-in and companion external-editor actions together in Script Manager's
     * local toolbar. The built-in actions do not define toolbar subgroups, so the companion
     * action would otherwise sort after all Script Manager actions by owner name.
     */
    private void organizeScriptManagerEditToolbar(
            ComponentProvider scriptManagerProvider) {

        setBuiltInActionSubgroups(
            scriptManagerProvider,
            BASIC_EDITOR_ACTION_NAME,
            BASIC_EDITOR_SUBGROUP
        );

        setBuiltInActionSubgroups(
            scriptManagerProvider,
            ECLIPSE_EDITOR_ACTION_NAME,
            ECLIPSE_EDITOR_SUBGROUP
        );

        setBuiltInActionSubgroups(
            scriptManagerProvider,
            VSCODE_EDITOR_ACTION_NAME,
            VSCODE_EDITOR_SUBGROUP
        );
    }

    /**
     * Assigns matching toolbar and popup subgroups to Script Manager's built-in editor actions.
     *
     * The Script Manager does not assign explicit subgroups to its built-in editor actions.
     * Without these values, the companion action is sorted separately by owner name.
     */
    private void setBuiltInActionSubgroups(
            ComponentProvider scriptManagerProvider,
            String actionName,
            String subgroup) {

        for (DockingActionIf action :
                tool.getLocalActions(scriptManagerProvider)) {

            if (!SCRIPT_MANAGER_OWNER.equals(action.getOwner()) ||
                    !actionName.equals(action.getName())) {
                continue;
            }

            ToolBarData toolbarData = action.getToolBarData();
            if (toolbarData != null) {
                originalToolbarSubgroups.putIfAbsent(
                    action,
                    toolbarData.getToolBarSubGroup()
                );

                toolbarData.setToolBarSubGroup(subgroup);
            }

            MenuData popupData = action.getPopupMenuData();
            if (popupData != null) {
                originalPopupSubgroups.putIfAbsent(
                    action,
                    popupData.getMenuSubGroup()
                );

                popupData.setMenuSubGroup(subgroup);
            }

            return;
        }

        Msg.warn(
            this,
            "Unable to locate Script Manager editor action: " + actionName
        );
    }

    @Override
    protected void dispose() {
        removeScriptManagerToolbarAction();

        originalToolbarSubgroups.forEach((action, subgroup) -> {
            ToolBarData toolbarData = action.getToolBarData();

            if (toolbarData != null) {
                toolbarData.setToolBarSubGroup(subgroup);
            }
        });

        originalPopupSubgroups.forEach((action, subgroup) -> {
            MenuData popupData = action.getPopupMenuData();

            if (popupData != null) {
                popupData.setMenuSubGroup(subgroup);
            }
        });

        originalToolbarSubgroups.clear();
        originalPopupSubgroups.clear();

        super.dispose();
    }

    private void removeScriptManagerToolbarAction() {
        if (scriptManagerProvider == null || scriptManagerToolbarAction == null) {
            return;
        }

        tool.removeLocalAction(
            scriptManagerProvider,
            scriptManagerToolbarAction
        );

        scriptManagerToolbarAction = null;
        scriptManagerProvider = null;
    }

    private boolean isScriptFileContext(ActionContext context) {
        ResourceFile script = getSelectedScript(context);

        if (script == null) {
            return false;
        }

        File file = script.getFile(false);
        return file != null && file.isFile();
    }

    /**
     * Resolves the Script Manager's selected script from its table view.
     *
     * Ghidra's Script Manager context object currently uses the selected table view row directly
     * against its unfiltered backing model. That can resolve to the wrong script while a Script
     * Manager filter is active. RowObjectTableModel#getRowObject(int) accepts a view row and
     * performs the correct filtered-view resolution.
     */
    private ResourceFile getSelectedScript(ActionContext context) {
        if (!(context.getSourceComponent() instanceof GTable table)) {
            return null;
        }

        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || table.getSelectedRowCount() != 1) {
            return null;
        }

        TableModel tableModel = table.getModel();
        if (!(tableModel instanceof RowObjectTableModel<?> rowObjectTableModel)) {
            return null;
        }

        Object rowObject = rowObjectTableModel.getRowObject(selectedRow);

        if (rowObject instanceof ResourceFile script) {
            return script;
        }

        return null;
    }

    private void openSelectedScriptInIdea(ActionContext context) {
        ResourceFile script = getSelectedScript(context);

        if (script == null) {
            return;
        }

        File file = script.getFile(false);
        if (file == null || !file.isFile()) {
            Msg.showError(
                this,
                tool.getToolFrame(),
                "Open Script in IntelliJ IDEA",
                "The selected script is not available as a local file."
            );
            return;
        }

        int port = options.getInt(
            IdeaIntegrationOptionsPlugin.SCRIPT_EDITOR_PORT_OPTION,
            IdeaIntegrationOptionsPlugin.DEFAULT_SCRIPT_EDITOR_PORT
        );

        if (port < 1024 || port > 65535) {
            Msg.showError(
                this,
                tool.getToolFrame(),
                "Open Script in IntelliJ IDEA",
                "The IntelliJ IDEA Script Editor Port must be between 1024 and 65535."
            );
            return;
        }

        try (Socket socket = new Socket()) {
            socket.connect(
                new InetSocketAddress(
                    InetAddress.getByName(LOOPBACK_ADDRESS),
                    port
                ),
                CONNECT_TIMEOUT_MILLIS
            );

            try (BufferedWriter output = new BufferedWriter(
                new OutputStreamWriter(
                    socket.getOutputStream(),
                    StandardCharsets.UTF_8
                )
            )) {
                output.write("open_" + file.getAbsolutePath());
                output.newLine();
                output.flush();
            }
        }
        catch (IOException e) {
            Msg.showError(
                this,
                tool.getToolFrame(),
                "Open Script in IntelliJ IDEA",
                "Unable to connect to the IntelliJ IDEA Script Editor listener at " +
                    LOOPBACK_ADDRESS + ":" + port + ".\n\n" +
                    "In IntelliJ IDEA, enable:\n" +
                    "Settings > Tools > Ghidra Script Editor > Enable Script Editor listener.",
                e
            );
        }
    }
}