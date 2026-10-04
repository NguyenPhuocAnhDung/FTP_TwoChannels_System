package com.ftpsystem.common;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Quan ly Da ngon ngu (Tieng Viet / English) cho toan bo he thong
 */
public class I18nManager {
    private static I18nManager instance;
    private ResourceBundle bundle;
    private Locale currentLocale;
    private final List<Runnable> listeners = new ArrayList<>();

    private I18nManager() {
        // Mac dinh Tieng Viet
        setLocale(Locale.forLanguageTag("vi"));
    }

    public static synchronized I18nManager getInstance() {
        if (instance == null) {
            instance = new I18nManager();
        }
        return instance;
    }

    public void setLocale(Locale locale) {
        this.currentLocale = locale;
        try {
            this.bundle = ResourceBundle.getBundle("messages", currentLocale);
        } catch (Exception e) {
            // Fallback load
            this.bundle = ResourceBundle.getBundle("messages_vi");
        }
        notifyListeners();
    }

    public void toggleLanguage() {
        if ("vi".equalsIgnoreCase(currentLocale.getLanguage())) {
            setLocale(Locale.forLanguageTag("en"));
        } else {
            setLocale(Locale.forLanguageTag("vi"));
        }
    }

    public String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return "!" + key + "!";
        }
    }

    public String getString(String key, Object... args) {
        String pattern = getString(key);
        return MessageFormat.format(pattern, args);
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }
}
