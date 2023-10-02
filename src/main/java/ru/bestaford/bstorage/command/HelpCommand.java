package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.User;
import ru.bestaford.bstorage.BStorageBot;
import ru.bestaford.bstorage.model.File;

import java.util.Map;

public final class HelpCommand extends Command {

    public HelpCommand(BStorageBot bot) {
        super(bot, null);
    }

    @Override
    public void execute(User user) {
        StringBuilder commands = new StringBuilder();
        for (Map.Entry<String, Command> entry : bot.commandMap.entrySet()) {
            String description = entry.getValue().description;
            if (description != null) {
                commands.append(String.format("/%s - %s", entry.getKey(), entry.getValue().description));
                commands.append("\n");
            }
        }
        StringBuilder files = new StringBuilder();
        for (File.Type type : File.Type.values()) {
            String typeString = type.toString();
            files.append("<b>•</b> ");
            files.append(typeString.substring(0, 1).toUpperCase());
            files.append(typeString.substring(1).toLowerCase());
            files.append("\n");
        }
        String result = String.format(bot.messages.getString("help"), bot.me.username(), commands.toString().trim(), files.toString().trim());
        bot.sendMessage(user, result);
    }
}