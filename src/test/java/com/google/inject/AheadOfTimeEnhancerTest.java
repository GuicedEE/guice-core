package com.google.inject;

import com.google.inject.internal.aop.ClassBuilding;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AheadOfTimeEnhancerTest {
  private static final AtomicInteger initializations = new AtomicInteger();

  public static class Service {
    static { initializations.incrementAndGet(); }
    private final String prefix;
    public Service(String prefix) { this.prefix = prefix; }
    public String call(String value) { return prefix + value; }
    public String fail() { throw new IllegalArgumentException("original failure"); }
  }

  @Test void preparesWithoutInitializingAndRetainsRuntimeInterceptorsAndExceptions() throws Exception {
    var builder = ClassBuilding.buildAheadOfTimeEnhancerBuilder(Service.class);
    Method[] methods = builder.getEnhanceableMethods();
    var selected = new BitSet();
    for (int i = 0; i < methods.length; i++) {
      if (methods[i].getName().equals("call") || methods[i].getName().equals("fail")) selected.set(i);
    }
    var enhancer = builder.buildEnhancer(selected);
    var constructor = enhancer.apply(ClassBuilding.signature(Service.class.getConstructor(String.class)));
    assertEquals(0, initializations.get(), "Image preparation must not initialize application classes");
    assertSame(enhancer, builder.buildEnhancer((BitSet) selected.clone()));
    var calls = new AtomicInteger();
    InvocationHandler[] handlers = new InvocationHandler[selected.cardinality()];
    int index = 0;
    for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) {
      var original = enhancer.apply(ClassBuilding.signature(methods[i]));
      handlers[index++] = (instance, unused, args) -> {
        calls.incrementAndGet();
        return original.apply(instance, args);
      };
    }
    var service = (Service) constructor.apply(handlers, new Object[]{"runtime:"});
    assertEquals(1, initializations.get());
    assertEquals("runtime:value", service.call("value"));
    assertEquals("original failure", assertThrows(IllegalArgumentException.class, service::fail).getMessage());
    assertEquals(2, calls.get());
    // JVM builders use a different generated class and keep their existing strategy.
    var jvm = ClassBuilding.buildEnhancerBuilder(Service.class).buildEnhancer(selected);
    index = 0;
    for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) {
      var original = jvm.apply(ClassBuilding.signature(methods[i]));
      handlers[index++] = (instance, unused, args) -> original.apply(instance, args);
    }
    var other = (Service) jvm.apply(ClassBuilding.signature(Service.class.getConstructor(String.class)))
        .apply(handlers, new Object[]{"jvm:"});
    assertNotEquals(service.getClass(), other.getClass());
    assertEquals("jvm:value", other.call("value"));
  }
}
