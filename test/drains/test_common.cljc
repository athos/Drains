(ns drains.test-common)

(def POS_INF
  #?(:clj Double/POSITIVE_INFINITY
     :cljs js/Infinity))

(def NEG_INF
  #?(:clj Double/NEGATIVE_INFINITY
     :cljs js/-Infinity))
