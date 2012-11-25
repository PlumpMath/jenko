
(ns jenko.core
  (:import java.io.ByteArrayInputStream)
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.xml :as xml]))

(def env #(System/getenv %))

(def ^{:dynamic true} JENKINS_URL (env "JENKINS_URL"))
(def ^{:dynamic true} JENKINS_USER (env "JENKINS_USER"))
(def ^{:dynamic true} JENKINS_TOKEN (env "JENKINS_TOKEN"))

(defn parse-string [str]
  (json/parse-string str true))

(defn url [resource params]
  (format "%s%s" JENKINS_URL
    (format resource params)))

(defn fetch [path & params]
  (client/get
    (apply url (cons path params))
	{:basic-auth [JENKINS_USER JENKINS_TOKEN]}))

(def fetch-body
  (comp :body fetch))

(def fetch-json
  (comp parse-string fetch-body))

(defn string2stream [string]
  (ByteArrayInputStream.
    (.getBytes (.trim string))))

(def fetch-xml
  (comp xml/parse
        string2stream
        fetch-body))

(defn is-failing [job]
  (= "red" (:color job)))

(defmacro ^{:doc "Allows making calls to jenkins with the specified
  connection details.

    (def cnn {:url \"http://localhost\"
              :user \"user\"
              :token \"tokenhere\"})

  And then pass these to the macro.

    (with-jenkins cnn (jobs))

  Uses bindings, so thread local.
"}
  with-jenkins [cnn & body]
  `(binding [JENKINS_URL (:url ~cnn)
             JENKINS_USER (:user ~cnn)
             JENKINS_TOKEN (:token ~cnn)]
     (doall ~@body)))

;; Public
;; ------

(defn jobs []
  (:jobs (fetch-json "/api/json")))

(defn failing-jobs []
  (->> (jobs)
       (filter is-failing)))

(defn job-info [name]
  (fetch-json "/job/%s/api/json" name))

(defn job-config [job-name]
  (fetch-xml (format "/job/%s/config.xml" job-name)))

(defn copy-job
  ([from to] (copy-job from to identity))
  ([from to mutator]
	(xml/emit
      (->> (job-config "scotam-webapp-master")
           (mutator)))))
