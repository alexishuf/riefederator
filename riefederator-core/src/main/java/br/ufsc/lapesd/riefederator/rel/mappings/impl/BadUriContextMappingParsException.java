package br.ufsc.lapesd.riefederator.rel.mappings.impl;

import javax.annotation.Nonnull;

import static java.lang.String.format;

public class BadUriContextMappingParsException extends ContextMappingParseException {
    private final @Nonnull String offendingUri;

    public BadUriContextMappingParsException(@Nonnull String offendingUri,
                                             @Nonnull String message) {
        super(format("Bad URI: %s. Cause: %s", offendingUri, message));
        this.offendingUri = offendingUri;
    }

    public @Nonnull String getOffendingUri() {
        return offendingUri;
    }
}
