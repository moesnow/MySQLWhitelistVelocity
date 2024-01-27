package top.kotori.mysqlwhitelistvelocity;

import java.io.*;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;

@Plugin(
        id = BuildConstants.ID,
        name = BuildConstants.NAME,
        version = BuildConstants.VERSION,
        description = BuildConstants.DESCRIPTION
)
public class MySQLWhitelistVelocity {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Path configFile;
    private Properties config;
    public static Connection connection;

    @Inject
    public MySQLWhitelistVelocity(
            ProxyServer server,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configFile = dataDirectory.resolve("config.properties");
        new org.mariadb.jdbc.Driver();
    }

    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        try {
            CommandManager commandManager = server.getCommandManager();
            CommandMeta commandMeta = commandManager.metaBuilder("mywl")
                    // This will create a new alias for the command "/test"
                    // with the same arguments and functionality
                    .aliases("otherAlias", "anotherAlias")
                    .plugin(this)
                    .build();
            BrigadierCommand commandToRegister = this.createBrigadierCommand(server);
            commandManager.register(commandMeta, commandToRegister);

            this.saveDefaultConfig();
            this.config = loadConfig();

            if (Boolean.parseBoolean(config.getProperty("enabled"))){
                server.getScheduler().buildTask(this, this::createDatabaseTable).schedule();
            }

            logger.info("{} {} loaded successfully!", BuildConstants.NAME, BuildConstants.VERSION);
        } catch (Exception e) {
            logger.error("Error during plugin initialization", e);
        }
    }
    private void createDatabaseTable() {
        openConnection();

        try (PreparedStatement sql = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS `" + config.getProperty("table") + "` (`UUID` varchar(100), `user` varchar(100)) ;")) {
            sql.execute();
        } catch (SQLException e) {
            logger.error("Error while creating database table", e);
        } finally {
            closeConnection();
        }
    }
    public void saveDefaultConfig() {
        if (Files.notExists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (Files.notExists(configFile)) {
            String defaultConfigContent = """
                    # Whitelist Status
                    enabled: false

                    # MySQL settings
                    host: localhost
                    user: root
                    password: example
                    database: minecraft
                    port: 3306
                    table: mysql_whitelist
                    
                    # Kick message
                    message: Sorry, you are not in the whitelist.
                    """;
            try {
                Files.write(configFile, defaultConfigContent.getBytes(), StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new RuntimeException("Error while saving default configuration", e);
            }
        }

    }
    private Properties loadConfig() {
        Properties properties = new Properties();

        if (Files.exists(configFile)) {
            try (InputStream input = Files.newInputStream(configFile,StandardOpenOption.READ)) {
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
                properties.load(reader);
            } catch (IOException e) {
                throw new RuntimeException("Error while loading configuration", e);
            }
        }

        return properties;
    }
//    private void saveConfig(Properties properties) {
//        try (OutputStream output = Files.newOutputStream(configFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
//            OutputStreamWriter writer = new OutputStreamWriter(output,StandardCharsets.UTF_8);
//            properties.store(writer, "Updated Configuration");
//        } catch (IOException e) {
//            throw new RuntimeException("Error while saving configuration", e);
//        }
//    }
    public synchronized void openConnection() {
        try {
            connection = DriverManager.getConnection("jdbc:mysql://" + config.getProperty("host") + ":" + config.getProperty("port") + "/" + config.getProperty("database") + "?useSSL=false", config.getProperty("user"), config.getProperty("password"));
        } catch (SQLException e) {
            throw new RuntimeException("Error while opening connection", e);
        }
    }
    public static synchronized void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException("Error while closing connection", e);
        }
    }

    public void addWhitelist(CommandSource source,String player) {
        this.openConnection();

        try {
            PreparedStatement sql = connection.prepareStatement("SELECT * FROM `" + config.getProperty("table") + "` WHERE `user`=?;");
            sql.setString(1, player);
            ResultSet rs = sql.executeQuery();
            if (!rs.next()) {
                PreparedStatement sql1 = connection.prepareStatement("INSERT INTO `" + config.getProperty("table") + "` (`user`) VALUES (?);");
                sql1.setString(1, player);
                sql1.execute();
                sql1.close();
            }

            rs.close();
            sql.close();
            source.sendMessage(Component.text(player + " is now whitelisted.", NamedTextColor.GREEN));
        } catch (SQLException e) {
            throw new RuntimeException("Error while add whitelist", e);
        } finally {
            closeConnection();
        }

    }

    public void delWhitelist(CommandSource source,String player) {
        this.openConnection();

        try {
            PreparedStatement sql = connection.prepareStatement("DELETE FROM `" + config.getProperty("table") + "` WHERE `user`=?;");
            sql.setString(1, player);
            sql.execute();
            sql.close();
            source.sendMessage(Component.text(player + " is no longer whitelisted.", NamedTextColor.AQUA));
        } catch (SQLException e) {
            throw new RuntimeException("Error while add whitelist", e);
        } finally {
            closeConnection();
        }

    }

    public BrigadierCommand createBrigadierCommand(final ProxyServer proxy) {
        LiteralCommandNode<CommandSource> helloNode = BrigadierCommand.literalArgumentBuilder("mywl")
                .requires(source -> source.hasPermission("mysqlwhitelist"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    sendUsageMessage(source, "all");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("argument", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            builder.suggest("add").suggest("del");
//                                    .suggest("on").suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            String[] arguments = context.getArgument("argument", String.class).split(" ");
                            if (arguments.length==1){
                                if (arguments[0].equals("add")){
                                    sendUsageMessage(source, "add");
                                } else if (arguments[0].equals("del")) {
                                    sendUsageMessage(source, "del");
//                                } else if (arguments[0].equals("on")) {
//                                    config.setProperty("enabled", String.valueOf(true));
//                                    saveConfig(config);
//                                    source.sendMessage(Component.text("Whitelist enabled", NamedTextColor.GREEN));
//                                } else if (arguments[0].equals("off")) {
//                                    config.setProperty("enabled", String.valueOf(false));
//                                    saveConfig(config);
//                                    source.sendMessage(Component.text("Whitelist disabled", NamedTextColor.AQUA));
                                }else {
                                    sendUsageMessage(source, "all");
                                }
                            } else if (arguments.length==2) {
                                if (arguments[0].equals("add")){
                                    addWhitelist(source,arguments[1]);
                                } else if (arguments[0].equals("del")) {
                                    delWhitelist(source,arguments[1]);
                                }else {
                                    sendUsageMessage(source, "all");
                                }
                            }else {
                                sendUsageMessage(source, "all");
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .build();

        return new BrigadierCommand(helloNode);
    }
    public void sendUsageMessage(CommandSource source, String subcommand) {
        String usage = switch (subcommand) {
//            case "all" -> "/mywl add/del/on/off <player>";
            case "all" -> "/mywl add/del <player>";
            case "add" -> "/mywl add <player>";
            case "del" -> "/mywl del <player>";
            default -> "";
        };

        if (!usage.isEmpty()) {
            Component message = Component.text("Usage:", NamedTextColor.RED)
                    .append(Component.text(" " + usage, NamedTextColor.WHITE));
            source.sendMessage(message);
        }
    }
    public boolean isWhitelisted(Player player) {
        try {
            openConnection();

            UUID uuid = player.getUniqueId();
            String tableName = config.getProperty("table");

            // Check if the player is already in the whitelist
            String selectQuery = "SELECT * FROM `" + tableName + "` WHERE `UUID`=?";
            try (PreparedStatement selectStatement = connection.prepareStatement(selectQuery)) {
                selectStatement.setString(1, uuid.toString());
                ResultSet rs = selectStatement.executeQuery();

                if (rs.next()) {
                    // Player is already in the whitelist, update username if necessary
                    String updateQuery = "UPDATE `" + tableName + "` SET `user`=? WHERE `UUID`=?";
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                        updateStatement.setString(1, player.getUsername());
                        updateStatement.setString(2, uuid.toString());
                        updateStatement.executeUpdate();
                    }
                    return true;
                }
            }

            // Check if there is an entry for the player without UUID
            String selectNullQuery = "SELECT * FROM `" + tableName + "` WHERE `user`=? AND `UUID` IS NULL";
            try (PreparedStatement selectNullStatement = connection.prepareStatement(selectNullQuery)) {
                selectNullStatement.setString(1, player.getUsername());
                ResultSet rs2 = selectNullStatement.executeQuery();

                if (!rs2.next()) {
                    // Player is not in the whitelist
                    return false;
                }

                // Update the entry with the player's UUID
                String updateUuidQuery = "UPDATE `" + tableName + "` SET `UUID`=? WHERE `user`=?";
                try (PreparedStatement updateUuidStatement = connection.prepareStatement(updateUuidQuery)) {
                    updateUuidStatement.setString(1, uuid.toString());
                    updateUuidStatement.setString(2, player.getUsername());
                    updateUuidStatement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while checking whitelist", e);
        } finally {
            closeConnection();
        }

        return true;
    }
    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (Boolean.parseBoolean(config.getProperty("enabled")) && !this.isWhitelisted(player)) {
            Component kickMessage = Component.text(config.getProperty("message"));
            event.setResult(ComponentResult.denied(kickMessage));
        }
    }
}
