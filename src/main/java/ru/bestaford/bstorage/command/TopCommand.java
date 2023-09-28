package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import ru.bestaford.bstorage.BStorageBot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public final class TopCommand extends Command {

    public static final int ITEMS_ON_PAGE = 10;

    public TopCommand(BStorageBot bot) {
        super(bot);
    }

    @Override
    public void execute(User user) throws Exception {
        send(user, 0);
    }

    public void send(User user, int offset) throws Exception {
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
        List<String> result = new ArrayList<>(tagSet);
        if (result.isEmpty()) {
            bot.sendMessage(user, bot.messages.getString("top.empty"));
            return;
        }
        List<String> page = new ArrayList<>();
        for (int i = offset; page.size() < ITEMS_ON_PAGE; i++) {
            if (i >= result.size()) {
                break;
            }
            page.add(result.get(i));
        }
        StringBuilder text = new StringBuilder(bot.messages.getString("top.list"));
        text.append("\n");
        for (String tag : page) {
            text.append(String.format("\n#%s: %d", tag, Collections.frequency(tagList, tag)));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> buttonList = new ArrayList<>();
        if (offset + page.size() > ITEMS_ON_PAGE) {
            buttonList.add(new InlineKeyboardButton("⬅️️").callbackData(String.valueOf(offset - ITEMS_ON_PAGE)));
        }
        if (result.size() > offset + page.size()) {
            buttonList.add(new InlineKeyboardButton("➡️").callbackData(String.valueOf(offset + ITEMS_ON_PAGE)));
        }
        markup.addRow(buttonList.toArray(new InlineKeyboardButton[0]));
        bot.executeAsyncBotRequest(new SendMessage(user.id(), text.toString()).replyMarkup(markup));
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery) throws Exception {
        send(callbackQuery.from(), Integer.parseInt(callbackQuery.data()));
        bot.executeAsyncBotRequest(new AnswerCallbackQuery(callbackQuery.id()));
    }
}