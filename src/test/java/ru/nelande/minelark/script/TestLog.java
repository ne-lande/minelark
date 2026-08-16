package ru.nelande.minelark.script;

import java.util.ArrayList;
import java.util.List;

/** A {@link ScriptLog} that records everything, for assertions in tests. */
final class TestLog implements ScriptLog {
    final List<Level> levels = new ArrayList<>();
    final List<String> messages = new ArrayList<>();

    @Override
    public void log(Level level, String message) {
        levels.add(level);
        messages.add(message);
    }

    boolean anyMessageContains(String needle) {
        return messages.stream().anyMatch(m -> m.contains(needle));
    }

    /** Returns the level of the first message containing {@code needle}, or null. */
    Level levelOfMessageContaining(String needle) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).contains(needle)) {
                return levels.get(i);
            }
        }
        return null;
    }
}
