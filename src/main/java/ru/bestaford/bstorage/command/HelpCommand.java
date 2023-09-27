package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.User;
import ru.bestaford.bstorage.BStorageBot;
import ru.bestaford.bstorage.model.File;

public final class HelpCommand extends Command {

    public HelpCommand(BStorageBot bot) {
        super(bot);
    }

    @Override
    public void execute(User user) {
        StringBuilder result = new StringBuilder(String.format(bot.messages.getString("help"), bot.me.username()));
        result.append("\n\n");
        for (File.Type type : File.Type.values()) {
            String typeString = type.toString();
            result.append("<b>•</b> ");
            result.append(typeString.substring(0, 1).toUpperCase());
            result.append(typeString.substring(1).toLowerCase());
            result.append("\n");
        }
        bot.sendMessage(user, result.toString());
    }
}