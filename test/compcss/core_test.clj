(ns compcss.core-test)

(require 'compcss.core)
(require 'clj-ph-css.core)

(require 'clojure.test)
(require 'matcho.core)

(import 'java.io.File)
(import 'java.nio.file.Files)
(import 'java.nio.file.attribute.FileAttribute)

(defn- get-directory-as-path
  [directory]
  (-> directory
      (or (System/getProperty "java.io.tmpdir"))
      (str)
      (java.io.File.)
      (.toPath)))

(defn- temporary-file
  [directory prefix suffix]
  (-> (get-directory-as-path directory)
      (java.nio.file.Files/createTempFile
       prefix
       suffix
       (into-array java.nio.file.attribute.FileAttribute []))
      (.toFile)))

(defn- temporary-directory
  [directory prefix]
  (-> (get-directory-as-path directory)
      (Files/createTempDirectory
       prefix
       (into-array java.nio.file.attribute.FileAttribute []))
      (.toFile)))

(clojure.test/deftest test-css-file?
  (def css-1 (temporary-file nil "css-1" ".css"))
  (def clj-1 (temporary-file nil "clj-1" ".clj"))
  (clojure.test/is (compcss.core/css-file? css-1))
  (clojure.test/is (not (compcss.core/css-file? clj-1))))

(clojure.test/deftest test-file-size
  (def css-1 (temporary-file nil "css-1" ".css"))
  (clojure.test/is (zero? (compcss.core/file-size css-1)))
  (spit css-1 "12345")
  (clojure.test/is (= 5 (compcss.core/file-size css-1))))

(clojure.test/deftest test-files
  (def dir-1 (temporary-directory nil "dir-1"))
  (def css-1 (temporary-file dir-1 "css-1" ".css"))
  (def clj-1 (temporary-file dir-1 "clj-1" ".clj"))
  (matcho.core/match
   (set (compcss.core/files dir-1))
   (set [css-1 clj-1]))
  (matcho.core/match
   (set (compcss.core/files clj-1))
   (set [clj-1]))
  (matcho.core/match
   (set (compcss.core/files (str clj-1)))
   (set [clj-1]))
  (matcho.core/match
   (set (compcss.core/files ""))
   (set [])))

(clojure.test/deftest test-import-css
  (def css-1 (temporary-file nil "css-1" ".css"))
  (def css-2 (temporary-file nil "css-2" ".css"))
  (matcho.core/match
   (compcss.core/import-stylesheets {} [css-1 css-2])
   {(str css-1) empty?
    (str css-2) empty?})
  (spit css-1 "a{b:c}")
  (matcho.core/match
   (compcss.core/import-stylesheets {} [css-1 css-2])
   {(str css-2) empty?
    (str css-1) not-empty}))

(clojure.test/deftest test-get-css-files
  (do 
    (def dir-1 (temporary-directory nil "dir-1"))
    (def css-1 (temporary-file dir-1 "css-1" ".css"))
    (def clj-1 (temporary-file dir-1 "clj-1" ".clj"))
    (def css-2 (temporary-file nil "css-2" ".css")))
  (matcho.core/match
   (set (compcss.core/get-css-files
         {:input {:css [(str dir-1)
                        (str css-2)]}}))
   (set [css-1 css-2])))

(clojure.test/deftest test-before-middleware
  (do 
    (def counter (atom 0))
    (def dir-1 (temporary-directory nil "dir-1"))
    (def css-1 (temporary-file dir-1 "css-1" ".css"))
    (def clj-1 (temporary-file dir-1 "clj-1" ".clj"))
    (def sut-configuration
      {:input {:css [(str css-1)]
               :clj [(str dir-1)]}})
    (spit css-1 "a{b:c}")
    (def handler
      (fn [configuration db]
        (swap! counter inc)
        (clojure.test/is (= configuration sut-configuration))
        (matcho.core/match
         db
         {:compcss.core/start-time         integer?
          :compcss.core/input-size         6
          :compcss.core/input-stylesheets  {(str css-1) not-empty}
          :compcss.core/output-stylesheets not-empty})))
    
    ((compcss.core/before-middleware handler) sut-configuration {}))
  (matcho.core/match @counter 1))

(clojure.test/deftest test-after-middleware
  (do 
    (def counter (atom 0))
    (def dir-1 (temporary-directory nil "dir-1"))
    (def css-1 (temporary-file dir-1 "css-1" ".css"))
    (def css-2 (temporary-file dir-1 "css-2" ".css"))
    (def clj-1 (temporary-file dir-1 "clj-1" ".clj"))
    (def sut-configuration
      {:output {:css (str css-2)}})
    (compcss.core/after-middleware
     sut-configuration
     {:compcss.core/output-stylesheets
      (clj-ph-css.core/string->schema "a{b:c}")}))
  (matcho.core/match (slurp css-2) "a{b:c}"))

(clojure.test/deftest test-create-watcher
  (do
    (def counter (atom 0))
    (def dir-1 (temporary-directory nil "dir-1"))
    (def css-1 (temporary-file dir-1 "css-1" ".css"))
    (def dir-2 (temporary-directory nil "dir-2"))
    (def css-2 (temporary-file dir-2 "css-2" ".css"))
    (def dir-3 (temporary-directory nil "dir-3"))
    (def clj-1 (temporary-file dir-3 "clj-1" ".clj"))
    (spit css-1 "a{b:c} d{e:f}")
    (defn remove-first-stylesheet-middleware
      [handler]
      (fn [configuration db]
        (swap! counter inc)
        (->> 
         (update db :compcss.core/output-stylesheets rest)
         (handler configuration))))
    (def watcher
      (compcss.core/create-watcher
       {:input {:css [(str dir-1)]
                :clj [(str dir-3)]}
        :handler
        (->
         compcss.core/after-middleware
         remove-first-stylesheet-middleware
         compcss.core/before-middleware)
        :output {:css (str css-2)}})))
  (matcho.core/match @counter 1)
  (matcho.core/match (slurp css-2) "d{e:f}")
  (.setLastModified clj-1 (long (/ (System/currentTimeMillis) 1000)))
  (Thread/sleep 500)
  (matcho.core/match @counter 2)
  (matcho.core/match (slurp css-2) "d{e:f}")
  (compcss.core/stop-watcher watcher))
