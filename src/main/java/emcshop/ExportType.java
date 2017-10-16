package emcshop;

public enum ExportType {
    BBCODE("BBCode"), CSV("CSV");

    private final String display;

    private ExportType(String display) {
        this.display = display;
    }

    @Override
    public String toString() {
        return display;
    }
}
