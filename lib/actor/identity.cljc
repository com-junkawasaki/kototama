(ns actor.identity
  "actor.identity — self-issued identity for atproto actors + artificial organisms.

  The workspace model (root CLAUDE.md): an actor holds its OWN Ed25519 key → its
  key-derived name IS its graph → it is structurally authorized to self-mint/publish
  (no operator, no token, no owner hand-off). This is the portable home for the pure
  parts of that model: Ed25519 keygen, the did:key derivation, and the load-or-create
  persistence shape. Signing/keygen are JVM/bb (java.security Ed25519, verified to run
  under babashka); the did:key math is pure and wasm-portable.

  SECURITY: the private key NEVER touches git. Callers persist it under a gitignored
  path (e.g. `.<actor>/identity.edn`) or an IPFS keystore key — this ns only computes
  the public identity (did:key) and packages the non-secret record."
  (:require [clojure.string :as str]))

;; ── base58btc (Bitcoin alphabet) — for did:key:z… ────────────────────────────

(def ^:private b58 "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(defn- bytes->b58 [^bytes bs]
  #?(:clj
     (let [zeros (count (take-while zero? (seq bs)))
           n (reduce (fn [acc b] (+ (* acc 256) (bit-and b 0xff))) (biginteger 0) (seq bs))
           sb (StringBuilder.)]
       (loop [n n]
         (when (pos? (.signum n))
           (let [[q r] [(.divide n (biginteger 58)) (.intValue (.mod n (biginteger 58)))]]
             (.append sb (.charAt b58 r)) (recur q))))
       (dotimes [_ zeros] (.append sb \1))
       (str/reverse (.toString sb)))
     :cljs (throw (ex-info "base58 keygen is JVM/bb-only" {}))))

;; ── Ed25519 raw pubkey (32 bytes) → did:key ──────────────────────────────────

(defn raw-ed25519-pub
  "The 32-byte raw Ed25519 public key from a JDK PublicKey's X.509 SPKI encoding
  (the raw key is the trailing 32 bytes of the 44-byte SPKI)."
  [pk]
  #?(:clj (let [enc (.getEncoded pk) n (alength enc)]
            (java.util.Arrays/copyOfRange enc (- n 32) n))
     :cljs (throw (ex-info "JVM-only" {}))))

(defn did-key
  "32-byte raw Ed25519 pubkey → did:key:z… (multicodec 0xED01 prefix + base58btc)."
  [^bytes raw32]
  #?(:clj (let [prefixed (byte-array (+ 2 (alength raw32)))]
            (aset-byte prefixed 0 (unchecked-byte 0xED))
            (aset-byte prefixed 1 (unchecked-byte 0x01))
            (System/arraycopy raw32 0 prefixed 2 (alength raw32))
            (str "did:key:z" (bytes->b58 prefixed)))
     :cljs (throw (ex-info "JVM-only" {}))))

#?(:clj
   (defn generate
     "Mint a fresh Ed25519 keypair → {:public-key :private-key :did}. Verified under bb."
     []
     (let [kp (.generateKeyPair (java.security.KeyPairGenerator/getInstance "Ed25519"))
           pub (.getPublic kp)]
       {:public-key pub :private-key (.getPrivate kp)
        :did (did-key (raw-ed25519-pub pub))})))

(defn public-record
  "The NON-SECRET identity record an actor commits to its repo."
  [{:keys [did handle ipns ipns-key model minted]}]
  {:actor/did did :actor/handle handle :actor/ipns ipns
   :actor/ipns-key ipns-key :actor/model (or model :self-issued-cacao)
   :actor/operator-gated false :actor/minted minted})
