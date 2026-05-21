package com.datatalk.application.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Translator {

    private final MessageSource messageSource;

    public Translator(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String code, Object... args) {
        if (code == null) {
            throw new NoSuchMessageException("null");
        }
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    public String getOrDefault(String code, String fallback, Object... args) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        try {
            return get(code, args);
        } catch (NoSuchMessageException ignored) {
            return fallback;
        }
    }
}
