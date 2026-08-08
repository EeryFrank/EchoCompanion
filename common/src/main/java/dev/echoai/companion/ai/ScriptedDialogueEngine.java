package dev.echoai.companion.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A deterministic fallback engine that never opens files, sockets, or other
 * network resources. All replies are generated from the supplied context.
 */
public final class ScriptedDialogueEngine implements DialogueEngine {
    @Override
    public CompletableFuture<DialogueResult> reply(DialogueContext context) {
        if (context == null) {
            return CompletableFuture.failedFuture(new NullPointerException("context"));
        }
        return CompletableFuture.completedFuture(DialogueResult.scripted(createReply(context)));
    }

    private String createReply(DialogueContext context) {
        String original = context.userMessage();
        String normalized = original.toLowerCase(Locale.ROOT);
        boolean chinese = containsCjk(original);

        if (containsAny(normalized, "你好", "您好", "嗨", "hello", "hey")
                || containsEnglishWord(normalized, "hi")) {
            return chinese
                    ? "你好，" + context.playerName() + "！我是你的离线伙伴。"
                    : "Hello, " + context.playerName() + "! I'm your offline companion.";
        }

        if (containsAny(normalized, "你是谁", "你叫什么", "who are you", "your name")) {
            return chinese
                    ? "我是 Echo Companion 的离线伪 AI；即使没有网络，我也能回应常见问题。"
                    : "I'm Echo Companion's offline scripted AI, available even without a network.";
        }

        if (containsAny(normalized, "帮助", "帮帮", "会什么", "help", "what can you do")) {
            return chinese
                    ? "我可以离线打招呼、读取你提供的位置和状态上下文，并记住最近几句对话。"
                    : "Offline, I can greet you, use supplied location or status context, and remember a few recent messages.";
        }

        if (containsAny(normalized, "谢谢", "多谢", "thank", "thanks")) {
            return chinese ? "不客气，我会继续陪着你。" : "You're welcome. I'll be right here.";
        }

        if (containsAny(normalized, "再见", "拜拜", "goodbye", "bye", "see you")) {
            return chinese ? "再见，冒险顺利！" : "Goodbye, and have a safe adventure!";
        }

        if (containsAny(normalized, "我在哪", "在哪里", "位置", "where am i", "location")) {
            return locationReply(context.attributes(), chinese);
        }

        if (containsAny(normalized, "状态", "生命", "血量", "天气", "时间", "status", "health", "weather", "time")) {
            return statusReply(context.attributes(), chinese);
        }

        String rememberedTopic = lastUserTopic(context.history());
        if (rememberedTopic != null) {
            return chinese
                    ? "我记得你刚才提到“" + rememberedTopic + "”。离线模式理解有限，但我们可以继续聊这个。"
                    : "I remember you mentioned \"" + rememberedTopic + "\". My offline understanding is limited, but we can continue with that.";
        }

        return chinese
                ? "我现在处于离线伪 AI 模式。你可以问候我，或询问位置、状态和帮助。"
                : "I'm currently in offline scripted mode. Try a greeting, or ask about location, status, or help.";
    }

    private static String locationReply(Map<String, String> attributes, boolean chinese) {
        String dimension = findAttribute(attributes, "dimension", "维度");
        String biome = findAttribute(attributes, "biome", "生物群系");
        String position = findAttribute(attributes, "position", "坐标", "pos");

        if (dimension == null && biome == null && position == null) {
            return chinese
                    ? "当前对话没有位置上下文；等游戏把维度、生物群系或坐标传给我后，我就能告诉你。"
                    : "No location context was supplied. I can report it when the game provides a dimension, biome, or position.";
        }

        if (chinese) {
            return "当前位置：" + joinContext(dimension, biome, position, "；") + "。";
        }
        return "Current location: " + joinContext(dimension, biome, position, "; ") + ".";
    }

    private static String statusReply(Map<String, String> attributes, boolean chinese) {
        String health = findAttribute(attributes, "health", "生命", "血量");
        String weather = findAttribute(attributes, "weather", "天气");
        String time = findAttribute(attributes, "time", "时间");

        if (health == null && weather == null && time == null) {
            return chinese
                    ? "当前对话没有状态数据；游戏提供生命、天气或时间后我才能读取。"
                    : "No status data was supplied. I need health, weather, or time context from the game.";
        }

        if (chinese) {
            return "当前状态：" + joinContext(health, weather, time, "；") + "。";
        }
        return "Current status: " + joinContext(health, weather, time, "; ") + ".";
    }

    private static String findAttribute(Map<String, String> attributes, String... acceptedKeys) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            for (String acceptedKey : acceptedKeys) {
                if (entry.getKey().equalsIgnoreCase(acceptedKey)) {
                    return entry.getKey() + "=" + entry.getValue();
                }
            }
        }
        return null;
    }

    private static String joinContext(String first, String second, String third, String separator) {
        StringBuilder result = new StringBuilder();
        appendContext(result, first, separator);
        appendContext(result, second, separator);
        appendContext(result, third, separator);
        return result.toString();
    }

    private static void appendContext(StringBuilder target, String value, String separator) {
        if (value == null) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(separator);
        }
        target.append(value);
    }

    private static String lastUserTopic(List<DialogueMessage> history) {
        for (int index = history.size() - 1; index >= 0; index--) {
            DialogueMessage message = history.get(index);
            if (message.role() == DialogueRole.USER) {
                return abbreviate(message.content(), 48);
            }
        }
        return null;
    }

    private static String abbreviate(String value, int maxLength) {
        String oneLine = value.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= maxLength) {
            return oneLine;
        }
        return oneLine.substring(0, maxLength - 1) + "…";
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsEnglishWord(String value, String word) {
        int fromIndex = 0;
        while (fromIndex < value.length()) {
            int match = value.indexOf(word, fromIndex);
            if (match < 0) {
                return false;
            }
            int before = match - 1;
            int after = match + word.length();
            boolean startsAtBoundary = before < 0 || !Character.isLetterOrDigit(value.charAt(before));
            boolean endsAtBoundary = after >= value.length() || !Character.isLetterOrDigit(value.charAt(after));
            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
            fromIndex = match + word.length();
        }
        return false;
    }

    private static boolean containsCjk(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x3400 && codePoint <= 0x9FFF);
    }
}
