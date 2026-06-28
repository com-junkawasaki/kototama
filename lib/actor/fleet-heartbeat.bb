#!/usr/bin/env bb
;; kototama — FLEET HEARTBEAT driver. The operational scheduler that makes a whole fleet of
;; organisms self-update: it scans a root for actors (dirs with an `actor.edn`) and runs each
;; one's beat+publish through the shared runtime (publish.bb runs the actor's :regen beat, then
;; ipfs-name-publishes its graph under its own key). This is the loop that DRIVES the beats;
;; the per-beat *semantics* (idempotent-by-content, mood, leash-gated act) live in
;; kototama.organism / kototama.heartbeat. Complementary, not a replacement.
;;
;;   bb fleet-heartbeat.bb --root <dir> [--live] [--once|--interval <seconds>]
;;
;; --root      dir whose immediate children may contain actor.edn (default: ../../etzhayyim)
;; --live      actually ipfs-name-publish (omit = dry-run, compose only)
;; --once      one pass then exit (default)
;; --interval  loop forever, sleeping N seconds between passes
(require '[babashka.process :refer [shell sh]] '[clojure.string :as str])

(def here (-> *file* (java.io.File.) .getAbsoluteFile .getParentFile))
(def runtime (str (.getPath here) "/publish.bb"))
(defn arg [flag] (let [a (vec *command-line-args*) i (.indexOf a flag)] (when (>= i 0) (get a (inc i)))))
(def live? (some #{"--live"} *command-line-args*))
(def interval (some-> (arg "--interval") parse-long))
(def root (-> (or (arg "--root") (str (.getPath here) "/../../../../etzhayyim"))
              (java.io.File.) .getAbsoluteFile))

(defn actors []
  ;; immediate child dirs of root that contain actor.edn
  (->> (.listFiles root)
       (filter #(.isDirectory %))
       (filter #(.exists (java.io.File. % "actor.edn")))
       (sort-by #(.getName %))))

(defn beat! [dir]
  (let [args (cond-> ["bb" runtime "--actor" (.getPath dir)] live? (conj "--live"))
        {:keys [out err exit]} (apply sh args)
        txt (str out "\n" err)
        ipns (second (re-find #"Published to (k51\w+)" txt))
        cid  (second (re-find #"bundle CID:\s*(\w+)" txt))]
    {:name (.getName dir) :exit exit :ipns ipns :cid cid
     :ok (and (zero? exit) (or ipns (not live?)))}))

(defn pass! []
  (let [as (actors)]
    (println (str "── fleet heartbeat: " (count as) " organisms · " (if live? "LIVE" "dry-run") " ──"))
    (let [results (doall (for [d as] (let [r (beat! d)]
                                       (println (format "  %-14s %s" (:name r)
                                                        (cond (:ipns r) (str "→ /ipns/" (:ipns r))
                                                              (:ok r)   (str "compose ok (" (:cid r) ")")
                                                              :else     (str "FAILED (exit " (:exit r) ")"))))
                                       r)))
          ok (count (filter :ok results))]
      (println (str "── " ok "/" (count results) " beat ok ──"))
      results)))

(if interval
  (loop []
    (pass!)
    (println (str "sleeping " interval "s …"))
    (Thread/sleep (* 1000 interval))
    (recur))
  (pass!))
