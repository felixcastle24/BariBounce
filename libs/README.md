# Bundled libraries

## baritone-fabric-1.20.0.jar

[Baritone](https://github.com/cabaletta/baritone) 1.20.0 for Fabric and Minecraft 26.3, built unmodified
from the Baritone 1.20.0 source release with:

```
./gradlew :fabric:build -Pavailable_loaders=fabric
```

The jar is `fabric/build/libs/baritone-fabric-1.20.0.jar`. The ProGuard step of that build, which only
makes a shrunk and obfuscated copy, is not needed.

Baritone is licensed under the GNU Lesser General Public License v3.0 with an additional "anime
exception". Both parts are in [`baritone-license/`](baritone-license/). Its source code is available
at https://github.com/cabaletta/baritone.

Baribounce only uses Baritone through its public API. Baritone stays an optional, separate mod: the
Baritone tab only appears when it is installed.
