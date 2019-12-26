(ns ktb.core
  (:require [clj-http.client :as client])
  (:require [clojure.java.jdbc :as jdbc])
  (:require [digest])
  (:require [cheshire.core :as json])
  (:import [org.jsoup Jsoup])
  (:require [clojure.string :as str]))

(def config (load-file "config/config.local.edn"))

(def pg-db (get config :db))

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

; There should be repeatedle-read transaction, but it somehow does not work..
(def last-section
  (let [row (jdbc/query pg-db ["SELECT section FROM results ORDER BY id DESC LIMIT 1"])]
    (get (first row) :section)))

(jdbc/insert! pg-db :results {:page kaktus-page :section kaktus-section :selector KAKTUS_SELECTOR})

(def subsriber-ids
  (let [updates (get (client/get (with-telegram-token "getUpdates")) :body)]
    (map (fn [result] (get (get (get result :message) :from) :id))
         (filter (fn [result] (= "/start" (str/trim (get (get result :message) :text))))
                 (get (json/parse-string updates true) :result)))))

(defn send-hook! []
  (doseq [chat_id subsriber-ids]
    (client/post
     (with-telegram-token "sendMessage")
     {:query-params {:chat_id chat_id :text kaktus-section}})))

(if-not (= 0 (compare (unify-section last-section) (unify-section kaktus-section)))
  (send-hook!))
