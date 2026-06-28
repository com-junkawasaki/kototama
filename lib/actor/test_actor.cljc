(ns actor.test-actor
  "Tests for the shared atproto-actor / artificial-organism lib (gates · atproto · identity)."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [actor.gates :as gates]
            [actor.atproto :as at]
            [actor.identity :as id]))

;; ── gates: G-no-advice (word-boundary EN, substring JA) ──────────────────────

(deftest no-advice-passes-disclosed-concepts
  (is (gates/no-advice? "Toyota disclosed operating income ¥5,353,000M"))   ; not "rating"
  (is (= "x" (gates/assert-no-advice "x"))))

(deftest no-advice-rejects-advice
  (doseq [bad ["we recommend you buy" "price target raised" "rating: outperform"
               "this looks undervalued" "買い推奨です" "目標株価は1万円" "業績予想を上方修正"]]
    (is (not (gates/no-advice? bad)) (str "should flag: " bad))
    (is (thrown? clojure.lang.ExceptionInfo (gates/assert-no-advice bad)))))

(deftest may-draft-folds-no-advice
  (is (gates/may-draft? {:sources ["a" "b"] :text "disclosed revenue ¥1tn"}))
  (is (not (gates/may-draft? {:sources ["a" "b"] :text "strong buy"})))
  (is (= [:G-no-advice] (gates/why-refused {:sources ["a" "b"] :text "strong buy"}))))

;; ── atproto: content-address + json + builders ───────────────────────────────

(deftest rkey-deterministic
  (is (= (at/rkey "k" "fact.x") (at/rkey "k" "fact.x")))
  (is (not= (at/rkey "k" "fact.x") (at/rkey "k" "fact.y")))
  (is (str/starts-with? (at/rkey "kanjo" "z") "kanjo")))

(deftest json-writer
  (is (= "{\"a\":1,\"b\":[true,null]}" (at/->json {"a" 1 "b" [true nil]})))
  (is (= "\"x\\\"y\"" (at/->json "x\"y"))))

(deftest feed-post-guards-text
  (is (= "app.bsky.feed.post" (get (at/feed-post {:text "disclosed FY2024 revenue ¥1tn"
                                                  :created-at "2026-01-01T00:00:00Z"}) "$type")))
  (is (thrown? clojure.lang.ExceptionInfo
               (at/feed-post {:text "buy now" :created-at "2026-01-01T00:00:00Z"})))
  (let [p (at/feed-post {:text "disclosed revenue ¥1tn" :created-at "t"
                         :embed-uri (at/at-uri "did:x" "com.y.z" "k1")})]
    (is (= "at://did:x/com.y.z/k1" (get-in p ["embed" "record" "uri"])))))

;; ── identity: Ed25519 → did:key (runs under bb) ──────────────────────────────

(deftest did-key-derivation
  (let [{:keys [public-key did]} (id/generate)]
    (is (str/starts-with? did "did:key:z"))
    (is (= 32 (count (id/raw-ed25519-pub public-key))))
    ;; deterministic: same raw pub → same did
    (is (= did (id/did-key (id/raw-ed25519-pub public-key))))))
