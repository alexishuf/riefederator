package br.ufsc.lapesd.riefederator.federation.spec.source;

import br.ufsc.lapesd.riefederator.federation.spec.FederationSpecException;
import br.ufsc.lapesd.riefederator.util.DictTree;

import javax.annotation.Nonnull;

public class SourceLoadException extends FederationSpecException {
    public SourceLoadException(@Nonnull String message, @Nonnull DictTree tree) {
        super(message, tree);
    }

    public SourceLoadException(@Nonnull String message, Throwable cause, @Nonnull DictTree tree) {
        super(message, cause, tree);
    }
}
