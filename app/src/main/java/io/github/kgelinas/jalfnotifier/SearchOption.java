package io.github.kgelinas.jalfnotifier;

public class SearchOption {
    public String value;
    public String label;
    public SearchOption(String value, String label) {
        this.value = value;
        this.label = label;
    }
    @Override public String toString() { return label; }
}
