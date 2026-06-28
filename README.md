# kototama — clojure-wasm-runtime

One canonical **Clojure/EDN-subset → WebAssembly** runtime. Unifies the two compilers
that grew up apart, and adds the browser path so Clojure compiles + runs **in the page**.

```
kotoba-edn (reader)  →  kotoba-clj (core)  +  kami-engine-clj (game layer)   →  kototama
                                            +  actor (atproto/organism layer)     ├─ native (rlib, +run→wasmtime)
                                                                                  └─ browser (wasm: compile / compile_game / compile_actor)
```

- **kotoba-clj** — general Clojure/EDN → wasm core.
- **kami-engine-clj** — `GAME_PRELUDE` + `kami:engine` host ABI for games.
- **actor** — `ACTOR_PRELUDE` + `actor:host` ABI for **atproto actors + artificial organisms**
  (the etzhayyim `20-actors/*` lineage). The shared runtime — self-certifying did:key,
  kotoba commit-DAG heartbeat, charter-gate vocabulary, outward membrane — lives in
  `lib/actor/*.cljc` (portable, bb/JVM today; scalar slice compiles to wasm now). See
  [ADR-0002](90-docs/adr/0002-actor-organism-runtime-lib.md).
- **kototama** — the seam + the **in-browser compiler**: edit CLJ → compile to wasm →
  `WebAssembly.instantiate` → run. No server, no native runtime. This is what powers
  live CLJ-game editing on [network-isekai](https://github.com/gftdcojp/network-isekai)
  / isekai.network.

## API

```rust
kototama::compile_clj(src)   -> Result<Vec<u8>, String>  // general program → wasm
kototama::compile_game(src)  -> Result<Vec<u8>, String>  // logic.clj (+GAME_PRELUDE, kami:engine ABI)
kototama::compile_actor(src) -> Result<Vec<u8>, String>  // actor logic (+ACTOR_PRELUDE, actor:host ABI)
```

### actor / organism layer (`lib/actor/`)

The shared runtime for atproto actors + artificial organisms — portable `.cljc`, runs today
under babashka:

```bash
bb --classpath lib lib/actor/test_actor.clj    # actor.gates / membrane / heartbeat / didkey
```

- `actor.gates` — charter-gate vocabulary (≥2 sources · cash≡0 · no-server-key · dry-run ·
  sim-only · no-advice) + `may-draft?` / `why-refused`.
- `actor.membrane` — outward self-publication membrane (dry-run posts; `build-live` is
  Council-gated, refuses at R0).
- `actor.heartbeat` — idempotent-by-content commit-DAG beat decision (crash/re-run safe).
- `actor.didkey` — self-certifying `did:key` (Ed25519) encoding (inlined base58btc).
- `actor:host` ABI (`lib/actor/host.edn`) — the crypto/net/storage capability boundary
  (`gen-keypair/sign/verify`, `sha256-hex`, `http-post`, `log-read/append!`, `now`) — the
  actor is the *bearer* of capability, never the holder of a server key.

Browser (wasm-bindgen, after `wasm-pack build --target web`):

```js
import init, { compile, compile_game } from "./pkg/kototama.js";
await init();
const wasm = compile_game(logicCljSource);          // Uint8Array of a real wasm module
const { instance } = await WebAssembly.instantiate(wasm, hostImports);
// drive instance.exports (tick / on-event …) from the JS/CLJS host (binds kami:engine/*)
```

## Build

```bash
cargo test --target <native>            # facade + both compilers (real wasm magic)
cargo build --features run              # native + wasmtime execution
wasm-pack build --target web            # → pkg/ : in-browser compiler
```

## Design

See [ADR-0001](90-docs/adr/0001-kototama-clojure-wasm-runtime.md): layered (core +
game), composition-first consolidation, the browser compile→instantiate loop, and the
migration that points kami-script-runtime + network-isekai at this one toolchain.

## License

MIT.
