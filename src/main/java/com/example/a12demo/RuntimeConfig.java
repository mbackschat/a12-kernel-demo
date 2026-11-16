package com.example.a12demo;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mgmtp.a12.kernel.md.rt.api.IDocumentDynamicServiceConfig;
import com.mgmtp.a12.kernel.md.rt.api.ILabelProvider;
import com.mgmtp.a12.kernel.md.rt.api.IModelCode;
import com.mgmtp.a12.kernel.md.rt.api.IModelCodeCache;

/** Runtime settings and compiled-model storage owned by one demo service. */
public final class RuntimeConfig implements IDocumentDynamicServiceConfig, IModelCodeCache {
    private final Map<String, IModelCode> compiledModels = new HashMap<>();

    @Override
    public IModelCodeCache getCache() {
        return this;
    }

    @Override
    public synchronized IModelCode getModelCode(String modelId) {
        return compiledModels.get(Objects.requireNonNull(modelId));
    }

    @Override
    public synchronized void addModelCode(String modelId, IModelCode compiledModel) {
        compiledModels.put(Objects.requireNonNull(modelId), Objects.requireNonNull(compiledModel));
    }

    @Override
    public Optional<ILabelProvider> getLabelProvider() {
        return Optional.empty();
    }

    // Kernel 30 requires this method; kernel 31 removed it from IDocumentDynamicServiceConfig.
    // Retaining it lets the same adapter serve the demo's 2025.06 and 2026.06 release branches.
    public Optional<String> getVariant() {
        return Optional.empty();
    }
}
