package br.ufsc.lapesd.freqel.query.modifiers;

import br.ufsc.lapesd.freqel.query.endpoint.Capability;
import com.google.errorprone.annotations.Immutable;

import javax.annotation.Nonnull;

@Immutable
public interface Modifier {
    @Nonnull Capability getCapability();
}
