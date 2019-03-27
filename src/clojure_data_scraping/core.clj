(ns clojure-data-scraping.core
  (:require [clj-http.client :as client]
            [reaver :refer [parse extract-from text attr]]
            [etaoin.api :refer [chrome go get-element-inner-html with-driver]]
            [hiccup.page :refer [html5]]))

(defn build-full-url [url]
  (str "https://clojuredocs.org" url))

(defn get-page-html [url]
  (let [full-url (build-full-url url)]
    (with-driver :chrome {:headless true} driver
                 (println (str "Fetching " full-url))
                 (go driver full-url)
                 (get-element-inner-html driver "html"))))

(defn get-links-to-spec-docs []
  (-> (client/get "https://clojuredocs.org/clojure.spec.alpha")
      :body
      parse
      (extract-from ".name"
                    [:name :url]
                    "a" text
                    "a" (attr :href))))

(defn get-number-of-examples [html-page]
  (-> html-page
      parse
      (extract-from "#examples" [:examples-count] "h5" text)
      first
      (update :examples-count #(-> %
                                   (clojure.string/split #" ")
                                   first
                                   Integer.))))

(defn create-report-html [links]
  (let [summary (->> links
                     (group-by :examples-count)
                     (reduce-kv (fn [m k v]
                                  (assoc m k (count v)))
                                {}))]
    (html5 [:div
            [:h1 "Report"]
            [:section
             [:h2 "Summary"]
             [:table
              [:tr
               [:th "# of examples"]
               [:th "# of functions"]]
              (for [line (vec summary)]
                [:tr
                 [:td (first line)]
                 [:td (second line)]])]]
            [:section
             [:h2 "Details"]
             [:table
              [:tr
               [:th "Name"]
               [:th "Link"]
               [:th "# of examples"]]
              (for [link links]
                [:tr (when (= (:examples-count link) 0) {:style "background-color: mistyrose"})
                 [:td (:name link)]
                 [:td [:a {:href (build-full-url (:url link))} "link"]]
                 [:td (:examples-count link)]])]]])))

(defn make-report []
  (let [links (get-links-to-spec-docs)]
    (->> (pmap #(get-page-html (:url %)) links)
         (map get-number-of-examples)
         (map #(merge %1 %2) links)
         create-report-html
         (spit "report.html"))
    (println "Report is ready!")))

;; https://github.com/dakrone/clj-http
;; https://github.com/mischov/reaver
;; https://github.com/igrishaev/etaoin