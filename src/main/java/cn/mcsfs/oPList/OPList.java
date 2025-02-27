package cn.mcsfs.oPList;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public final class OPList extends JavaPlugin {

    private FileConfiguration config;
    private Set<String> allowedOps = new HashSet<>();
    private String customCommand;
    private String broadcastMessage; // 新增广播消息字段
    private int taskId;

    @Override
    public void onEnable() {
        // 确保配置文件生成
        saveDefaultConfig();
        reloadConfiguration();

        // 安全注册命令
        PluginCommand command = getCommand("mcsfsadmin");
        if (command != null) {
            command.setExecutor(this);
            getLogger().info("命令注册成功");
        } else {
            getLogger().severe("命令注册失败，请检查plugin.yml配置！");
        }

        // 启动定时任务
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::checkOPs, 0L, 20L);
        getLogger().log(Level.INFO, "插件已启动 v{0}", getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTask(taskId);
        getLogger().info("插件已安全关闭");
    }

    private void reloadConfiguration() {
        // 重新加载配置文件
        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));

        // 加载允许的OP名单（全小写）
        allowedOps.clear();
        for (String name : config.getStringList("allowed_ops")) {
            allowedOps.add(name.toLowerCase(Locale.ROOT));
        }

        // 加载自定义命令（带默认值）
        customCommand = config.getString("custom_command", "kick {player} 非法管理员权限");

        // 加载广播消息（带默认值）
        broadcastMessage = config.getString("broadcast_message", "&c玩家 &6{player} &c因非法获取OP权限已被封禁！");

        getLogger().info("已加载 " + allowedOps.size() + " 个合法OP");
        getLogger().info("自定义命令模板: " + customCommand);
        getLogger().info("广播消息模板: " + broadcastMessage);
    }

    private void checkOPs() {
        // 获取所有OP玩家（包括离线）
        Set<OfflinePlayer> operators = new HashSet<>(Bukkit.getOperators());

        for (OfflinePlayer op : operators) {
            // 跳过无效玩家
            if (op.getName() == null) continue;

            String playerName = op.getName().toLowerCase(Locale.ROOT);

            // 检查是否在白名单
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