package ru.nelande.minelark.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-2 tests for the generated resource-pack JSON (no Minecraft needed). */
class AssetJsonTest {

    @Test
    void plainItemModel() {
        assertEquals(
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minelark:item/ruby\"}}",
                AssetJson.itemModel("ruby", false));
    }

    @Test
    void toolItemModelIsHandheld() {
        assertTrue(AssetJson.itemModel("ruby_pickaxe", true).contains("minecraft:item/handheld"),
                AssetJson.itemModel("ruby_pickaxe", true));
    }

    @Test
    void fullBlockModelAndState() {
        assertEquals(
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minelark:block/marble\"}}",
                AssetJson.cubeAllBlockModel("marble"));
        assertEquals(
                "{\"variants\":{\"\":{\"model\":\"minelark:block/marble\"}}}",
                AssetJson.simpleBlockstate("marble"));
    }

    @Test
    void slabBlockstateHasThreeTypes() {
        String json = AssetJson.slabBlockstate("marble");
        assertTrue(json.contains("\"type=bottom\":{\"model\":\"minelark:block/marble\"}"), json);
        assertTrue(json.contains("\"type=top\":{\"model\":\"minelark:block/marble_top\"}"), json);
        assertTrue(json.contains("\"type=double\":{\"model\":\"minelark:block/marble_double\"}"), json);
    }

    @Test
    void stairsBlockstateMatchesVanillaMatrix() {
        String json = AssetJson.stairsBlockstate("marble");
        // straight east, bottom: model only, no rotation/uvlock.
        assertTrue(json.contains(
                "\"facing=east,half=bottom,shape=straight\":{\"model\":\"minelark:block/marble\"}"), json);
        // north bottom straight: uvlock + y270.
        assertTrue(json.contains(
                "\"facing=north,half=bottom,shape=straight\":{\"model\":\"minelark:block/marble\",\"uvlock\":true,\"y\":270}"),
                json);
        // a top variant uses x:180.
        assertTrue(json.contains(
                "\"facing=east,half=top,shape=straight\":{\"model\":\"minelark:block/marble\",\"uvlock\":true,\"x\":180}"),
                json);
        assertTrue(json.contains("marble_inner"), json);
        assertTrue(json.contains("marble_outer"), json);
    }

    @Test
    void fenceBlockstateIsMultipartWithPostAndSides() {
        String json = AssetJson.fenceBlockstate("marble");
        assertTrue(json.startsWith("{\"multipart\":["), json);
        assertTrue(json.contains("{\"apply\":{\"model\":\"minelark:block/marble_post\"}}"), json);
        assertTrue(json.contains(
                "{\"apply\":{\"model\":\"minelark:block/marble_side\",\"uvlock\":true,\"y\":90},\"when\":{\"east\":\"true\"}}"),
                json);
    }

    @Test
    void wallBlockstateHasPostLowAndTallSides() {
        String json = AssetJson.wallBlockstate("marble");
        assertTrue(json.contains("\"when\":{\"up\":\"true\"}"), json);
        assertTrue(json.contains("marble_side_tall"), json);
        assertTrue(json.contains("\"when\":{\"north\":\"low\"}"), json);
        assertTrue(json.contains("\"when\":{\"west\":\"tall\"}"), json);
    }
}
