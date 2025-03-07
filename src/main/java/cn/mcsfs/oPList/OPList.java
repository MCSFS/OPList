package cn.mcsfs.oPList;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public final class OPList extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Set<String> allowedOps = new HashSet<>();
    private String customCommand;
    private String broadcastMessage;
    private int taskId;

    @Override
    public void onEnable() {
        // 获取服务器版本
        String version = "Unknown";
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = packageName.split("\\.");
            if (parts.length >= 4) {
                version = parts[3]; // 例如 "v1_21_R1"
            } else {
                version = Bukkit.getServer().getBukkitVersion(); // 备用方案
            }
        } catch (Exception e) {
            getLogger().warning("无法获取服务器版本，使用备用方案");
            version = Bukkit.getServer().getBukkitVersion(); // 备用方案
        }
        getLogger().info("服务器版本: " + version);

        // 注册事件和命令
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        reloadConfiguration();

        PluginCommand command = getCommand("oplist");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        // 启动定时任务
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::checkOPs, 0L, 20L);
        getLogger().info("插件已启动 v" + getDescription().getVersion());
    }

    // 新增命令监听部分
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        handleCommand(event.getSender(), event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        handleCommand(event.getPlayer(), event.getMessage());
    }

    private void handleCommand(CommandSender sender, String command) {
        String cmd = command.replace("/", "").trim();
        String[] args = cmd.split("\\s+");

        if (args.length >= 2 && args[0].equalsIgnoreCase("op")) {
            String target = args[1];
            logOPAction(sender, target);
        }
    }

    // 新增日志记录方法
    private void logOPAction(CommandSender operator, String target) {
        try {
            File logFile = new File(getDataFolder(), "op.log");
            if (!logFile.exists()) logFile.createNewFile();

            String operatorName = (operator instanceof Player) ?
                    ((Player) operator).getName() : "CONSOLE";
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date());

            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(String.format("[%s] <%s>将OP给予给<%s>\n",
                        timestamp,
                        operatorName,
                        target));
            }
        } catch (IOException e) {
            getLogger().severe("无法写入OP日志: " + e.getMessage());
        }
    }

    // 以下保持原有代码不变
    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTask(taskId);
        getLogger().info("插件已安全关闭");
    }

    private void reloadConfiguration() {
        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        allowedOps.clear();
        for (String name : config.getStringList("allowed_ops")) {
            allowedOps.add(name.toLowerCase(Locale.ROOT));
        }
        customCommand = config.getString("custom_command", "kick {player} 非法管理员权限");
        broadcastMessage = config.getString("broadcast_message", "&c玩家 &6{player} &c因非法获取OP权限已被封禁！");
    }

    private void checkOPs() {
        Set<OfflinePlayer> operators = new HashSet<>(Bukkit.getOperators());
        for (OfflinePlayer op : operators) {
            if (op.getName() == null) continue;
            String playerName = op.getName().toLowerCase(Locale.ROOT);
            if (!allowedOps.contains(playerName)) {
                handleIllegalOP(op);
            }
        }
    }

    private void handleIllegalOP(OfflinePlayer offlinePlayer) {
        try {
            // 记录原始状态
            boolean wasOp = offlinePlayer.isOp();
            String playerName = offlinePlayer.getName();

            // 移除OP权限
            offlinePlayer.setOp(false);

            // 如果玩家在线，保存数据
            if (offlinePlayer instanceof Player) {
                ((Player) offlinePlayer).saveData();
            }

            // 执行自定义命令
            if (customCommand != null && !customCommand.isEmpty()) {
                String command = customCommand
                        .replace("{player}", playerName)
                        .replace(" ", " "); // 防止命令注入

                Bukkit.getScheduler().runTask(this, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                );
            }

            // 发送广播消息
            if (broadcastMessage != null && !broadcastMessage.isEmpty()) {
                String message = broadcastMessage
                        .replace("{player}", playerName)
                        .replace("&", "§"); // 将 & 转换为 Minecraft 颜色代码

                Bukkit.broadcastMessage(message);
            }

            getLogger().warning(String.format(
                    "已处理非法OP玩家 [名称: %s, 在线: %s, 原OP状态: %s]",
                    playerName,
                    offlinePlayer.isOnline() ? "是" : "否",
                    wasOp ? "是" : "否"
            ));

        } catch (Exception e) {
            getLogger().severe("处理非法OP时出错: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            reloadConfiguration();
            sender.sendMessage("§a配置已重新加载！");
            return true;
        }
        sender.sendMessage("§c用法: /mcsfsadmin reload");
        return true;
    }

    @Override
    public void saveDefaultConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
    }
}
