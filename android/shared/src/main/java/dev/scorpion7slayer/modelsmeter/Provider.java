package dev.scorpion7slayer.modelsmeter;

/** Stable storage identifiers; labels are provider trademarks. */
public enum Provider {
    CHATGPT("chatgpt", "ChatGPT"),
    ANTHROPIC("anthropic", "Anthropic · Claude"),
    CURSOR("cursor", "Cursor"),
    OPENCODE_GO("opencode-go", "OpenCode Go");

    public final String id;
    public final String label;
    Provider(String id, String label) { this.id = id; this.label = label; }
    public static Provider from(String id) {
        for (Provider provider : values()) if (provider.id.equals(id)) return provider;
        return CHATGPT;
    }
    public static String[] labels() {
        String[] labels = new String[values().length];
        for (int i = 0; i < labels.length; i++) labels[i] = values()[i].label;
        return labels;
    }
}
