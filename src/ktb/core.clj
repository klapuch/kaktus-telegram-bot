(ns ktb.core
    (:require [clj-http.client :as client])
    (:require [digest])
    (:require [cheshire.core :as json])
    (:import [org.jsoup Jsoup])
    (:require [clojure.java.io :as io])
    (:require [clojure.data :as data])
    (:require [clojure.string :as str])
  (:gen-class))

(defn -main
  [& args]
  (def config (load-file "config/config.local.edn"))

  (defn create-file-if-not-exists
    [filename]
    (if-not (.exists (io/file filename))
            (do
              (io/make-parents filename)
              (spit filename "")))
    filename)

  (defn with-store-directory
    [path]
    (format "%s/%s" (:filename (:store config)) path))

  (def KAKTUS_SECTION_FILENAME
    (let [filename (with-store-directory "bonuses")]
      (create-file-if-not-exists filename)
      filename))

  (def KAKTUS_SUBSCRIBERS_FILENAME
    (let [filename (with-store-directory "subscribers")]
      (create-file-if-not-exists filename)
      filename))

  (defn with-telegram-token
    [path]
    (let [telegram_url "https://api.telegram.org"]
      (format "%s/bot%s/%s" telegram_url (:token (:telegram config)) path)))

  (def KAKTUS_URL "https://www.mujkaktus.cz/homepage")
  (def KAKTUS_SELECTOR ".box-bubble > * > .journal-content-article:nth-of-type(2) > * > p:not(:empty):nth-of-type(2)")

  (defn get-body [url] (:body (client/get url)))
  (defn get-parsed-body [body] (Jsoup/parse body))

  (def kaktus-page (get-body KAKTUS_URL))
  (def kaktus-section (.html (.select (.body (get-parsed-body kaktus-page)) KAKTUS_SELECTOR)))

  (defn unify-section
    [section]
    (digest/md5 (str/trim section)))

  (def last-section (slurp KAKTUS_SECTION_FILENAME))
  (spit KAKTUS_SECTION_FILENAME kaktus-section)

  (def new-subscriber-ids
    (let [updates (:body (client/get (with-telegram-token "getUpdates")))]
      (map #(:id (:from (:message %1)))
        (filter #(= "/start" (str/trim (:text (:message %1))))
          (:result (json/parse-string updates true))))))

  (def current-subscriber-ids
    (str/split (slurp KAKTUS_SUBSCRIBERS_FILENAME) #"\n"))

  (spit KAKTUS_SUBSCRIBERS_FILENAME
    (str/join "\n"
      (let [[new-subscribers] (data/diff new-subscriber-ids current-subscriber-ids)]
        (not-empty new-subscribers))))

  (def subscriber-ids
    (distinct (map str (concat new-subscriber-ids current-subscriber-ids))))

  (defn send-hook!
    [subscribers]
    (doseq [chat_id subscribers]
      (client/post
       (with-telegram-token "sendMessage")
       {:query-params {:chat_id chat_id :text kaktus-section}})))

  (if-not
   (= 0 (compare (unify-section last-section) (unify-section kaktus-section)))
   (send-hook! subscriber-ids)))
