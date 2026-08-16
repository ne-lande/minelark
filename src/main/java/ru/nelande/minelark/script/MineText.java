package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A text component: styled, hoverable, clickable text (or a translation key). Deliberately immutable
 * so it survives Starlark's module freeze - every builder method returns a new {@code MineText}:
 *
 * <pre>{@code
 * text("Click me").color("gold").bold().click_run("/say hi").hover("runs a command")
 * translate("block.minecraft.stone").italic()
 * }</pre>
 *
 * <p>MC-agnostic: it stores plain data, and the game adapter turns it into a real
 * {@code net.minecraft.text.Text} when a message is actually sent.
 */
public final class MineText implements StarlarkValue {

    /** The 16 vanilla colour names; {@code #rrggbb} hex is also accepted by {@link #color}. */
    private static final Set<String> NAMED_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    /** A click behaviour attached to a component. */
    public enum ClickAction {RUN_COMMAND, SUGGEST_COMMAND, OPEN_URL, COPY_TO_CLIPBOARD}

    private final String literal;              // set unless this is a translation
    private final String translateKey;         // set for a translation
    private final List<MineText> translateArgs;
    private final String color;                // named or #rrggbb, or null (inherit)
    private final Boolean bold;                // tri-state (null = inherit)
    private final Boolean italic;
    private final Boolean underlined;
    private final Boolean strikethrough;
    private final Boolean obfuscated;
    private final MineText hover;              // show-text hover, or null
    private final ClickAction clickAction;     // or null
    private final String clickValue;
    private final List<MineText> extra;        // appended children

    private MineText(String literal, String translateKey, List<MineText> translateArgs, String color,
                     Boolean bold, Boolean italic, Boolean underlined, Boolean strikethrough,
                     Boolean obfuscated, MineText hover, ClickAction clickAction, String clickValue,
                     List<MineText> extra) {
        this.literal = literal;
        this.translateKey = translateKey;
        this.translateArgs = List.copyOf(translateArgs);
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.hover = hover;
        this.clickAction = clickAction;
        this.clickValue = clickValue;
        this.extra = List.copyOf(extra);
    }

    /** A plain literal component. */
    public static MineText literal(String content) {
        return new MineText(content, null, List.of(), null, null, null, null, null, null, null, null,
                null, List.of());
    }

    /** A translated component: a client-side language key with optional arguments. */
    public static MineText translate(String key, List<MineText> args) {
        return new MineText(null, key, args, null, null, null, null, null, null, null, null, null,
                List.of());
    }

    /** Coerces a script value (string or {@code MineText}) into a component. */
    static MineText coerce(Object value) {
        return value instanceof MineText text ? text : literal(Starlark.str(value));
    }

    // --- builder methods (each returns a new component) ---

    @StarlarkMethod(
            name = "color",
            doc = "Sets the colour: a vanilla name (`\"gold\"`, `\"dark_red\"`, ...) or `#rrggbb` hex.",
            parameters = {@Param(name = "value", doc = "The colour name or hex string.")})
    public MineText color(String value) throws EvalException {
        if (!NAMED_COLORS.contains(value) && !HEX.matcher(value).matches()) {
            throw new EvalException("unknown colour '" + value + "' (expected a vanilla name or #rrggbb)");
        }
        return new MineText(literal, translateKey, translateArgs, value, bold, italic, underlined,
                strikethrough, obfuscated, hover, clickAction, clickValue, extra);
    }

    @StarlarkMethod(
            name = "bold",
            doc = "Bolds the text (pass `False` to force it off).",
            parameters = {@Param(name = "on", defaultValue = "True", doc = "Whether to bold.")})
    public MineText bold(boolean on) {
        return new MineText(literal, translateKey, translateArgs, color, on, italic, underlined,
                strikethrough, obfuscated, hover, clickAction, clickValue, extra);
    }

    @StarlarkMethod(
            name = "italic",
            doc = "Italicises the text.",
            parameters = {@Param(name = "on", defaultValue = "True", doc = "Whether to italicise.")})
    public MineText italic(boolean on) {
        return new MineText(literal, translateKey, translateArgs, color, bold, on, underlined,
                strikethrough, obfuscated, hover, clickAction, clickValue, extra);
    }

    @StarlarkMethod(
            name = "underline",
            doc = "Underlines the text.",
            parameters = {@Param(name = "on", defaultValue = "True", doc = "Whether to underline.")})
    public MineText underline(boolean on) {
        return new MineText(literal, translateKey, translateArgs, color, bold, italic, on,
                strikethrough, obfuscated, hover, clickAction, clickValue, extra);
    }

    @StarlarkMethod(
            name = "strikethrough",
            doc = "Strikes through the text.",
            parameters = {@Param(name = "on", defaultValue = "True", doc = "Whether to strike through.")})
    public MineText strikethrough(boolean on) {
        return new MineText(literal, translateKey, translateArgs, color, bold, italic, underlined,
                on, obfuscated, hover, clickAction, clickValue, extra);
    }

    @StarlarkMethod(
            name = "obfuscated",
            doc = "Obfuscates the text (the scrambled, glitchy effect).",
            parameters = {@Param(name = "on", defaultValue = "True", doc = "Whether to obfuscate.")})
    public MineText obfuscated(boolean on) {
        return new MineText(literal, translateKey, translateArgs, color, bold, italic, underlined,
                strikethrough, on, hover, clickAction, clickValue, extra);
    }

    @StarlarkMethod(
            name = "hover",
            doc = "Shows tooltip text when the player hovers over this component.",
            parameters = {@Param(name = "text", doc = "The hover text (a string or another `text(...)`).")})
    public MineText hover(Object text) {
        return new MineText(literal, translateKey, translateArgs, color, bold, italic, underlined,
                strikethrough, obfuscated, coerce(text), clickAction, clickValue, extra);
    }

    @StarlarkMethod(
            name = "click_run",
            doc = "Runs a command when clicked (as the clicking player).",
            parameters = {@Param(name = "command", doc = "The command, e.g. `\"/spawn\"`.")})
    public MineText clickRun(String command) {
        return withClick(ClickAction.RUN_COMMAND, command);
    }

    @StarlarkMethod(
            name = "click_suggest",
            doc = "Puts text into the player's chat box when clicked.",
            parameters = {@Param(name = "text", doc = "The text to suggest.")})
    public MineText clickSuggest(String text) {
        return withClick(ClickAction.SUGGEST_COMMAND, text);
    }

    @StarlarkMethod(
            name = "click_url",
            doc = "Opens a URL when clicked (the client asks the player to confirm).",
            parameters = {@Param(name = "url", doc = "The URL to open.")})
    public MineText clickUrl(String url) {
        return withClick(ClickAction.OPEN_URL, url);
    }

    @StarlarkMethod(
            name = "click_copy",
            doc = "Copies text to the player's clipboard when clicked.",
            parameters = {@Param(name = "text", doc = "The text to copy.")})
    public MineText clickCopy(String text) {
        return withClick(ClickAction.COPY_TO_CLIPBOARD, text);
    }

    @StarlarkMethod(
            name = "append",
            doc = "Appends another component (or string) after this one, keeping this one's style.",
            parameters = {@Param(name = "text", doc = "The component or string to append.")})
    public MineText append(Object text) {
        List<MineText> children = new ArrayList<>(extra);
        children.add(coerce(text));
        return new MineText(literal, translateKey, translateArgs, color, bold, italic, underlined,
                strikethrough, obfuscated, hover, clickAction, clickValue, children);
    }

    private MineText withClick(ClickAction action, String value) {
        return new MineText(literal, translateKey, translateArgs, color, bold, italic, underlined,
                strikethrough, obfuscated, hover, action, value, extra);
    }

    // --- accessors for the game adapter ---

    public boolean isTranslation() {
        return translateKey != null;
    }

    public String literal() {
        return literal;
    }

    public String translateKey() {
        return translateKey;
    }

    public List<MineText> translateArgs() {
        return translateArgs;
    }

    public String colorValue() {
        return color;
    }

    public Boolean boldValue() {
        return bold;
    }

    public Boolean italicValue() {
        return italic;
    }

    public Boolean underlinedValue() {
        return underlined;
    }

    public Boolean strikethroughValue() {
        return strikethrough;
    }

    public Boolean obfuscatedValue() {
        return obfuscated;
    }

    public MineText hoverText() {
        return hover;
    }

    public ClickAction clickAction() {
        return clickAction;
    }

    public String clickValue() {
        return clickValue;
    }

    public List<MineText> extra() {
        return extra;
    }

    @Override
    public boolean isImmutable() {
        return true;
    }

    @Override
    public String toString() {
        return literal != null ? literal : "translate(" + translateKey + ")";
    }
}
