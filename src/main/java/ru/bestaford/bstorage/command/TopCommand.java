package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.User;
import ru.bestaford.bstorage.BStorageBot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public final class TopCommand extends Command {

    public TopCommand(BStorageBot bot) {
        super(bot);
    }

    @Override
    public void execute(User user) throws Exception {
        List<String> tagList = new ArrayList<>();
        PreparedStatement statement = bot.connection.prepareStatement("""
                SELECT
                    *
                FROM
                    FILES
                WHERE
                    USER_ID = ?
                """);
        statement.setLong(1, user.id());
        ResultSet resultSet = bot.executeStatement(statement);
        while (resultSet.next()) {
            String tags = resultSet.getString(6);
            if (tags != null) {
                tagList.addAll(Arrays.stream(tags.split(BStorageBot.REGEX_WHITESPACES)).map(String::trim).filter(s -> !s.isBlank()).toList());
            }
        }
        Set<String> tagSet = new TreeSet<>((o1, o2) -> {
            if (o1.equals(o2)) {
                return 0;
            }
            int f1 = Collections.frequency(tagList, o1);
            int f2 = Collections.frequency(tagList, o2);
            if (f1 > f2) {
                return -1;
            }
            return 1;
        });
        tagSet.addAll(tagList);
        if (tagSet.isEmpty()) {
            bot.sendMessage(user, bot.messages.getString("top.empty"));
        } else {
            StringBuilder result = new StringBuilder(bot.messages.getString("top.list"));
            result.append("\n");
            for (String tag : tagSet) {
                result.append(String.format("\n#%s: %d", tag, Collections.frequency(tagList, tag)));
            }
            bot.sendMessage(user, result.toString());
        }
    }
}