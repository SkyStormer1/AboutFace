# About Face

**Hold a key. Place a block. It goes down facing the other way.**

A small client-side Fabric mod for Minecraft 26.2. Hold <kbd>Left Ctrl</kbd> and any directional
block you place comes out turned 180° from where it would normally point. Let go and placement is
completely normal again — nothing is hooked the rest of the time.

Works on **vanilla servers**. Nothing needs to be installed server-side.

---

## What it does

| Block | Normally | With About Face |
|---|---|---|
| Piston, observer, dropper, dispenser | Faces you | Faces away |
| Furnace, chest, blast furnace | Faces you | Faces away |
| Hopper | Into the face you clicked | Out the other side |
| Stairs | Faces you | Faces away |

The key is **held, like crouch** — not a toggle. There is nothing to leave switched on by mistake,
and releasing it takes effect immediately.

An action-bar line tells you which way the next block will go, so the mod is never silently active:

> About Face: next block faces north

## How it works

A vanilla server decides a block's facing for itself, from the state the player appears to be in
when the placement arrives. A client-side mod cannot overrule that — so instead of fighting the
placement, About Face arranges the answer **before** you click.

While the key is held, it looks at the block under your crosshair, works out what the server would
need to believe for that block to land reversed, and keeps telling it exactly that. Your right click
is then an ordinary placement that happens to produce a turned-around block. Nothing is cancelled
and nothing is replayed.

Two things can be claimed, and the cheaper one is preferred:

- **Rotation** — most directional blocks take their facing from where you are looking, so claiming a
  different rotation is enough and nothing else about the placement changes.
- **The click itself** — some blocks ignore your look direction completely. A hopper takes its
  facing from the face that was clicked. Claiming a click on the empty space the block is going into
  leaves it landing in the same place, while making the clicked face free to choose.

Nothing is assumed about which blocks read which input. Every candidate is checked by asking the
block what it *would* place before anything is claimed, so a block that cannot be turned around this
way is left alone rather than placed wrongly.

### Limits

- **A hopper cannot be made to face up.** Vanilla does not allow it — the property that stores a
  hopper's facing has no "up" value at all.
- Blocks whose orientation does not follow either your rotation or the clicked face are left alone.
  You will see *Cannot reverse that block here* rather than a wrong placement.

## Building

Requires JDK 25 or newer.

```bash
./gradlew build
```

The jar lands in `build/libs/`. For a dev client:

```bash
./gradlew runClient
```

## Installing

Drop the jar in `.minecraft/mods` alongside:

- [Fabric Loader](https://fabricmc.net/use/) 0.19.3+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## Configuration

Rebind the key in **Options → Controls → About Face**. That is the whole configuration surface, by
design.

## Licence and origin

MIT — see [LICENSE](LICENSE).

This mod is written from scratch and contains no code from any other project. The underlying
techniques — presenting a chosen rotation to the server, and claiming a click on the space a block
is going into — are long-standing, widely documented Minecraft client-side methods, not inventions
of any one mod. Several mods use them; none of their code is used here.

