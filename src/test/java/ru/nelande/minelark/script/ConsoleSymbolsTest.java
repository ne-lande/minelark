package ru.nelande.minelark.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for the autocomplete manifest reflected from the {@code @StarlarkMethod} annotations. */
class ConsoleSymbolsTest {

    @Test
    void listsGlobalsAndNamespaceMembers() {
        String json = ConsoleSymbols.toJson(Map.of("storage", new Storage(null), "greeting", "hi"));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        List<String> globals = root.getAsJsonArray("globals").asList().stream()
                .map(element -> element.getAsString()).toList();
        assertTrue(globals.contains("storage"), globals.toString());
        assertTrue(globals.contains("greeting"), globals.toString());

        JsonObject members = root.getAsJsonObject("members");
        // A namespace object contributes its methods...
        assertTrue(members.has("storage"));
        List<String> storageMethods = members.getAsJsonArray("storage").asList().stream()
                .map(element -> element.getAsJsonObject().get("name").getAsString()).toList();
        assertTrue(storageMethods.contains("set"), storageMethods.toString());
        assertTrue(storageMethods.contains("get"), storageMethods.toString());
        assertTrue(storageMethods.contains("player"), storageMethods.toString());

        // ...a plain value (a string) does not.
        assertFalse(members.has("greeting"));
    }

    @Test
    void memberSignaturesShowParams() {
        String json = ConsoleSymbols.toJson(Map.of("storage", new Storage(null)));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String getSig = root.getAsJsonObject("members").getAsJsonArray("storage").asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(member -> member.get("name").getAsString().equals("get"))
                .map(member -> member.get("sig").getAsString())
                .findFirst().orElse("");
        assertTrue(getSig.startsWith("get(key"), getSig);
        assertTrue(getSig.contains("default"), getSig);
    }
}
