# EventSys

**This branch is targeting CodeAPI 4 alpha, not production ready**

Property based event class generator.

# Class Generation

Event Sys generate event class, event factories and method event listeners at runtime, the first generation may be slow (because of class loading), consequent generations are faster.

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

Sometimes you need to provide a implementation of a method but you don't want to write this on the interface, to solve this you can use `Extension`s.
 
*EventSys Extension* is like a trait, a `Extension` can provide a interface to implement and the implementation of methods.

You could specify extensions in factory class or register in `EventGenerator` using `EventGenerator.registerExtension(Class, ExtensionSpecification)`

Example of extension class:

```java
public interface PlayerJoinEvent extends Event {
    Player getPlayer();
    
    void kick();
}

public class PlayerJoinEventExtension {
    public static void kick(PlayerJoinEvent event) {
        event.getPlayer().kick();
    }
}
```

Extension methods must be static and receive event as parameter (and have only one argument).

You may prefer to use Kotlin extension methods:

```kotlin
fun PlayerJoinEvent.kick() {
    this.player.kick()
}
```

