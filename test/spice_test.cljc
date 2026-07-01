(ns spice-test
  (:require [clojure.test :refer [deftest is testing]]
            [spice]))
(deftest namespace-loads
  (testing "the restored CLJC namespace loads"
    (is (some? spice))))
