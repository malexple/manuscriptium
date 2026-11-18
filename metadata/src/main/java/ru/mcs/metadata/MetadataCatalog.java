package ru.mcs.metadata;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/** Объединяет описания модулей и валидирует перекрёстные ссылки до создания UI. */
public final class MetadataCatalog {

    private final Map<String, Contribution> contributions;

    private MetadataCatalog(Map<String, Contribution> contributions) {
        this.contributions = Collections.unmodifiableMap(new LinkedHashMap<>(contributions));
    }

    public static MetadataCatalog load() {
        List<MetadataProvider> providers = ServiceLoader.load(MetadataProvider.class).stream()
                .map(ServiceLoader.Provider::get).toList();
        return from(providers);
    }

    public static MetadataCatalog from(MetadataProvider... providers) {
        return from(List.of(providers));
    }

    public static MetadataCatalog from(List<MetadataProvider> providers) {
        Map<String, Contribution> contributions = new LinkedHashMap<>();
        Map<String, MetadataDocument> documents = new LinkedHashMap<>();
        for (MetadataProvider provider : providers) {
            if (contributions.containsKey(provider.id())) {
                throw new MetadataException("Duplicate metadata provider: " + provider.id());
            }
            MetadataDocument document = MetadataParser.parse(provider.open());
            contributions.put(provider.id(), new Contribution(provider.id(), provider, document));
            documents.put(provider.id(), document);
        }
        MetadataValidator.validate(documents);
        return new MetadataCatalog(contributions);
    }

    public Contribution get(String id) {
        Contribution contribution = contributions.get(id);
        if (contribution == null) {
            throw new MetadataException("Metadata provider not found: " + id);
        }
        return contribution;
    }

    public Collection<Contribution> contributions() {
        return contributions.values();
    }

    public record Contribution(String id, MetadataProvider provider, MetadataDocument document) {
    }
}
