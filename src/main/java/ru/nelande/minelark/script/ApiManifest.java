package ru.nelande.minelark.script;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A complete, self-describing snapshot of Minelark's scripting API, reflected at runtime from the very
 * {@code @StarlarkMethod} / {@code @Param} annotations the scripts see - so it can never drift from the
 * jar it was built into. This is the answer to cross-version doc staleness: the guides live on the
 * website, but the exact API of a given build comes from that build.
 *
 * <p>Rendered to JSON (tooling) and Markdown (humans), stamped with the mod and Minecraft versions. The
 * versions are passed in, so this class stays free of Minecraft types and unit-testable. The phase
 * environments are supplied by {@link StarlarkHost#describeApi()}; the fixed event and view types are
 * reflected here.
 */
public final class ApiManifest {

    /** One lifecycle phase's predeclared surface: its namespaces, and its top-level builtin holders. */
    public record Phase(String name, Map<String, Object> namespaces, Map<String, Object> topLevel) {
    }

    private record ParamDoc(String name, String def, String doc) {
    }

    private record Member(String name, String sig, String doc, boolean field, List<ParamDoc> params) {
    }

    private static final class Section {
        final Set<String> phases = new LinkedHashSet<>();
        boolean topLevel;
        List<Member> members = List.of();
    }

    private final String minelarkVersion;
    private final String minecraftVersion;
    private final String generated;
    /** Top-level builtins and namespaces, keyed by name (""-free; top-level holders keep their label). */
    private final Map<String, Section> sections = new LinkedHashMap<>();
    /** The view types handed to callbacks ({@code ctx.player} etc.), keyed by a short name. */
    private final Map<String, List<Member>> types = new LinkedHashMap<>();

    private ApiManifest(String minelarkVersion, String minecraftVersion) {
        this.minelarkVersion = minelarkVersion;
        this.minecraftVersion = minecraftVersion;
        this.generated = Instant.now().toString();
    }

    /** Reflects the given phases (plus the fixed events and view types) into a manifest. */
    public static ApiManifest of(String minelarkVersion, String minecraftVersion, List<Phase> phases) {
        ApiManifest manifest = new ApiManifest(minelarkVersion, minecraftVersion);
        for (Phase phase : phases) {
            phase.namespaces().forEach((name, value) -> manifest.section(name, phase.name(), false, value.getClass()));
            phase.topLevel().forEach((label, value) -> manifest.section(label, phase.name(), true, value.getClass()));
        }
        // The event constants live on EventNamespace (annotated struct fields), reached as
        // events.<namespace>.<EVENT>; merge them into the `events` section so they are documented too.
        manifest.mergeMembers("events", EventNamespace.class);
        // The typed views handed to callbacks.
        manifest.types.put("player", membersOf(PlayerView.class));
        manifest.types.put("level", membersOf(LevelView.class));
        manifest.types.put("entity", membersOf(EntityView.class));
        manifest.types.put("item", membersOf(ItemStackView.class));
        manifest.types.put("command_source", membersOf(CommandSourceView.class));
        return manifest;
    }

    private void section(String name, String phase, boolean topLevel, Class<?> cls) {
        Section section = sections.computeIfAbsent(name, k -> new Section());
        section.phases.add(phase);
        section.topLevel = topLevel;
        if (section.members.isEmpty()) {
            section.members = membersOf(cls);
        }
    }

    /** Appends any members of {@code cls} not already listed under {@code name} (dedupe by member name). */
    private void mergeMembers(String name, Class<?> cls) {
        Section section = sections.get(name);
        if (section == null) {
            return;
        }
        Set<String> present = new TreeSet<>();
        section.members.forEach(m -> present.add(m.name()));
        List<Member> merged = new ArrayList<>(section.members);
        for (Member member : membersOf(cls)) {
            if (present.add(member.name())) {
                merged.add(member);
            }
        }
        merged.sort(Comparator.comparing(Member::name));
        section.members = merged;
    }

    // --- rendering ---

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("minelark_version", minelarkVersion);
        root.addProperty("minecraft_version", minecraftVersion);
        root.addProperty("generated", generated);

        JsonObject namespaces = new JsonObject();
        sections.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> namespaces.add(entry.getKey(), sectionJson(entry.getValue())));
        root.add("namespaces", namespaces);

        JsonObject typeJson = new JsonObject();
        types.forEach((name, members) -> typeJson.add(name, membersJson(members)));
        root.add("types", typeJson);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static JsonObject sectionJson(Section section) {
        JsonObject json = new JsonObject();
        json.addProperty("top_level", section.topLevel);
        JsonArray phases = new JsonArray();
        section.phases.forEach(phases::add);
        json.add("phases", phases);
        json.add("members", membersJson(section.members));
        return json;
    }

    private static JsonArray membersJson(List<Member> members) {
        JsonArray array = new JsonArray();
        for (Member member : members) {
            JsonObject json = new JsonObject();
            json.addProperty("name", member.name());
            json.addProperty("sig", member.sig());
            json.addProperty("doc", member.doc());
            json.addProperty("field", member.field());
            JsonArray params = new JsonArray();
            for (ParamDoc param : member.params()) {
                JsonObject p = new JsonObject();
                p.addProperty("name", param.name());
                if (!param.def().isEmpty()) {
                    p.addProperty("default", param.def());
                }
                p.addProperty("doc", param.doc());
                params.add(p);
            }
            json.add("params", params);
            array.add(json);
        }
        return array;
    }

    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# Minelark API (this build)\n\n");
        md.append("- Minelark version: `").append(minelarkVersion).append("`\n");
        md.append("- Minecraft version: `").append(minecraftVersion).append("`\n");
        md.append("- Generated: ").append(generated).append("\n\n");
        md.append("Reflected from this jar's annotations, so it matches exactly what these scripts can do.\n\n");

        md.append("## Namespaces and builtins\n\n");
        sections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Section section = entry.getValue();
            String title = entry.getKey().isEmpty() ? "(top level)" : entry.getKey();
            md.append("### ").append(title);
            if (section.topLevel) {
                md.append(" (top-level functions)");
            }
            md.append("\n\n_Phases: ").append(String.join(", ", section.phases)).append("_\n\n");
            appendMembers(md, section.members);
        });

        md.append("## Callback value types\n\n");
        types.forEach((name, members) -> {
            md.append("### ctx.").append(name).append("\n\n");
            appendMembers(md, members);
        });
        return md.toString();
    }

    private static void appendMembers(StringBuilder md, List<Member> members) {
        for (Member member : members) {
            md.append("- `").append(member.sig()).append("`");
            if (!member.doc().isEmpty()) {
                md.append(" - ").append(member.doc());
            }
            md.append('\n');
        }
        md.append('\n');
    }

    // --- reflection ---

    /** The {@code @StarlarkMethod} members of a class, sorted by name, deduped. */
    private static List<Member> membersOf(Class<?> cls) {
        List<Member> members = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        for (Method method : cls.getMethods()) {
            StarlarkMethod annotation = method.getAnnotation(StarlarkMethod.class);
            if (annotation == null || !seen.add(annotation.name())) {
                continue;
            }
            List<ParamDoc> params = new ArrayList<>();
            for (Param param : annotation.parameters()) {
                params.add(new ParamDoc(param.name(), param.defaultValue(), param.doc()));
            }
            members.add(new Member(annotation.name(), signature(annotation), annotation.doc(),
                    annotation.structField(), params));
        }
        members.sort(Comparator.comparing(Member::name));
        return members;
    }

    /** A display signature, e.g. {@code get(key, default=None)} - or just the name for a struct field. */
    private static String signature(StarlarkMethod method) {
        if (method.structField()) {
            return method.name();
        }
        StringBuilder builder = new StringBuilder(method.name()).append('(');
        Param[] params = method.parameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(params[i].name());
            if (!params[i].defaultValue().isEmpty()) {
                builder.append('=').append(params[i].defaultValue());
            }
        }
        return builder.append(')').toString();
    }
}
