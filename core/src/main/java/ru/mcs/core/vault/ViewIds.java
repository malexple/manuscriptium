package ru.mcs.core.vault;

import java.text.Normalizer;
import java.util.Locale;

/** Безопасное человекочитаемое имя нового {@code .manuscriptium/views/<id>.yml}. */
final class ViewIds {

    private ViewIds() {
    }

    static String base(String displayName) {
        String ascii = Normalizer.normalize(displayName, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (ascii.isEmpty()) {
            return "view";
        }
        return ascii.length() <= 64 ? ascii : ascii.substring(0, 64).replaceAll("-+$", "");
    }
}
