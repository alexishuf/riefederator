package br.ufsc.lapesd.riefederator.webapis.requests;

import br.ufsc.lapesd.riefederator.query.CQEndpoint;
import br.ufsc.lapesd.riefederator.query.Solution;
import com.google.errorprone.annotations.Immutable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import java.util.List;

@Immutable
public interface PagingStrategy {
    interface Pager {
        boolean atEnd();
        @Nonnull Solution apply(@Nonnull Solution solution);
        void notifyResponse(@Nonnull Response response);
        void notifyResponseEndpoint(@Nullable CQEndpoint endpoint);
    }

    @Nonnull Pager createPager();
    @Nonnull List<String> getParametersUsed();
}