# Gauntlet Highlighter

A standalone RuneLite plugin that highlights resource nodes, monsters, and ground items in
the Corrupted Gauntlet, and computes a suggested collection-aware route through them.

Built from RuneLite's [example-plugin](https://github.com/runelite/example-plugin) template,
so it compiles against the published `net.runelite:client` artifact rather than a full
RuneLite source checkout.

## Running

```
./gradlew run
```

## Building a runnable jar

```
./gradlew shadowJar
```

Produces `build/libs/easycgplugin-<version>-all.jar`, runnable with:

```
java -jar build/libs/easycgplugin-<version>-all.jar --developer-mode --debug
```
