(ns kototama.leash
  "kototama · leash — the revocable member CACAO capability, distilled from
  ibuki delegation + ADR-2606111400. Autonomous identity = a scoped, expiring,
  revocable capability a MEMBER signs in their own runtime; the organism only
  ever PRESENTS the opaque token (present-only — it NEVER signs, holds no
  custodial key). The kotoba node verifies the member signature and attributes
  every autonomous write to the consenting member (`write-author`).

  The leash is the off-switch of the 種をまく doctrine: stop re-issuing (or let
  it expire) and the organism self-disables, falling back to its local log."
  (:require [clojure.string :as str]))

(defn leash
  "Construct the present-only leash view of a member-issued delegation bundle.
  Required: :member-did :capability :graph :exp (unix seconds). :cacao-b64 is the
  opaque member-signed CBOR (present-only; this lib never inspects or signs it)."
  [{:keys [member-did capability graph exp cacao-b64 nonce]}]
  {:member-did member-did :capability capability :graph graph
   :exp exp :cacao-b64 cacao-b64 :nonce nonce})

(defn valid?
  "A leash is valid iff present, not expired (exp > now), and scoped to the
  intended capability + graph. `now` is a caller-supplied unix-seconds int
  (no wall-clock in the lib → replay-safe)."
  [{:keys [member-did capability graph exp cacao-b64]} now
   & [{:keys [need-capability need-graph]}]]
  (boolean (and member-did cacao-b64 (number? exp) (> exp now)
                (or (nil? need-capability) (= need-capability capability))
                (or (nil? need-graph) (= need-graph graph)))))

(defn write-author
  "The member DID an autonomous write is attributed to (accountability by
  consent). nil when unleashed → caller must fall back to operator-bearer or
  refuse the write."
  [leash] (:member-did leash))

(defn revoked?
  "A leash is revoked when absent or expired."
  [leash now] (not (valid? leash now)))
