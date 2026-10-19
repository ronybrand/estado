package br.com.rony.spring.boot.estado.ask;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Lang {
    PT("pt"),
    EN("en");

    private final String tag;

    Lang(String tag) {
        this.tag = tag;
    }

    public static Lang fromLocale(Locale locale) {
        return "en".equalsIgnoreCase(locale.getLanguage()) ? EN : PT;
    }

    @JsonCreator
    public static Lang fromTag(String tag) {
        String language = Locale.forLanguageTag(tag).getLanguage();
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "pt" -> PT;
            case "en" -> EN;
            default -> throw new IllegalArgumentException("Unsupported answer language: " + tag);
        };
    }

    @JsonValue
    public String tag() {
        return tag;
    }
}
