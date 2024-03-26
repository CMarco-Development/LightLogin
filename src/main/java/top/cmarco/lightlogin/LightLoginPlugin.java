/*
 * LightLogin - Optimised and Safe SpigotMC Software for Authentication
 *     Copyright © 2024  CMarco
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package top.cmarco.lightlogin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.bstats.bukkit.Metrics;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import top.cmarco.lightlogin.command.CommandManager;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.configuration.ConfigUtils;
import top.cmarco.lightlogin.configuration.ConfigurationFiles;
import top.cmarco.lightlogin.configuration.LightConfiguration;
import top.cmarco.lightlogin.data.*;
import top.cmarco.lightlogin.database.*;
import top.cmarco.lightlogin.library.LibraryManager;
import top.cmarco.lightlogin.listeners.ListenerManager;
import top.cmarco.lightlogin.log.AuthLogs;
import top.cmarco.lightlogin.log.SafetyFilter;
import top.cmarco.lightlogin.log.StartupLogo;
import top.cmarco.lightlogin.mail.MailManager;
import top.cmarco.lightlogin.world.WorldUtils;

import java.util.EnumMap;
import java.util.Objects;

public final class LightLoginPlugin extends JavaPlugin {

    private static final int METRICS_ID = 21378;

    private AbstractFilter safetyFilter = null;
    private LightConfiguration lightConfiguration = null;
    private PluginDatabase database = null;
    private AuthenticationManager authenticationManager = null;
    private AutoKickManager autoKickManager = null;
    private final EnumMap<ConfigurationFiles, FileConfiguration> languagesConfigMap = new EnumMap<>(ConfigurationFiles.class);
    private PlaintextPasswordManager plaintextPasswordManager = null;
    private boolean disabled = false;
    private AuthLogs authLogs = null;
    private VoidLoginManager voidLoginManager = null;
    private StartupLoginsManager startupLoginsManager = null;
    private MailManager mailManager = null;
    private LibraryManager libraryManager = null;
    private CommandManager commandManager = null;
    private ListenerManager listenerManager = null;
    private Metrics dataStealer = null;

    /* --------------------------------------------------------- */

    /**
     * Plugin startup logic.
     * Called on STARTUP phase.
     */
    @Override
    public void onEnable() {
        this.printLogoStartup();
        this.setupChatFilter();
        this.setupSecurity();
        this.loadLibraries();
        this.setupConfig();
        this.setupDatabase();
        this.setupAuthenticationManager();
        this.setupPasswordManager();
        this.setupStartupLoginsManager();
        this.registerAllListeners();
        this.setupMailManager();
        this.setupCommands();
        this.setupKickManager();
        this.setAuthLogs();
        this.startMetrics();
    }

    /**
     * Plugin disable logic.
     */
    @Override
    public void onDisable() {
        if (this.database != null) {
            this.database.close();
        }

        if (this.authLogs != null) {
            this.authLogs.saveLogs();
        }

        this.stopMetrics();
    }

    /* --------------------------------------------------------- */

    private void startMetrics() {
        this.dataStealer = new Metrics(this, METRICS_ID);
    }

    private void stopMetrics() {
        if (this.dataStealer == null) {
            return;
        }

        this.dataStealer.shutdown();
    }

    private void loadLibraries() {
        this.libraryManager = new LibraryManager(this);
        this.libraryManager.loadLibraries();
    }

    private void setupMailManager() {
        this.mailManager = new MailManager(this);

        if (!lightConfiguration.isEmailEnabled()) {
            return;
        }

        this.mailManager.startSession();
    }

    private void setupStartupLoginsManager() {
        this.startupLoginsManager = new StartupLoginsManager();
    }

    public void sendConsoleColoured(String text) {
        super.getServer().getConsoleSender().sendMessage(LightLoginCommand.colorMessage(text));
    }

    private void setupPasswordManager() {
        this.plaintextPasswordManager = new PlaintextPasswordManager();
    }

    private void printLogoStartup() {
        this.sendConsoleColoured(StartupLogo.LOGO_ARRAY[0]);
        this.sendConsoleColoured(StartupLogo.getLoadingString(0));
    }

    private void setupSecurity() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(1));
        this.sendConsoleColoured(StartupLogo.getLoadingString(2));
    }

    private void setAuthLogs() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(5));
        this.authLogs = new AuthLogs(this);
        this.sendConsoleColoured(StartupLogo.getLoadingString(6));
    }

    private void saveAllConfigs() {
        for (final ConfigurationFiles file : ConfigurationFiles.values()) {
            final FileConfiguration fileConfiguration = ConfigUtils.createCustomConfig(file, this);
            this.languagesConfigMap.put(file, fileConfiguration);
        }
    }

    /**
     * Setup chat filter to prevent password leaks
     */
    private void setupChatFilter() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(9));
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        this.safetyFilter = new SafetyFilter();
        this.safetyFilter.start();
        rootLogger.addFilter(this.safetyFilter);
        this.sendConsoleColoured(StartupLogo.getLoadingString(10));
    }

    /**
     * Setup all the commands registered by this software,
     * registering them into the server instance.
     *
     */
    private void setupCommands() {
        this.commandManager = new CommandManager(this);
        this.commandManager.registerCommands();
    }

    /**
     * Setup the authentication manager instance
     */
    private void setupAuthenticationManager() {
        if (this.authenticationManager != null) {
            return;
        }

        this.sendConsoleColoured(StartupLogo.getLoadingString(11));
        this.authenticationManager = new BasicAuthenticationManager(this);
        this.authenticationManager.startLoginNotifyTask();
        this.authenticationManager.startRegisterNotifyTask();
        this.sendConsoleColoured(StartupLogo.getLoadingString(12));
    }

    private void setupKickManager() {
        if (this.autoKickManager != null) {
            return;
        }

        this.sendConsoleColoured(StartupLogo.getLoadingString(13));
        this.autoKickManager = new AutoKickManager(this);
        this.autoKickManager.startAutoKickTask();
        this.sendConsoleColoured(StartupLogo.getLoadingString(14));
    }

    /**
     * Method responsible for registering all of this software listeners.
     */
    private void registerAllListeners() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(15));
        this.listenerManager = new ListenerManager(this);
        this.listenerManager.addAllListeners();
        this.listenerManager.registerAllAddedListeners();
        this.sendConsoleColoured(StartupLogo.getLoadingString(16));
    }

    private void setupConfig() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(17));
        saveDefaultConfig();
        saveAllConfigs();
        ConfigurationFiles chosenLanguage = ConfigUtils.chosenLanguage(this);
        this.sendConsoleColoured(StartupLogo.getLoadingString(21) + chosenLanguage.name() + "!");
        lightConfiguration = new LightConfiguration(chosenLanguage, this);
        lightConfiguration.loadConfig();
    }

    private void setupDatabase() {
        this.sendConsoleColoured(StartupLogo.getLoadingString(22));
        final DatabaseType databaseType = DatabaseType.fromName(
                Objects.requireNonNull(this.lightConfiguration.getDatabaseType()));

        if (databaseType == null) {
            this.disabled = true;
            getLogger().warning(StartupLogo.getLoadingString(23));
            if (lightConfiguration.isCrashShutdown()) {
                getLogger().warning(StartupLogo.getLoadingString(24));
                getServer().shutdown();
            }
            return;
        }

        switch (databaseType) {
            case SQLITE: {this.database = new SQLiteDatabase(this); break;}
            case MYSQL: {this.database = new MySqlDatabase(this); break;}
            case POSTGRESQL: {this.database = new PostgreSQLDatabase(this); break;}
            default: {this.database = null; break;}
        }

        assert this.database != null;

        this.database.loadDriverClass();
        this.database.connect();
        this.database.createTables();

        this.sendConsoleColoured(StartupLogo.getLoadingString(25) + databaseType.name() + StartupLogo.getLoadingString(26));
    }

    /* -------------------------------------------------------------------------- */

    public LightConfiguration getLightConfiguration() {
        return lightConfiguration;
    }

    public PluginDatabase getDatabase() {
        return database;
    }

    public AuthenticationManager getAuthenticationManager() {
        return authenticationManager;
    }

    public EnumMap<ConfigurationFiles, FileConfiguration> getLanguagesConfigMap() {
        return languagesConfigMap;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public AutoKickManager getAutoKickManager() {
        return autoKickManager;
    }

    public PlaintextPasswordManager getPlaintextPasswordManager() {
        return plaintextPasswordManager;
    }

    public AuthLogs getAuthLogs() {
        return authLogs;
    }

    public StartupLoginsManager getStartupLoginsManager() {
        return startupLoginsManager;
    }
}