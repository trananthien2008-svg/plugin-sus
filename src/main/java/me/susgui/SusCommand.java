package me.susgui;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SusCommand implements CommandExecutor {

    private final SusGuiPlugin plugin;

    public SusCommand(SusGuiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Lệnh này chỉ dùng được trong game.");
            return true;
        }
        // Chỉ OP: kiểm tra trực tiếp để quyền cấp qua plugin permission cũng không mở được
        if (!player.isOp()) {
            player.sendMessage(SusGuiPlugin.text("&cChỉ OP mới được dùng lệnh này!"));
            return true;
        }
        new SusGui(plugin).open(player);
        return true;
    }
}
