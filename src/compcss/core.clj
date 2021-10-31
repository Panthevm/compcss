(ns compcss.core)

(import 'java.io.File)

(require 'clojure.java.io)
(require 'clojure.string)

(require 'clj-ph-css.core)
(require 'io.aviso.ansi)
(require 'hawk.core)

(defn css-file?
  "compcss.core-test/test-css-file?"
  [^java.io.File file]
  (clojure.string/ends-with? (str file) ".css"))

(defn file-size
  "compcss.core-test/test-file-size"
  [resource]
  (-> resource clojure.java.io/file .length))

(defn files
  "compcss.core-test/test-files"
  [resource]
  (->> (clojure.java.io/as-file resource)
       (file-seq)
       (filter (memfn ^java.io.File isFile))))

(defn get-css-files
  "compcss.core-test/test-get-css-files"
  [configuration]
  (->>
   (get-in configuration [:input :css])
   (mapcat files)
   (filter css-file?)))

(defn import-stylesheets
  "compcss.core-test/test-import-css"
  [old-stylesheets files]
  (reduce
   (fn [new-stylesheet file]
     (if (contains? new-stylesheet (str file))
       new-stylesheet
       (->>
        (clj-ph-css.core/string->schema (slurp file))
        (assoc new-stylesheet (str file)))))
   old-stylesheets files))

(def message-separator (io.aviso.ansi/green "█"))

(defn import-message
  [configuration db]
  (println
   message-separator
   "CleanCSS"
   message-separator
   (get-in configuration [:output :css])
   message-separator
   "Import"))

(defn export-message
  [configuration db]
  (println
   message-separator
   "CleanCSS"
   message-separator
   (get-in configuration [:output :css])
   message-separator
   "Export"
   message-separator
   (when (::start-time db)
     (format
      "%.03f s"
      (/ (- (. System (currentTimeMillis)) (::start-time db)) 1000.0)))
   message-separator
   (when (::input-size db)
     (format
      "%.03f MB -> %.03f MB"
      (-> (::input-size db)
          (/ (* 1024.0 1024.0)))
      (-> (get-in configuration [:output :css])
          (file-size)
          (/ (* 1024.0 1024.0)))))))

(defn before-middleware
  "compcss.core-test/test-before-middleware"
  [handler]
  (fn [configuration db]
    (import-message configuration db)
    (let [start-time  (System/currentTimeMillis)
          css-files   (get-css-files configuration)
          stylesheets (import-stylesheets (::input-stylesheets db) css-files)]
      (->>
       {::start-time         start-time
        ::input-size         (apply + (map file-size css-files))
        ::input-stylesheets  stylesheets
        ::output-stylesheets (mapcat val stylesheets)}
       (merge db)
       (handler configuration)))))

(defn after-middleware
  "compcss.core-test/test-after-middleware"
  [configuration db]
  (let [output-path (get-in configuration [:output :css])]
    (clojure.java.io/make-parents output-path)
    (->>
     (::output-stylesheets db)
     (clj-ph-css.core/schema->string)
     (spit output-path))
    (export-message configuration db))
  db)

(defn create-watcher
  "compcss.core-test/test-create-watcher"
  [configuration]
  (let [handler (:handler configuration)]
    (hawk.core/watch!
     [{:context (constantly (handler configuration {}))
       :handler (fn [payload event]
                  (handler configuration payload))
       :paths   (into
                 (-> configuration :input :clj)
                 (-> configuration :input :css))}])))

(defn stop-watcher
  [watcher]
  (hawk.core/stop! watcher))
