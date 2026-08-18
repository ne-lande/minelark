package ru.nelande.minelark.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the self-describing API manifest: reflected from the live {@code @StarlarkMethod}
 * annotations via {@link StarlarkHost#describeApi()}, so it stays in step with the jar. Doubles as a
 * coverage check that the namespaces, events, and view types are all captured.
 */
class ApiManifestTest {

    private static JsonObject manifest() {
        String json = ApiManifest.of("1.2.3", "1.21.1", StarlarkHost.describeApi()).toJson();
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void stampsVersions() {
        JsonObject root = manifest();
        assertEquals("1.2.3", root.get("minelark_version").getAsString());
        assertEquals("1.21.1", root.get("minecraft_version").getAsString());
    }

    @Test
    void listsNamespacesWithPhasesAndDocumentedMembers() {
        JsonObject namespaces = manifest().getAsJsonObject("namespaces");
        for (String expected : new String[]{"log", "recipes", "timers", "hud", "events", "prelude"}) {
            assertTrue(namespaces.has(expected), "missing namespace: " + expected);
        }
        // recipes is a server namespace; timers too.
        JsonObject recipes = namespaces.getAsJsonObject("recipes");
        assertTrue(recipes.getAsJsonArray("phases").toString().contains("server"), "recipes should be server-phase");

        // timers.after should carry its doc and a `ticks` param.
        JsonObject timers = namespaces.getAsJsonObject("timers");
        boolean foundAfter = false;
        for (var element : timers.getAsJsonArray("members")) {
            JsonObject member = element.getAsJsonObject();
            if (member.get("name").getAsString().equals("after")) {
                foundAfter = true;
                assertTrue(!member.get("doc").getAsString().isEmpty(), "after should have a doc");
                assertTrue(member.getAsJsonArray("params").toString().contains("ticks"), "after should list ticks");
            }
        }
        assertTrue(foundAfter, "timers.after should be documented");
    }

    @Test
    void capturesEventConstants() {
        JsonObject events = manifest().getAsJsonObject("namespaces").getAsJsonObject("events");
        String members = events.getAsJsonArray("members").toString();
        assertTrue(members.contains("SERVER_STARTED"), members);
        assertTrue(members.contains("USE_BLOCK"), members);   // an event added in theme #3
    }

    @Test
    void capturesCallbackViewTypes() {
        JsonObject types = manifest().getAsJsonObject("types");
        assertTrue(types.has("player"), "player type missing");
        String player = types.getAsJsonArray("player").toString();
        assertTrue(player.contains("heal"), player);    // an action verb from theme #1
        assertTrue(player.contains("count"), player);   // an inventory verb
    }

    @Test
    void markdownRenders() {
        String md = ApiManifest.of("1.0", "1.21.1", StarlarkHost.describeApi()).toMarkdown();
        assertTrue(md.contains("# Minelark API"), md.substring(0, Math.min(80, md.length())));
        assertTrue(md.contains("### recipes"), "expected a recipes section");
        assertTrue(md.contains("ctx.player"), "expected the player view type");
    }
}
