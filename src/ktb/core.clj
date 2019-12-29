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
    (format "%s/%s" (get (get config :store) :filename) path))

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
      (format "%s/bot%s/%s" telegram_url (get (get config :telegram) :token) path)))

  (def KAKTUS_URL "https://www.mujkaktus.cz/homepage")
  (def KAKTUS_SELECTOR ".box-bubble > * > .journal-content-article:nth-of-type(2) > * > p:not(:empty):nth-of-type(2)")

  (defn get-body [url] (get (client/get url) :body))
  (defn get-parsed-body [body] (Jsoup/parse body))

  (def kaktus-page (get-body KAKTUS_URL))
  (def kaktus-section (.html (.select (.body (get-parsed-body kaktus-page)) KAKTUS_SELECTOR)))

  (defn unify-section
    [section]
    (digest/md5 (str/trim section)))

  (def last-section (slurp KAKTUS_SECTION_FILENAME))
  (spit KAKTUS_SECTION_FILENAME kaktus-section)

  (def new-subscriber-ids
    (let [updates (get (client/get (with-telegram-token "getUpdates")) :body)]
      (map (fn [result] (get (get (get result :message) :from) :id))
           (filter (fn [result] (= "/start" (str/trim (get (get result :message) :text))))
                   (get (json/parse-string updates true) :result)))))

  (def current-subscriber-ids
    (str/split (slurp KAKTUS_SUBSCRIBERS_FILENAME) #"\n"))

  (spit KAKTUS_SUBSCRIBERS_FILENAME
        (str/join "\n"
                  (filter some? (nth (data/diff new-subscriber-ids current-subscriber-ids) 0))))
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
