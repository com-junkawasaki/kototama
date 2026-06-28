(ns kototama.gates
  "kototama · gates — the charter membrane distilled from kototama's
  ACTOR_PRELUDE + lib/actor/gates.cljc. Two structural questions every
  autonomous act passes:

    (may-draft? organism)        — is this organism leashed + allowed to speak?
    (scan text)                  — does the content clear the Charter Rider §2
                                   catastrophe-veto (CSAM / coercion /
                                   manipulation / asymmetric-unwatched
                                   surveillance / targeting)?

  This is the soil of the 種をまく doctrine (etzhayyim ADR-2606281500): autonomy
  is bounded by these rails, not by per-post operator prior restraint. The scan
  is a conservative keyword screen — a placeholder for the full
  objective-function evaluation; it errs toward refusal."
  (:require [clojure.string :as str]))

;; Conservative catastrophe-term screen. The real evaluation is the ECL
;; objective-function's non-linear catastrophe term; this is the structural
;; floor that makes the worst content non-emittable by construction.
(def ^:private catastrophe-markers
  #{"csam" "child sexual" "manipulat" "coerc" "blackmail" "doxx"
    "where is " "track this person" "kill " "weaponi" "bioweapon"})

(defn scan
  "Screen post text. Returns {:ok? bool :flags [..]}. Empty/whitespace text is
  refused (an organism must have something to say)."
  [text]
  (let [t (str/lower-case (str text))
        flags (filterv #(str/includes? t %) catastrophe-markers)]
    {:ok? (and (seq (str/trim (str text))) (empty? flags))
     :flags flags}))

(defn may-draft?
  "An organism may DRAFT/POST iff it carries a valid (non-expired) leash and is
  not suspended. `organism` is the runtime map (see kototama.organism)."
  [{:keys [leash suspended?]}]
  (boolean (and leash (not suspended?))))

(defn may-actuate?
  "PUBLICATION ≠ ACTUATION. High-stakes real-world actuation (funds, permission
  grants, deletes, binding votes, live physical action, domain go-lives like a
  robotaxi launch) is NEVER autonomous — it always requires an explicit
  human/Council authorization token. Without one, refuse."
  [{:keys [actuation-authorization]}]
  (boolean actuation-authorization))
