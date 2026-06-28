(ns actor.gates
  "actor.gates — the shared CHARTER-GATE vocabulary for atproto actors + artificial
  organisms (the etzhayyim 20-actors/* lineage: iriai/kaname/tsubasa/ibuki/danjo…).

  Each actor re-implemented the same handful of gate predicates inline; this is the one
  portable home. Pure, deterministic, dependency-free — runs today under bb/JVM and (the
  scalar subset of it) under the kototama wasm runtime via the ACTOR_PRELUDE.

  The gates encode the recurring invariants:
    G-sources     — an outward record needs >=2 provenance citations
    G-cash-zero   — a commons act is never a market (cash to the consumer = 0)
    G-keyless     — no-server-key: the server holds no signing key (ADR-2605231525)
    G-dry-run     — R0 outward acts are :dry-run; :published is gated, never default
    G-sim-only    — assessment/simulation only; never an actuation
  A composite `may-draft?` is the membrane decision: a record may become a DRY-RUN
  outward post only when EVERY applicable gate holds."
  (:require [clojure.string :as str]))

(defn enough-sources?
  "G-sources: >=2 non-blank provenance citations (ADR / committed-ledger CID / DID)."
  [sources]
  (>= (count (filter #(and % (seq (str/trim (str %)))) (or sources []))) 2))

(defn cash-zero?
  "G-cash-zero: the consumer-facing cash amount is exactly 0 (commons, not a market)."
  [cash]
  (= 0 (or cash 0)))

(defn keyless?
  "G-keyless (no-server-key): the server holds no signing key for this act."
  [server-held-key?]
  (not (boolean server-held-key?)))

(defn dry-run?
  "G-dry-run: the act's status is :dry-run (the only producible status at R0)."
  [status]
  (= :dry-run status))

(defn sim-only?
  "G-sim-only: the act is flagged simulation/assessment, never an actuation."
  [sim?]
  (boolean sim?))

;; G-no-advice — an outward TEXT may state observations but never advice / valuation /
;; forecast / buy-sell. English terms match on WORD BOUNDARIES (so "ope-rating income"
;; never trips "rating"); Japanese has no boundaries → substring is the correct test.
;; Promoted from the etzhayyim kanjō atproto cell (ADR-0002) to the shared lib.
(def advice-terms-en
  ["buy" "sell" "hold" "overweight" "underweight" "outperform" "underperform"
   "price target" "undervalued" "overvalued" "cheap" "expensive" "bullish" "bearish"
   "recommend" "rating" "ratings" "upgrade" "downgrade" "forecast" "guidance"])
(def advice-terms-ja
  ["投資判断" "買い推奨" "売り推奨" "推奨" "割安" "割高" "目標株価" "格付" "業績予想" "見通し"])

(def ^:private advice-en-re
  (re-pattern (str "(?i)\\b(?:" (str/join "|" (map #(str/replace % " " "\\s+") advice-terms-en)) ")\\b")))

(defn advice-hit
  "Return the first advice/valuation/forecast term `text` carries, or nil if clean."
  [text]
  (let [t (or text "")]
    (or (some #(when (str/includes? t %) %) advice-terms-ja)
        (re-find advice-en-re t))))

(defn no-advice?
  "G-no-advice: `text` carries no advice / valuation / forecast / buy-sell language."
  [text]
  (nil? (advice-hit text)))

(defn assert-no-advice
  "Throw if `text` carries advice language (G-no-advice); else return it unchanged.
  The single guard every machine-composed outward string passes before publish."
  [text]
  (when-let [hit (advice-hit text)]
    (throw (ex-info (str "G-no-advice violation: outward text carries forbidden term " (pr-str hit))
                    {:term hit :text text})))
  text)

(defn may-draft?
  "The outward-membrane decision: may this record become a DRY-RUN post?
  Pass a map of the act's gate-relevant fields; missing keys default to the safe value.
  Returns true only when every applicable gate holds."
  [{:keys [status sources cash server-held-key sim-only text]
    :or {status :dry-run sources [] cash 0 server-held-key false sim-only true}}]
  (and (dry-run? status)
       (enough-sources? sources)
       (cash-zero? cash)
       (keyless? server-held-key)
       (sim-only? sim-only)
       (or (nil? text) (no-advice? text))))      ; G-no-advice applies only when text is present

(defn why-refused
  "Diagnostic: the ordered list of gate keywords that FAILED (empty = may-draft?)."
  [{:keys [status sources cash server-held-key sim-only text]
    :or {status :dry-run sources [] cash 0 server-held-key false sim-only true}}]
  (cond-> []
    (not (dry-run? status))             (conj :G-dry-run)
    (not (enough-sources? sources))     (conj :G-sources)
    (not (cash-zero? cash))             (conj :G-cash-zero)
    (not (keyless? server-held-key))    (conj :G-keyless)
    (not (sim-only? sim-only))          (conj :G-sim-only)
    (and (some? text) (not (no-advice? text))) (conj :G-no-advice)))
