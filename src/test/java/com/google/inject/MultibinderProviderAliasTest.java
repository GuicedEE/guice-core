package com.google.inject;

import com.google.inject.multibindings.Multibinder;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MultibinderProviderAliasTest {
    @Test void emptyExtensionPointHasNoRecursiveProviderBinding() {
        var injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() { Multibinder.newSetBinder(binder(), String.class); }
        });
        assertTrue(injector.getInstance(Key.get(new TypeLiteral<Set<String>>() {})).isEmpty());
        assertTrue(injector.getInstance(Key.get(new TypeLiteral<Collection<Provider<String>>>() {})).isEmpty());
    }

    @Test void contributionsFromMultipleModulesRemainLazyAndDistinct() {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                Multibinder.newSetBinder(binder(), String.class).addBinding().toProvider(() -> { count.incrementAndGet(); return "one"; });
            }
        }, new AbstractModule() {
            @Override protected void configure() { Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("two"); }
        });
        var providers = injector.getInstance(Key.get(new TypeLiteral<Collection<Provider<String>>>() {}));
        assertEquals(2, providers.size());
        assertEquals(0, count.get());
        assertEquals(Set.of("one", "two"), providers.stream().map(Provider::get).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, count.get());
        assertEquals(Set.of("one", "two"), injector.getInstance(Key.get(new TypeLiteral<Set<String>>() {})));
    }
}
