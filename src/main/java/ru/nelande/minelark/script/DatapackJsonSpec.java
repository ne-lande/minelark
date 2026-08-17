package ru.nelande.minelark.script;

/**
 * An arbitrary JSON file to write into the generated data pack, declared by {@code datapack.json(...)}.
 *
 * @param path the file path relative to the pack root (e.g. {@code data/minelark/advancement/root.json})
 * @param json the JSON content
 */
public record DatapackJsonSpec(String path, String json) {
}
