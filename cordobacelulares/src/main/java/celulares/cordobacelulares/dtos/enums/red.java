package celulares.cordobacelulares.dtos.enums;

public enum red {
    _4G("4G"),
    _5G("5G");

    private final String displayName;

    red(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}