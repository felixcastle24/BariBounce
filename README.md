# Baribounce

**Baribounce is an unofficial fork of [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) by CCBlueX.
It is not affiliated with, endorsed by or supported by CCBlueX.** Please don't report problems with
Baribounce to the LiquidBounce team.

Baribounce is based on LiquidBounce 0.43.0 for Minecraft 26.3 (Fabric) and adds:

- **Redesigned ClickGUI**: a single window with a category sidebar, searchable module cards in a grid or
  list, a slide-in settings pane and a violet glass look.
- **Config switcher**: save your setup under a name and switch between configs from the ClickGUI. Each
  config stores which modules are on, so switching never mixes two configs.
- **ModuleList HUD element**: shows your enabled modules in the same style as the ClickGUI.
- **Baritone tab** (optional): Goto, Mine, Follow, Farm and Explore modules plus Baritone's main options,
  when the [Baritone](https://github.com/cabaletta/baritone) mod is installed.
- **Goto map**: a top-down map of the loaded chunks in the ClickGUI; click a spot and Baritone walks there.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 26.3.
2. Download `baribounce-<version>.jar` from the releases and put it into your `mods` folder.
3. For the Baritone tab, also put `baritone-fabric-1.20.0.jar` from the same release into `mods`.

Using a hacked client on multiplayer servers usually breaks their rules and can get you banned.

## Building

Baribounce builds like LiquidBounce. It needs JDK 25 and [Node.js](https://nodejs.org) for the theme.

```
./gradlew build      # jar in build/libs
./gradlew runClient  # starts a development client with Baritone
```

## License

Baribounce is free software under the [GNU General Public License v3.0](LICENSE), like LiquidBounce.
If you use code from this project you must also publish your source code under the GPL.

LiquidBounce is © CCBlueX. The source files keep their original copyright headers. Code added in this
fork is marked by its git history.

Baritone, bundled in `libs/` and shipped as a separate jar, is licensed under the GNU Lesser General
Public License v3.0 with the "anime exception"; see [`libs/README.md`](libs/README.md).

## Credits

- [CCBlueX](https://github.com/CCBlueX) for LiquidBounce
- [cabaletta](https://github.com/cabaletta) and contributors for Baritone
