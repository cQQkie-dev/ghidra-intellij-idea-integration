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
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.table.TableModel;

import docking.ActionContext;
import docking.ComponentProvider;
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
    private static final Icon INTELLIJ_IDEA_ICON = new ImageIcon(
        IdeaIntegrationPlugin.class.getResource(
            "/images/intellij-idea-16.png"
        )
    );

    private ToolOptions options;

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
    private void installScriptManagerToolbarAction() {
        ComponentProvider scriptManagerProvider =
            tool.getComponentProvider("Script Manager");

        if (scriptManagerProvider == null) {
            Msg.warn(
                this,
                "Unable to add the IntelliJ IDEA toolbar button because the " +
                    "Script Manager component provider was not found."
            );
            return;
        }

        new ActionBuilder("Edit with IntelliJ IDEA Toolbar", getName())
            .toolBarIcon(INTELLIJ_IDEA_ICON)
            .toolBarGroup(null)
            .description("Edit Script with IntelliJ IDEA")
            .enabledWhen(this::isScriptFileContext)
            .onAction(this::openSelectedScriptInIdea)
            .buildAndInstallLocal(scriptManagerProvider);
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