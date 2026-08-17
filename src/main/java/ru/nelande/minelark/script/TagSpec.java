package ru.nelande.minelark.script;

import java.util.List;

/**
 * An explicit tag declaration from the {@code tags} namespace: adds {@code members} to a tag of the
 * given registry {@code kind}. Merged with the tags derived from {@code item()}/{@code block()} specs
 * and written into the generated data pack.
 *
 * @param kind    the tag folder: {@code item}, {@code block}, {@code fluid}, or {@code entity_type}
 * @param tag     the full tag id ({@code namespace:path}; a bare name uses the conventional {@code c:})
 * @param members the member ids (each {@code namespace:path}, or {@code #tag} to include another tag)
 */
public record TagSpec(String kind, String tag, List<String> members) {
}
