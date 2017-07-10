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

For generic events, a new class is generated for each parameter type combination, this means that implementation class of `MyEvent<String>` is not the same implementation class of `MyEvent<Integer>`, this allow EventSys to infer the type of the event, and then, to dispatch it to listeners which listen to generic events (type can be also provided in `dispatch` methods using `TypeInfo`).

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
public class MyGenericEvent_of_java_lang_String__Impl implements MyGenericEvent<String> {
    private final String obj;
    private final Map<String, Property> #properties = new HashMap();
    private final Map<String, Property> immutable#properties;

    public MyGenericEvent_of_java_lang_String__Impl(@Name("obj") String obj) {
        this.immutable#properties = Collections.unmodifiableMap(this.#properties);
        Objects.requireNonNull(obj);
        this.obj = obj;
        this.#properties.put("obj", new Impl(String.class, this::getObj));
    }

    public Object getObj() {
        return this.obj;
    }

    public String getObj() {
        return (String)Objects.requireNonNull(this.obj);
    }

    public <T> T getExtension(Class<T> extensionClass) {
        return null;
    }

    @Override
    public Map<String, Property> getProperties() {
        return this.immutable#properties;
    }
}
```

And following event class will be generated to `createMyGenericEvent(TypeInfo.of(Integer.class), 9)`:

```java
public class MyGenericEvent_of_java_lang_Integer__Impl implements MyGenericEvent<Integer> {
    private final Integer obj;
    private final Map<String, Property> #properties = new HashMap();
    private final Map<String, Property> immutable#properties;

    public MyGenericEvent_of_java_lang_Integer__Impl(@Name("obj") Integer obj) {
        this.immutable#properties = Collections.unmodifiableMap(this.#properties);
        Objects.requireNonNull(obj);
        this.obj = obj;
        this.#properties.put("obj", new Impl(Integer.class, this::getObj));
    }

    public Object getObj() {
        return this.obj;
    }

    public Integer getObj() {
        return (Integer)Objects.requireNonNull(this.obj);
    }

    public <T> T getExtension(Class<T> extensionClass) {
        return null;
    }

    @Override
    public Map<String, Property> getProperties() {
        return this.immutable#properties;
    }
}
```

Yes, you need to provide a `TypeInfo<T>` for event construction, you can also construct the event without the type information, but `dispatch` methods will only dispatch for listeners which listen to event without the type or with bound type, for example, if you construct `MyGenericEvent` without type information and `dispatch` it without `TypeInfo`, only listeners which listen to `MyGenericEvent` and `MyGenericEvent<Object>` will be invoked. If you `dispatch` the event with `TypeInfo`, listeners which listen to specific event with same generic information as provided by `TypeInfo` *may* be invoked.

Note the *italic* *may* word, listeners like that:

```java
@Listener
public void listen(MyGenericEvent<String> s) {
    // ...
}
```

Will be invoked, but a listener like that:

```java
@Listener
public void listen(MyGenericEvent<String> s, @Name("obj") String obj) {
    
}
```

Will **not** be invoked because `MyGenericEvent` does not have a property `obj` with `String` type, it only have a property `obj` with `Object` type.
 
If you look in generated specialized class, the property is registered only for `specialized type`, and is not registered for `Object` (which is the bound of type parameter), this happens to allow destruction to work with this type of `listener` (this above), but if you don't provide a type, the property will be registered for `Object` type, and `PropertyHolder` only lookup for properties with `same type` or a super type.

But a listener like that:

```java
@Listener
public void listen(MyGenericEvent<String> s, @Name("obj") @Erased String obj) {
    
}
```

Will be invoked, because `Erased` marks the property as erased, the event generator will call `PropertyHolder.lookup(Class<?> type, String name)` which will lookup for the property with `obj` name and only return if the value is assignable to `type` (in this case, `String`). The difference is that without `@Erased` the `PropertyHolder.getProperty` will be called, which only lookup for exact property type or super-type (regardless the value type).

### Factory of generic event

Factory of generic events have a little cost, instead of generating the event class and them invoking the constructor, factory of generic events need to generate when the factory method is called because the type information is provided as an argument instead of as a static information, currently we have two modes:

- Reflection + PropertySorter
- InvokeDynamic + Bootstrap with fallback

The first call the `EventGenerator` to generate event class and uses `PropertySorter` class to sort event arguments to follow constructor property order and call it using `Constructor` instance (Reflectively).

The second delegate the call to `FactoryBootstrap`, which implements the generation and sorting logic (and a simple cache).

**The second mode is the default mode**

#### Reflection factory

```java
public MyGenericEvent createMyGenericEvent(TypeInfo arg0, Object obj) {
    Class eventClass = this.eventGenerator.createEventClass(TypeInfo.builderOf(MyGenericEvent.class).of(arg0).build(), Collections3.listOf(), Collections3.listOf());
    Constructor ctr = eventClass.getDeclaredConstructors()[0];
    Object[] sorted = PropertiesSort.sort(ctr, new String[]{"obj"}, new Object[]{obj});
    return (MyGenericEvent)ctr.newInstance(sorted);
}
```

Bytecode:

```
public com.github.projectsandstone.eventsys.test.event.MyGenericEvent createMyGenericEvent(com.github.jonathanxd.iutils.type.TypeInfo, java.lang.Object) {
  desc: (Lcom/github/jonathanxd/iutils/type/TypeInfo;Ljava/lang/Object;)Lcom/github/projectsandstone/eventsys/test/event/MyGenericEvent; 
  maxStack: 6, maxLocals: 6 
  Label_0:
   LINE 4 -> Label_0
    aload 0
    getfield com.github.projectsandstone.eventsys.test.factory.MyFactory$Impl.eventGenerator (type: com.github.projectsandstone.eventsys.gen.event.EventGenerator)
    ldc Lcom/github/projectsandstone/eventsys/test/event/MyGenericEvent;              // type: java.lang.Class
    invokestatic com.github.jonathanxd.iutils.type.TypeInfo.builderOf(java.lang.Class)com.github.jonathanxd.iutils.type.TypeInfoBuilder (ownerIsInterface: false)
    iconst_1
    anewarray com.github.jonathanxd.iutils.type.TypeInfo
    dup
    iconst_0
    aload 1
    aastore
    invokevirtual com.github.jonathanxd.iutils.type.TypeInfoBuilder.of(com.github.jonathanxd.iutils.type.TypeInfo[])com.github.jonathanxd.iutils.type.TypeInfoBuilder (ownerIsInterface: false)
    invokevirtual com.github.jonathanxd.iutils.type.TypeInfoBuilder.build()com.github.jonathanxd.iutils.type.TypeInfo (ownerIsInterface: false)
    iconst_0
    anewarray java.lang.Object
    invokestatic com.github.jonathanxd.iutils.collection.Collections3.listOf(java.lang.Object[])java.util.List (ownerIsInterface: false)
    iconst_0
    anewarray java.lang.Object
    invokestatic com.github.jonathanxd.iutils.collection.Collections3.listOf(java.lang.Object[])java.util.List (ownerIsInterface: false)
    invokeinterface com.github.projectsandstone.eventsys.gen.event.EventGenerator.createEventClass(com.github.jonathanxd.iutils.type.TypeInfo, java.util.List, java.util.List)java.lang.Class (ownerIsInterface: true)
  Label_1:
    astore 3
  Label_2:
   LINE 5 -> Label_2
    aload 3
    invokevirtual java.lang.Class.getDeclaredConstructors()java.lang.reflect.Constructor[] (ownerIsInterface: false)
    iconst_0
    aaload
  Label_3:
    astore 4
  Label_4:
   LINE 6 -> Label_4
    aload 4
    iconst_1
    anewarray java.lang.String
    dup
    iconst_0
    ldc "obj"              // type: java.lang.String
    aastore
    iconst_1
    anewarray java.lang.Object
    dup
    iconst_0
    aload 2
    aastore
    invokestatic com.github.projectsandstone.eventsys.reflect.PropertiesSort.sort(java.lang.reflect.Constructor, java.lang.String[], java.lang.Object[])java.lang.Object[] (ownerIsInterface: false)
  Label_5:
    astore 5
  Label_6:
   LINE 7 -> Label_6
    aload 4
    aload 5
    invokevirtual java.lang.reflect.Constructor.newInstance(java.lang.Object[])java.lang.Object (ownerIsInterface: false)
    areturn
  Label_7:
  LocalVariables {
    index: 5, name: sorted, start: Label_5, end: Label_7, type: java.lang.Object[], signature: null
    index: 4, name: ctr, start: Label_3, end: Label_7, type: java.lang.reflect.Constructor, signature: null
    index: 3, name: eventClass, start: Label_1, end: Label_7, type: java.lang.Class, signature: null
    index: 2, name: obj, start: Label_0, end: Label_7, type: java.lang.Object, signature: null
    index: 1, name: arg0, start: Label_0, end: Label_7, type: com.github.jonathanxd.iutils.type.TypeInfo, signature: null
    index: 0, name: this, start: Label_0, end: Label_7, type: com.github.projectsandstone.eventsys.test.factory.MyFactory$Impl, signature: null
  }
}

```

#### Bootstrap factory

```java
public MyGenericEvent createMyGenericEvent(TypeInfo arg0, Object obj) {
    return this.eventGenerator.create<invokedynamic>(this.eventGenerator, TypeInfo.builderOf(MyGenericEvent.class).of(arg0).build(), Collections3.listOf(), Collections3.listOf(), new String[]{"obj"}, new Object[]{obj});
}
```

Bytecode:
```
public com.github.projectsandstone.eventsys.test.event.MyGenericEvent createMyGenericEvent(com.github.jonathanxd.iutils.type.TypeInfo, java.lang.Object) {
  desc: (Lcom/github/jonathanxd/iutils/type/TypeInfo;Ljava/lang/Object;)Lcom/github/projectsandstone/eventsys/test/event/MyGenericEvent; 
  maxStack: 9, maxLocals: 3 
  Label_0:
   LINE 4 -> Label_0
    aload 0
    getfield com.github.projectsandstone.eventsys.test.factory.MyFactory$Impl.eventGenerator (type: com.github.projectsandstone.eventsys.gen.event.EventGenerator)
    ldc Lcom/github/projectsandstone/eventsys/test/event/MyGenericEvent;              // type: java.lang.Class
    invokestatic com.github.jonathanxd.iutils.type.TypeInfo.builderOf(java.lang.Class)com.github.jonathanxd.iutils.type.TypeInfoBuilder (ownerIsInterface: false)
    iconst_1
    anewarray com.github.jonathanxd.iutils.type.TypeInfo
    dup
    iconst_0
    aload 1
    aastore
    invokevirtual com.github.jonathanxd.iutils.type.TypeInfoBuilder.of(com.github.jonathanxd.iutils.type.TypeInfo[])com.github.jonathanxd.iutils.type.TypeInfoBuilder (ownerIsInterface: false)
    invokevirtual com.github.jonathanxd.iutils.type.TypeInfoBuilder.build()com.github.jonathanxd.iutils.type.TypeInfo (ownerIsInterface: false)
    iconst_0
    anewarray java.lang.Object
    invokestatic com.github.jonathanxd.iutils.collection.Collections3.listOf(java.lang.Object[])java.util.List (ownerIsInterface: false)
    iconst_0
    anewarray java.lang.Object
    invokestatic com.github.jonathanxd.iutils.collection.Collections3.listOf(java.lang.Object[])java.util.List (ownerIsInterface: false)
    iconst_1
    anewarray java.lang.String
    dup
    iconst_0
    ldc "obj"              // type: java.lang.String
    aastore
    iconst_1
    anewarray java.lang.Object
    dup
    iconst_0
    aload 2
    aastore
    invokedynamic create(com.github.projectsandstone.eventsys.gen.event.EventGenerator, com.github.jonathanxd.iutils.type.TypeInfo, java.util.List, java.util.List, java.lang.String[], java.lang.Object[])com.github.projectsandstone.eventsys.test.event.MyGenericEvent [
      // Bootstrap method
      com.github.projectsandstone.eventsys.bootstrap.FactoryBootstrap.factoryBootstrap(java.lang.invoke.MethodHandles$Lookup, java.lang.String, java.lang.invoke.MethodType, java.lang.Object[])java.lang.invoke.CallSite (tag: h_invokestatic, itf: false) [
      ]
    ]
    areturn
  Label_1:
  LocalVariables {
    index: 2, name: obj, start: Label_0, end: Label_1, type: java.lang.Object, signature: null
    index: 1, name: arg0, start: Label_0, end: Label_1, type: com.github.jonathanxd.iutils.type.TypeInfo, signature: null
    index: 0, name: this, start: Label_0, end: Label_1, type: com.github.projectsandstone.eventsys.test.factory.MyFactory$Impl, signature: null
  }
}
```


#### Performance

Both have similar performance (and is very good).