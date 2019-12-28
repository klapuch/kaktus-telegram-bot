(ns ktb.core
  (:require [clj-http.client :as client])
  (:require [digest])
  (:require [cheshire.core :as json])
  (:import [org.jsoup Jsoup])
  (:require [clojure.java.io :as io])
  (:require [clojure.string :as str]))

(def config (load-file "config/config.local.edn"))

(def KAKTUS_SECTION_FILENAME 
  (let [filename (format "%s/bonuses" (get (get config :store) :filename))]
    (if-not (.exists (io/file filename))
      (do
        (io/make-parents filename)
        (spit filename "")))
    filename))

(defn with-telegram-token [path]
  (let [TELEGRAM_URL "https://api.telegram.org"]
    (format "%s/bot%s/%s" TELEGRAM_URL (get (get config :telegram) :token) path)))

(def KAKTUS_URL "https://www.mujkaktus.cz/homepage")
(def KAKTUS_SELECTOR ".box-bubble > * > .journal-content-article:nth-of-type(2) > * > p:not(:empty):nth-of-type(2)")

(defn get-body [url] (get (client/get url) :body))
(defn get-parsed-body [body] (Jsoup/parse body))

(def kaktus-page (get-body KAKTUS_URL))
(def kaktus-section (.html (.select (.body (get-parsed-body kaktus-page)) KAKTUS_SELECTOR)))

(defn unify-section [section] (digest/md5 (str/trim section)))

(def last-section (slurp KAKTUS_SECTION_FILENAME))
(spit KAKTUS_SECTION_FILENAME kaktus-section)

(def subscriber-ids
  (let [updates (get (client/get (with-telegram-token "getUpdates")) :body)]
    (map (fn [result] (get (get (get result :message) :from) :id))
         (filter (fn [result] (= "/start" (str/trim (get (get result :message) :text))))
                 (get (json/parse-string updates true) :result)))))

(defn send-hook! []
  (doseq [chat_id subscriber-ids]
    (client/post
     (with-telegram-token "sendMessage")
     {:query-params {:chat_id chat_id :text kaktus-section}})))

(if-not (= 0 (compare (unify-section last-section) (unify-section kaktus-section)))
  (send-hook!))
