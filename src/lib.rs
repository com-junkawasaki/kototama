//! # kototama — clojure-wasm-runtime
//!
//! One canonical **Clojure/EDN-subset → WebAssembly** runtime, unifying the two
//! compilers that grew up apart:
//!
//! - **kotoba-clj** — the general-purpose core compiler (kotoba-edn reader + wasm
//!   codegen). `compile_clj`.
//! - **kami-engine-clj** — the game layer on top: `GAME_PRELUDE` (vec/map/timer/vec3
//!   helpers) + the `kami:engine` host ABI. `compile_game`.
//!
//! Both read Clojure source *as EDN* (kotoba-edn is the single source-of-truth
//! reader) and emit real wasm bytes. kototama is the seam they share, plus the
//! **browser path**: this crate compiles to wasm itself, so a browser can compile
//! Clojure → wasm with no server, then run the result via `WebAssembly.instantiate`.
//! That is what lights up "edit CLJ → live game" (CodePen for CLJ games) on
//! network-isekai / isekai.network.
//!
//! See `90-docs/adr/0001-kototama-clojure-wasm-runtime.md`.

pub use kami_engine_clj;
pub use kotoba_clj;

/// The compiler error type, re-exported so hosts depend only on kototama.
pub use kami_engine_clj::CljError;

/// Compile a general Clojure/EDN-subset program to a wasm module (no game prelude).
pub fn compile_clj(src: &str) -> Result<Vec<u8>, String> {
    kotoba_clj::compile_str(src).map_err(|e| e.to_string())
}

/// Compile a kami **game** (`logic.clj`): the `GAME_PRELUDE` is prepended and the
/// `kami:engine` host ABI is targeted, so the module is drivable by a kami host
/// (native `kami-script-runtime`, or a browser host over the same imports).
pub fn compile_game(src: &str) -> Result<Vec<u8>, String> {
    compile_game_typed(src).map_err(|e| e.to_string())
}

/// Native: compile a game keeping the typed [`CljError`], so hosts get `?`-friendly
/// errors (kami-script-runtime). Same output as [`compile_game`].
pub fn compile_game_typed(src: &str) -> Result<Vec<u8>, CljError> {
    kami_engine_clj::compile_str_with_prelude(src)
}

/// The game prelude source (helpers written in the language itself).
pub fn game_prelude() -> &'static str {
    kami_engine_clj::game_prelude()
}

/// The **actor / artificial-organism** prelude (the third layer above `compile_clj`).
///
/// Charter-gate + heartbeat-decision primitives written in the language itself, as the
/// shared runtime for **atproto actors** and **artificial organisms** (the etzhayyim
/// `20-actors/*` lineage: identity / kotoba commit-DAG / social membrane / autorun). It is
/// the scalar slice that compiles under the *current* `compile_clj` subset; the richer
/// primitives (datom vectors / maps / strings / Ed25519 sign / http bridge / storage append)
/// live in `lib/actor/*.cljc` and are reached via the `actor:host` ABI — exactly as games
/// reach vec/map/timer via `GAME_PRELUDE` + the `kami:engine` ABI. See
/// `90-docs/adr/0002-actor-organism-runtime-lib.md`.
pub const ACTOR_PRELUDE: &str = r#"
;; kototama organism/cell gate vocabulary — pure, scalar, subset-compilable.
;; The scalar slice of kototama.gates (lib/kototama/gates.cljc); the full lib runs
;; under bb/JVM. A post/outward act is a charter-clean DRY-RUN unless EVERY gate holds.
(defn kototama-dry-run? [status] (= status 0))            ; 0=dry-run · 1=published(Council-gated)
(defn kototama-enough-sources? [n] (if (< n 2) false true)) ; >=2 provenance citations
(defn kototama-cash-zero? [c] (= c 0))                    ; commons-not-a-market (G2)
(defn kototama-keyless? [server-held-key] (not server-held-key)) ; no-server-key
;; the membrane gate as one pure decision: may this become a dry-run post?
(defn kototama-may-draft? [status sources cash server-held-key]
  (and (kototama-dry-run? status)
       (and (kototama-enough-sources? sources)
            (and (kototama-cash-zero? cash)
                 (kototama-keyless? server-held-key)))))
;; idempotent-by-content heartbeat: append only when content changed (1) else no-op (0).
(defn kototama-append? [changed] (if changed 1 0))
"#;

/// Compile an **actor / organism** `logic.clj`: [`ACTOR_PRELUDE`] is prepended (charter-gate
/// + heartbeat primitives), then the general [`compile_clj`] core runs. Capability functions
/// (sign / http / storage / sha256) are the `actor:host` ABI — host-provided, like
/// `kami:engine` for games. Returns wasm bytes.
pub fn compile_actor(src: &str) -> Result<Vec<u8>, String> {
    let mut full = String::with_capacity(ACTOR_PRELUDE.len() + src.len() + 1);
    full.push_str(ACTOR_PRELUDE);
    full.push('\n');
    full.push_str(src);
    compile_clj(&full)
}

/// The actor/organism prelude source (helpers written in the language itself).
pub fn actor_prelude() -> &'static str {
    ACTOR_PRELUDE
}

/// Browser API: the compiler runs *in the page* (kototama compiled to wasm). Returns
/// wasm bytes the browser instantiates directly — no native runtime needed.
#[cfg(target_arch = "wasm32")]
mod web {
    use wasm_bindgen::prelude::*;

    /// Compile general Clojure source → wasm bytes (Uint8Array in JS).
    #[wasm_bindgen]
    pub fn compile(src: &str) -> Result<Vec<u8>, JsValue> {
        super::compile_clj(src).map_err(|e| JsValue::from_str(&e))
    }

    /// Compile a kami game `logic.clj` → wasm bytes (GAME_PRELUDE + kami:engine ABI).
    #[wasm_bindgen]
    pub fn compile_game(src: &str) -> Result<Vec<u8>, JsValue> {
        super::compile_game(src).map_err(|e| JsValue::from_str(&e))
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn compiles_general_clj_to_wasm() {
        let w = super::compile_clj("(defn fact [n] (if (< n 2) 1 (* n (fact (- n 1)))))").unwrap();
        assert_eq!(&w[0..4], b"\0asm"); // real wasm magic
    }

    #[test]
    fn compiles_game_logic_to_wasm() {
        let w = super::compile_game("(defsystem tick [dt] (+ dt 1))").unwrap();
        assert_eq!(&w[0..4], b"\0asm");
    }

    #[test]
    fn compiles_actor_logic_to_wasm() {
        // an actor's outward-membrane decision built on the ACTOR_PRELUDE gates
        let w = super::compile_actor(
            "(defn publish-decision [status sources cash key]
               (if (kototama-may-draft? status sources cash key) 1 0))",
        )
        .unwrap();
        assert_eq!(&w[0..4], b"\0asm"); // real wasm magic
    }

    #[test]
    fn actor_prelude_itself_compiles() {
        // the prelude alone must be subset-clean (it is prepended to every actor program)
        let w = super::compile_actor("(def ok 1)").unwrap();
        assert_eq!(&w[0..4], b"\0asm");
    }
}
