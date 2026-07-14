package io.github.kgelinas.jalfnotifier.data;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


public class SearchOption {
    public String value;
    public String label;
    public SearchOption(String value, String label) {
        this.value = value;
        this.label = label;
    }
    @Override public String toString() { return label; }
}
