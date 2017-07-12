# Listener method

EventSys has two modes of listener method invocation, first is generating a class which implements `EventListener`,
example, if the listener is called `MyListener` and the listener methods is `listen`:

Obs: **Classes was decompiled with IntelliJ Built-in FernFlower**

```java
public class listen$0 implements EventListener<MvEvent> {
    private final MyListener $instance;

    public listen$0(MyListener $instance) {
        this.$instance = $instance;
    }

    @Override
    public void onEvent(Event event, Object pluginContainer) {
        this.$instance.listen((MvEvent)event);
    }

    @Override
    public EventPriority getPriority() {
        return EventPriority.NORMAL;
    }

    @Override
    public int getPhase() {
        return -1;
    }

    @Override
    public boolean getIgnoreCancelled() {
        return false;
    }
}
```

For destruction, the class generator generates the destruction on `onEvent` method:

```java
public class listen$0 implements EventListener<MyGenericEvent<Integer>> {
    private final MyListener $instance;

    public listen$0(MyListener $instance) {
        this.$instance = $instance;
    }

    @Override
    public void onEvent(Event event, Object pluginContainer) {
        MyListener var10000 = this.$instance;
        MyGenericEvent var10001 = (MyGenericEvent)event;
        GetterProperty var10002 = event.getGetterProperty(Integer.class, "obj");
        if (var10002 != null) {
            if (var10002 != null) {
                var10000.listen2(var10001, (Integer)var10002.getValue());
            }
        }
    }

    @Override
    public EventPriority getPriority() {
        return EventPriority.NORMAL;
    }

    @Override
    public int getPhase() {
        return -1;
    }

    @Override
    public boolean getIgnoreCancelled() {
        return false;
    }
}
```

Annotation properties are also inlined on the generated class, example, given following class:

```java
@Listener(ignoreCancelled = true, priority = EventPriority.HIGHEST)
public void listen3(MyGenericEvent<Integer> event, @Name("obj") Integer i) {
    System.out.println("Value: " + i);
}
```

Generator would generate a class like that:

```java
public class listen$0 implements EventListener<MyGenericEvent<Integer>> {
    private final MyListener $instance;

    public listen$0(MyListener $instance) {
        this.$instance = $instance;
    }

    @Override
    public void onEvent(Event event, Object pluginContainer) {
        MyListener var10000 = this.$instance;
        MyGenericEvent var10001 = (MyGenericEvent)event;
        GetterProperty var10002 = event.getGetterProperty(Integer.class, "obj");
        if (var10002 != null) {
            if (var10002 != null) {
                var10000.listen3(var10001, (Integer)var10002.getValue());
            }
        }
    }

    @Override
    public EventPriority getPriority() {
        return EventPriority.HIGHEST;
    }

    @Override
    public int getPhase() {
        return -1;
    }

    @Override
    public boolean getIgnoreCancelled() {
        return true;
    }
}
```

The second way is using `Java 7 MethodHandle` (and yes, destruction is supported).

# Event classes

Given following event class:

```java
public interface MyTestEvent extends Event {

    @NotNullValue
    String getName();

    int getAmount();

    void setAmount(int amount);

    default void applyToAmount(IntUnaryOperator operator) {
        this.setAmount(operator.applyAsInt(this.getAmount()));
    }
}
```

Generator would generate an event class like that:

```java
public class MyTestEventImpl implements MyTestEvent {
    private int amount;
    private final String name;
    private final Map<String, Property> #properties = new HashMap();
    private final Map<String, Property> immutable#properties;

    public MyTestEventImpl(@Name("amount") int amount, @Name("name") String name) {
        this.immutable#properties = Collections.unmodifiableMap(this.#properties);
        Objects.requireNonNull(name);
        this.amount = amount;
        this.name = name;
        this.#properties.put("amount", new IntGSProperty.Impl(this::getAmount, this::setAmount));
        this.#properties.put("name", new GetterProperty.Impl(String.class, this::getName));
    }

    public int getAmount() {
        return this.amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getName() {
        return this.name;
    }

    public <T> T getExtension(Class<T> extensionClass) {
        return null;
    }

    @Override
    public Map<String, Property> getProperties() {
        return this.immutable#properties;
    }
    
    // Note: A lot of bridge methods are generated to call DefaultImpls of PropertyHolder class
}
```

Note that `EventGenerator` uses specialized versions of `Property` for primitive types, and generates `Objects.requireNonNull` for `@NotNullValue` annotated properties.

## Generic events

For generic events, the type information of event should be provided to event class.

Given following event interface:

```java
public interface MyGenericEvent<T> extends Event {

    @NotNullValue
    T getObj();

}
```

And following factory:

```java
<T> MyGenericEvent<T> createMyGenericEvent(@TypeParam TypeInfo<T> type, @Name("obj") T obj);
```

Following event class will be generated to `createMyGenericEvent(TypeInfo.of(String.class), "A")`:

```java
public class MyGenericEventImpl implements MyGenericEvent {
    private final Object obj;
    private final Map<String, Property> #properties = new HashMap();
    private final Map<String, Property> immutable#properties;
    private final TypeInfo<MyGenericEvent> eventTypeInfo;

    public MyGenericEventImpl(@TypeParam @Name("eventTypeInfo") TypeInfo<MyGenericEvent> eventTypeInfo, @Name("obj") Object obj) {
        this.immutable#properties = Collections.unmodifiableMap(this.#properties);
        Objects.requireNonNull(obj);
        Objects.requireNonNull(eventTypeInfo);
        this.eventTypeInfo = eventTypeInfo;
        this.obj = obj;
        this.#properties.put("obj", new Impl(Object.class, this::getObj));
    }

    public Object getObj() {
        return this.obj;
    }

    @Override
    public TypeInfo<? extends Event> getEventTypeInfo() {
        return this.eventTypeInfo;
    }

    // toString()

    public <T> T getExtension(Class<T> extensionClass) {
        return null;
    }

    @Override
    public Map<String, Property> getProperties() {
        return this.immutable#properties;
    }
}
```

Yes, you need to provide a `TypeInfo<T>` for event construction, you can also construct the event without the type information, but `dispatch` methods will only dispatch for listeners which listen to event without the type or with bound type, for example, if you construct `MyGenericEvent` without type information and `dispatch` it without `TypeInfo`, only listeners which listen to `MyGenericEvent` and `MyGenericEvent<Object>` will be invoked. If you `dispatch` the event with `TypeInfo` event will be also be dispatched correctly.

**Since 1.3**

If you use generic events, `PropertyHolder.lookup` will be called instead of `PropertyHolder.getGetterProperty`.

## Lazy generation

Events can be also lazily generated by factories, this means that event classes will not be generated before the factory is generated, they will be generated on-demand, there are currently two modes of lazy generation:

- Reflection + PropertySorter
- InvokeDynamic + Bootstrap with fallback

The first call the `EventGenerator` to generate event class and uses `PropertySorter` class to sort event arguments to follow constructor property order and call it using `Constructor` instance (Reflectively).

The second delegate the call to `FactoryBootstrap`, which implements the generation and sorting logic (and a simple cache).

**The second mode is the default mode**

#### Reflection factory

```java
public MyGenericEvent createMyGenericEvent(TypeInfo eventTypeInfo, Object obj) {
    Class eventClass = this.eventGenerator.createEventClass(TypeInfo.of(MyGenericEvent.class), Collections3.listOf(new Object[0]), Collections3.listOf(new Object[0]));
    Constructor ctr = eventClass.getDeclaredConstructors()[0];
    Object[] sorted = PropertiesSort.sort(ctr, new String[]{"eventTypeInfo", "obj"}, new Object[]{(Object)TypeInfo.builderOf(MyGenericEvent.class).of(new TypeInfo[]{eventTypeInfo}).build(), obj});
    return (MyGenericEvent)ctr.newInstance(sorted);
}
```

Bytecode:

```
public com.github.projectsandstone.eventsys.test.event.MyGenericEvent createMyGenericEvent(com.github.jonathanxd.iutils.type.TypeInfo, java.lang.Object);
descriptor: (Lcom/github/jonathanxd/iutils/type/TypeInfo;Ljava/lang/Object;)Lcom/github/projectsandstone/eventsys/test/event/MyGenericEvent;
flags: ACC_PUBLIC
Code:
  stack=10, locals=6, args_size=3
     0: aload_0
     1: getfield      #16                 // Field eventGenerator:Lcom/github/projectsandstone/eventsys/gen/event/EventGenerator;
     4: ldc           #42                 // class com/github/projectsandstone/eventsys/test/event/MyGenericEvent
     6: invokestatic  #48                 // Method com/github/jonathanxd/iutils/type/TypeInfo.of:(Ljava/lang/Class;)Lcom/github/jonathanxd/iutils/type/TypeInfo;
     9: iconst_0
    10: anewarray     #4                  // class java/lang/Object
    13: invokestatic  #54                 // Method com/github/jonathanxd/iutils/collection/Collections3.listOf:([Ljava/lang/Object;)Ljava/util/List;
    16: iconst_0
    17: anewarray     #4                  // class java/lang/Object
    20: invokestatic  #54                 // Method com/github/jonathanxd/iutils/collection/Collections3.listOf:([Ljava/lang/Object;)Ljava/util/List;
    23: invokeinterface #60,  4           // InterfaceMethod com/github/projectsandstone/eventsys/gen/event/EventGenerator.createEventClass:(Lcom/github/jonathanxd/iutils/type/TypeInfo;Ljava/util/List;Ljava/util/List;)Ljava/lang/Class;
    28: astore_3
    29: aload_3
    30: invokevirtual #66                 // Method java/lang/Class.getDeclaredConstructors:()[Ljava/lang/reflect/Constructor;
    33: iconst_0
    34: aaload
    35: astore        4
    37: aload         4
    39: iconst_2
    40: anewarray     #68                 // class java/lang/String
    43: dup
    44: iconst_0
    45: ldc           #69                 // String eventTypeInfo
    47: aastore
    48: dup
    49: iconst_1
    50: ldc           #70                 // String obj
    52: aastore
    53: iconst_2
    54: anewarray     #4                  // class java/lang/Object
    57: dup
    58: iconst_0
    59: ldc           #42                 // class com/github/projectsandstone/eventsys/test/event/MyGenericEvent
    61: invokestatic  #74                 // Method com/github/jonathanxd/iutils/type/TypeInfo.builderOf:(Ljava/lang/Class;)Lcom/github/jonathanxd/iutils/type/TypeInfoBuilder;
    64: iconst_1
    65: anewarray     #44                 // class com/github/jonathanxd/iutils/type/TypeInfo
    68: dup
    69: iconst_0
    70: aload_1
    71: aastore
    72: invokevirtual #79                 // Method com/github/jonathanxd/iutils/type/TypeInfoBuilder.of:([Lcom/github/jonathanxd/iutils/type/TypeInfo;)Lcom/github/jonathanxd/iutils/type/TypeInfoBuilder;
    75: invokevirtual #83                 // Method com/github/jonathanxd/iutils/type/TypeInfoBuilder.build:()Lcom/github/jonathanxd/iutils/type/TypeInfo;
    78: checkcast     #4                  // class java/lang/Object
    81: aastore
    82: dup
    83: iconst_1
    84: aload_2
    85: aastore
    86: invokestatic  #89                 // Method com/github/projectsandstone/eventsys/reflect/PropertiesSort.sort:(Ljava/lang/reflect/Constructor;[Ljava/lang/String;[Ljava/lang/Object;)[Ljava/lang/Object;
    89: astore        5
    91: aload         4
    93: aload         5
    95: invokevirtual #95                 // Method java/lang/reflect/Constructor.newInstance:([Ljava/lang/Object;)Ljava/lang/Object;
    98: areturn
  LocalVariableTable:
    Start  Length  Slot  Name   Signature
       89      10     5 sorted   [Ljava/lang/Object;
       35      64     4   ctr   Ljava/lang/reflect/Constructor;
       28      71     3 eventClass   Ljava/lang/Class;
        0      99     2   obj   Ljava/lang/Object;
        0      99     1 eventTypeInfo   Lcom/github/jonathanxd/iutils/type/TypeInfo;
        0      99     0  this   Lcom/github/projectsandstone/eventsys/test/factory/MyFactory$Impl;
  LineNumberTable:
    line 4: 0
    line 5: 29
    line 6: 37
    line 7: 91
MethodParameters:
  Name                           Flags
  eventTypeInfo
  obj
```

#### Bootstrap factory

```java
public MyGenericEvent createMyGenericEvent(TypeInfo eventTypeInfo, Object obj) {
    return this.eventGenerator.create<invokedynamic>(this.eventGenerator, TypeInfo.of(MyGenericEvent.class), Collections3.listOf(new Object[0]), Collections3.listOf(new Object[0]), new String[]{"eventTypeInfo", "obj"}, new Object[]{(Object)TypeInfo.builderOf(MyGenericEvent.class).of(new TypeInfo[]{eventTypeInfo}).build(), obj});
}
```

Bytecode:
```
public com.github.projectsandstone.eventsys.test.event.MyGenericEvent createMyGenericEvent(com.github.jonathanxd.iutils.type.TypeInfo, java.lang.Object);
descriptor: (Lcom/github/jonathanxd/iutils/type/TypeInfo;Ljava/lang/Object;)Lcom/github/projectsandstone/eventsys/test/event/MyGenericEvent;
flags: ACC_PUBLIC
Code:
  stack=13, locals=3, args_size=3
     0: aload_0
     1: getfield      #16                 // Field eventGenerator:Lcom/github/projectsandstone/eventsys/gen/event/EventGenerator;
     4: ldc           #86                 // class com/github/projectsandstone/eventsys/test/event/MyGenericEvent
     6: invokestatic  #38                 // Method com/github/jonathanxd/iutils/type/TypeInfo.of:(Ljava/lang/Class;)Lcom/github/jonathanxd/iutils/type/TypeInfo;
     9: iconst_0
    10: anewarray     #4                  // class java/lang/Object
    13: invokestatic  #44                 // Method com/github/jonathanxd/iutils/collection/Collections3.listOf:([Ljava/lang/Object;)Ljava/util/List;
    16: iconst_0
    17: anewarray     #4                  // class java/lang/Object
    20: invokestatic  #44                 // Method com/github/jonathanxd/iutils/collection/Collections3.listOf:([Ljava/lang/Object;)Ljava/util/List;
    23: iconst_2
    24: anewarray     #46                 // class java/lang/String
    27: dup
    28: iconst_0
    29: ldc           #87                 // String eventTypeInfo
    31: aastore
    32: dup
    33: iconst_1
    34: ldc           #88                 // String obj
    36: aastore
    37: iconst_2
    38: anewarray     #4                  // class java/lang/Object
    41: dup
    42: iconst_0
    43: ldc           #86                 // class com/github/projectsandstone/eventsys/test/event/MyGenericEvent
    45: invokestatic  #92                 // Method com/github/jonathanxd/iutils/type/TypeInfo.builderOf:(Ljava/lang/Class;)Lcom/github/jonathanxd/iutils/type/TypeInfoBuilder;
    48: iconst_1
    49: anewarray     #34                 // class com/github/jonathanxd/iutils/type/TypeInfo
    52: dup
    53: iconst_0
    54: aload_1
    55: aastore
    56: invokevirtual #97                 // Method com/github/jonathanxd/iutils/type/TypeInfoBuilder.of:([Lcom/github/jonathanxd/iutils/type/TypeInfo;)Lcom/github/jonathanxd/iutils/type/TypeInfoBuilder;
    59: invokevirtual #101                // Method com/github/jonathanxd/iutils/type/TypeInfoBuilder.build:()Lcom/github/jonathanxd/iutils/type/TypeInfo;
    62: checkcast     #4                  // class java/lang/Object
    65: aastore
    66: dup
    67: iconst_1
    68: aload_2
    69: aastore
    70: invokedynamic #104,  0            // InvokeDynamic #0:create:(Lcom/github/projectsandstone/eventsys/gen/event/EventGenerator;Lcom/github/jonathanxd/iutils/type/TypeInfo;Ljava/util/List;Ljava/util/List;[Ljava/lang/String;[Ljava/lang/Object;)Lcom/github/projectsandstone/eventsys/test/event/MyGenericEvent;
    75: areturn
  LocalVariableTable:
    Start  Length  Slot  Name   Signature
        0      76     2   obj   Ljava/lang/Object;
        0      76     1 eventTypeInfo   Lcom/github/jonathanxd/iutils/type/TypeInfo;
        0      76     0  this   Lcom/github/projectsandstone/eventsys/test/factory/MyFactory$Impl;
  LineNumberTable:
    line 6: 0
MethodParameters:
  Name                           Flags
  eventTypeInfo
  obj

BootstrapMethods:
  0: #60 invokestatic com/github/projectsandstone/eventsys/bootstrap/FactoryBootstrap.factoryBootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;
    Method arguments:
```

#### Performance

Both have similar performance (and is very good).