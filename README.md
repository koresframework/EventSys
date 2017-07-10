# EventSys

**This branch is targeting CodeAPI 4 alpha, not production ready**

Property based event class generator.

# Class Generation

Event Sys generate event class, event factories and method event listeners at runtime, the first generation may be slow (because of class loading), consequent generations are faster.

## Why do we use code generation

First, I started writing an event system in `SandstoneAPI` ([first commit](https://github.com/ProjectSandstone/SandstoneAPI/commit/df938d487d6dc8e405acadf2d43fc58bc219b634)), and then it was moved from `SandstoneAPI` project to a separated project.

I used `Class Generation` because instead of having to write a bunch of event classes only to extend `PlayerEvent` was not a good idea. (And yes, it would be more faster than writing an event generator, but I like to take longer to finish things `:P`), I also used class generation to generate faster listener invocations instead of using `Reflect` or `MethodHandle API`, to generate factory interface implementation, and recently written an Annotation Processor to generate factory interface for events.

**Note: First implementation was written using CodeAPI 1.14, we are currently on CodeAPI 4.0-ALPHA, and between these two versions, CodeAPI was rewritten 3 times**

# Base event interfaces

All event interfaces must extend `Event` interface, EventSys uses a property system to provide values regardless the event type.

## Event with properties

Properties are inferred from `getters` and `setters` methods, in Kotlin you can use `val` in interfaces and the Kotlin compiler will generate getters (and setters for `var`).

Kotlin:
```kotlin
interface PersonRegisterEvent : Event {
    val name: String
    val email: String
}
```

Java:
```java
interface PersonRegisterEvent extends Event {
    String getName();
    String getEmail();
}
```

You can also add methods with default implementations to event interfaces.

```java
interface PlayerJoinEvent extends Event {
    Player getPlayer();
    
    default void kick() {
        this.getPlayer().kick();
    }
}
```

You can also provide default methods implementation in a `DefaultImpls` inner class

```java
interface PlayerJoinEvent extends Event {
    Player getPlayer();
    
    void kick();
    
    class DefaultImpls {
        public static void kick(PlayerJoinEvent event) {
            event.getPlayer().kick();
        }
    }
}
```

**We only support this to keep compatibility with kotlin functions with default implementation.**

## Event factories

To create event classes you need to provide a factory interface, example:

```java
public interface MyEventFactory {
    PersonRegisterEvent createPersonRegisterEvent(@Name("name") String name, @Name("email") String email);
    PlayerJoinEvent createPlayerJoinEvent(@Name("player") Player player);
}
```

**If you create a kotlin event factory interface, or pass `-parameters` to javac you don't need to annotated parameters with `@Name` annotation**

```kotlin
interface MyEventFactory {
    fun createPersonRegisterEvent(name: String, email: String): PersonRegisterEvent
    fun createPlayerJoinEvent(player: Player): PlayerJoinEvent
}
```

## Event manager

Event manager manages and dispatch events.

## Getting event manager instance.

```
EventManager manager = new CommonEventManager.Default(); // Recommended only for java applications.
```

### Phases

EventSys dispatch is split in phases, each phase corresponds to a group of `EventListener`s, `EventListener`s can listen to all phases (-1) and `EventManager` can dispatch to all `EventListener` ignoring their phase group.

### Owners

EventSys requires a owner instance to register listener and dispatch events.

### Creating event instance

```java
public class Example {
    public void example() {
        EventManager manager = new CommonEventManager.Default(); // Recommended only for java applications.
        // Create the factory
        MyEventFactory factory = manager.getEventGenerator().createFactory(MyEventFactory.class);
        PersonRegisterEvent event = factory.createPersonRegisterEvent("Username", "username@domain.com");        
    }
}
```

### Dispatch

```java
public class Example {
    public void example() {
        EventManager manager = new CommonEventManager.Default(); // Recommended only for java applications.
        // Create the factory
        MyEventFactory factory = manager.getEventGenerator().createFactory(MyEventFactory.class);
        PersonRegisterEvent event = factory.createPersonRegisterEvent("Username", "username@domain.com");
        manager.dispatch(event, this);
    }
}
```

### Listening

There is two ways to listen events.

###### Method listeners

First parameter of method listener must be the type of event to listen, and the method must be annotated with `@Listener`.

```java
public class MyListener {
    
    @Listener
    public void listen(PersonRegisterEvent event) {
        String name = event.getName();
    }
    
}
```

You can also get instances adding `@Name` annotation to additional parameters:

```java
public class MyListener {
    
    @Listener
    public void listen(PersonRegisterEvent event, @Name("name") String name, @Name("email") String email) {
        // ...
    }
    
}
```

###### Listener class

```java
public class MyListenerClass extends EventListener<PersonRegisterEvent> {
    
    @Override
    public void onEvent(PersonRegisterEvent event, Object owner) {
        // ...
    }
    
}
```

#### Registering listeners

###### Method listener

```
eventManager.registerListeners(this /* owner of registration */, new MyListener() /* method listener instance */);
```

###### Listener class

```
eventManager.registerListener(this /* owner of registration */, PersonRegisterEvent.class /*event type*/, new MyListenerClass());
```

## Extension

Sometimes you want to provide additional properties to the event but does not want to write a specialized version of the event, in this case you can use `Extension`, `Extension` may also be used to provide implementation of event methods.

You could specify extensions in factory class or register in `EventGenerator` using `EventGenerator.registerExtension(Class, ExtensionSpecification)`

Example of extension class:

```java
public interface PlayerJoinEvent extends Event {
    Player getPlayer();
    
    void kick();
}

public class PlayerJoinEventExtension {
    private final PlayerJoinEvent event;
    
    public PlayerJoinEventExtension(PlayerJoinEvent event) {
        this.event = event;
    }
    
    public void kick() {
        this.event.getPlayer().kick();
    }
}
```

~~Extension methods must be static and receive event as parameter (and have only one argument).~~ Since 1.1, extensions must not be static and is instantiated in event constructor. The extension class must have at least one no-arg constructor.

You may prefer to use Kotlin extension methods:

```kotlin
class PlayerJoinEventExtension(val event: PlayerJoinEvent) {
    fun PlayerJoinEvent.kick() {
        this.player.kick()
    }
}
```

# Performance

Measures points that `Kotlin Reflect` takes too much time to get function names, EventSys does not call `Kotlin Reflect` unless you use a `Kotlin` class or if the parameters have `@Name` annotation. 

**If (and only if), JetBrains enables Java 8 parameters emission by default ([KT-15346](https://youtrack.jetbrains.com/issue/KT-15346)), we will change te code to use annotations or Java 8 parameter names.** 

This is not a `Major` problem because JIT may (and will) optimize this.


# Internals

See [Internals](INTERNALS.md) for implementation details.