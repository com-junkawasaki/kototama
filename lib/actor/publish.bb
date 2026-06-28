#!/usr/bin/env bb
;; kototama — the shared ORGANISM SELF-PUBLISH runtime. One actor-agnostic cell every
;; atproto actor / artificial organism invokes to publish its own graph under its OWN key
;; (self-issued CACAO; no operator, no token — ADR kototama-0002 / etzhayyim ADR-0006).
;;
;;   bb <kototama>/lib/actor/publish.bb --actor <actor-dir> [--live]
;;
;; The actor ships a tiny `actor.edn` (did / handle / ipns-key / regen / bundle / posts);
;; this runtime loads the shared gates·atproto·identity, RE-ASSERTS the G-no-advice membrane
;; on every post, bundles, and `ipfs name publish`es. Same loop for kanjō, keizu, danjo, …
(require '[babashka.process :refer [shell sh]]
         '[clojure.string :as str]
         '[clojure.edn :as edn])

(def here (-> *file* (java.io.File.) .getAbsoluteFile .getParentFile))
(load-file (str here "/gates.cljc"))
(load-file (str here "/didkey.cljc"))
(load-file (str here "/atproto.cljc"))
(load-file (str here "/identity.cljc"))
(require '[kototama.gates :as gates] '[kototama.atproto :as at])

(defn- argval [flag] (let [a (vec *command-line-args*)
                           i (.indexOf a flag)] (when (>= i 0) (get a (inc i)))))
(def live? (some #{"--live"} *command-line-args*))
(def actor-dir (-> (or (argval "--actor") ".") (java.io.File.) .getAbsoluteFile))
(defn at-actor [& xs] (str (.getPath actor-dir) "/" (str/join "/" xs)))
(defn exists? [p] (.exists (java.io.File. p)))

(def cfg (edn/read-string (slurp (at-actor "actor.edn"))))
(def ipns-key (:ipns-key cfg))

(defn sh-out [& args] (str/trim (:out (apply sh args))))

(defn ensure-key! []
  (when-not (some #{ipns-key} (str/split-lines (sh-out "ipfs" "key" "list")))
    (println "  minting IPNS key" ipns-key)
    (sh "ipfs" "key" "gen" ipns-key "--type=ed25519"))
  (some #(when (str/ends-with? % ipns-key) (first (str/split % #"\s+")))
        (str/split-lines (sh-out "ipfs" "key" "list" "-l"))))

(defn regenerate! []
  (doseq [cmd (:regen cfg)]
    (println "  regen:" (str/join " " (take 2 cmd)) "…")
    (apply shell {:dir (.getPath actor-dir)} cmd)))

(defn assert-posts-clean! []
  ;; the membrane — EVERY composed post re-passes the shared G-no-advice gate before publish
  (when-let [pf (:posts-file cfg)]
    (when (exists? (at-actor pf))
      (let [re (re-pattern (or (:post-text-re cfg) "\"text\"\\s*:\\s*\"(.*?)\"\\s*,"))
            texts (keep #(second (re-find re %))
                        (remove str/blank? (str/split-lines (slurp (at-actor pf)))))]
        (doseq [t texts] (gates/assert-no-advice t))
        (println (str "  G-no-advice ✓ — " (count texts) " posts clean"))))))

(defn bundle! []
  (let [pub (at-actor "out/publish")]
    (sh "rm" "-rf" pub) (.mkdirs (java.io.File. pub))
    (doseq [[src dst] (:bundle cfg)]
      (when (exists? (at-actor src))
        (let [df (java.io.File. (str pub "/" dst))]
          (.mkdirs (.getParentFile df))
          (sh "cp" "-r" (at-actor src) (str df)))))
    (spit (str pub "/index.json")
          (at/->json {"$type" (or (:graph-type cfg) "com.etzhayyim.actor.graph")
                      "actor" (:did cfg) "handle" (:handle cfg)
                      "ipns" (str "/ipns/" (ensure-key!))
                      "self_published" true
                      "runtime" "kototama/lib/actor (gates·atproto·identity)"
                      "model" "self-issued CACAO — actor holds its own key → key-derived IPNS graph; no operator"
                      "published" (:published cfg "2026-06-28")}))
    pub))

;; ── run ──────────────────────────────────────────────────────────────────────
(let [ipns (ensure-key!)]
  (println (str "organism self-publish — " (:did cfg) " · /ipns/" ipns))
  (regenerate!)
  (assert-posts-clean!)
  (let [pub (bundle!)
        cid (sh-out "ipfs" "add" "-rQ" "--pin=true" pub)]
    (println "  bundle CID:" cid)
    (if live?
      (do (shell "ipfs" "name" "publish" (str "--key=" ipns-key) (str "/ipfs/" cid))
          (println "  PUBLISHED → /ipns/" ipns))
      (println "  dry-run (pass --live). would publish /ipfs/" cid "under" ipns-key))))
