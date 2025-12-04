package com.daytonjwatson.communism.utils;

import org.bukkit.ChatColor;

public class Utils {

        public static final String PREFIX = ChatColor.DARK_RED + "" + ChatColor.BOLD + "☭ "
                        + ChatColor.RED + "Communism " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY;

        public static String color(String string) {
                return ChatColor.translateAlternateColorCodes('&', string);
        }

        public static String prefix(String message) {
                return PREFIX + ChatColor.translateAlternateColorCodes('&', message);
        }
}
