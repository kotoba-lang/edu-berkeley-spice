(ns spice-test
  "Restoration-fidelity tests — one per original kami-spice Rust test
  (kami-engine/kami-spice/src/lib.rs `mod tests`, deleted PR #82)."
  (:require [clojure.test :refer [deftest is testing]]
            [spice]
            [spice.circuit :as circuit]
            [spice.analysis :as analysis]
            [spice.model :as model]
            [spice.netlist :as netlist]))

(deftest namespace-loads
  (testing "the restored CLJC namespace loads"
    (is (some? (the-ns 'spice)))))

;; mirrors `dc_op_voltage_divider`: R1=1k, R2=1k, V1=10V -> V(mid) = 5V
(deftest dc-op-voltage-divider
  (let [ckt (-> (circuit/circuit)
                (circuit/add-element {:kind :voltage-source :name "V1"
                                       :n-pos "in" :n-neg "0" :dc-value 10.0
                                       :ac-mag 0.0 :ac-phase 0.0})
                (circuit/add-element {:kind :resistor :name "R1"
                                       :n1 "in" :n2 "mid" :value 1000.0})
                (circuit/add-element {:kind :resistor :name "R2"
                                       :n1 "mid" :n2 "0" :value 1000.0}))
        result (analysis/solve-dc-op ckt)
        v-mid (first (get-in result [:node-voltages "mid"]))
        v-in (first (get-in result [:node-voltages "in"]))]
    (is (< (Math/abs (- v-mid 5.0)) 1e-9))
    (is (< (Math/abs (- v-in 10.0)) 1e-9))))

;; mirrors `parse_netlist`
(deftest parse-netlist
  (let [nl "Voltage Divider\nV1 in 0 10\nR1 in mid 1k\nR2 mid 0 1k\n.end\n"
        [status ckt] (netlist/parse-spice-netlist nl)]
    (is (= :ok status))
    (is (= 3 (circuit/element-count ckt)))
    (is (= 2 (circuit/node-count ckt)))))

;; mirrors `model_library`
(deftest model-library
  (let [lib (-> (model/library)
                (model/add-mosfet (model/default-nmos "NMOS1"))
                (model/add-bjt (model/default-npn "NPN1"))
                (model/add-diode (model/default-diode "D1")))]
    (is (= 3 (model/model-count lib)))
    (is (some? (model/get-model lib "NMOS1")))
    (is (nil? (model/get-model lib "MISSING")))))

;; mirrors `element_count`
(deftest element-count
  (let [ckt (circuit/circuit)]
    (is (= 0 (circuit/element-count ckt)))
    (let [ckt (-> ckt
                  (circuit/add-element {:kind :resistor :name "R1" :n1 "a" :n2 "b" :value 100.0})
                  (circuit/add-element {:kind :capacitor :name "C1" :n1 "a" :n2 "b" :value 1e-12}))]
      (is (= 2 (circuit/element-count ckt)))
      (is (= 2 (circuit/node-count ckt))))))

;; mirrors `netlist_export_roundtrip`
(deftest netlist-export-roundtrip
  (let [ckt (-> (circuit/circuit)
                (circuit/add-element {:kind :voltage-source :name "V1"
                                       :n-pos "in" :n-neg "0" :dc-value 5.0
                                       :ac-mag 0.0 :ac-phase 0.0})
                (circuit/add-element {:kind :resistor :name "R1" :n1 "in" :n2 "out" :value 1000.0}))
        exported (netlist/export-spice ckt)
        [status reparsed] (netlist/parse-spice-netlist exported)]
    (is (= :ok status))
    (is (= (circuit/element-count ckt) (circuit/element-count reparsed)))))

;; mirrors `dc_op_current_source`: I1=1mA into "a", R1=1k -> V(a) = 1V
(deftest dc-op-current-source
  (let [ckt (-> (circuit/circuit)
                (circuit/add-element {:kind :current-source :name "I1"
                                       :n-pos "0" :n-neg "a" :dc-value 1e-3})
                (circuit/add-element {:kind :resistor :name "R1" :n1 "a" :n2 "0" :value 1000.0}))
        result (analysis/solve-dc-op ckt)
        v-a (first (get-in result [:node-voltages "a"]))]
    (is (< (Math/abs (- v-a 1.0)) 1e-9))))
