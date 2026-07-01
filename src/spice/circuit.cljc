(ns spice.circuit
  "SPICE circuit elements + circuit container. Restored from kami-spice's
  `circuit` module (kami-engine/kami-spice/src/lib.rs, deleted PR #82).
  An element is a plain EDN map `{:kind <kind> ...}`; a circuit is
  `{:elements [...] :models {name -> definition-string}}`.")

(def element-kinds
  #{:resistor :capacitor :inductor :voltage-source :current-source
    :mosfet :bjt :diode})

(defn circuit
  "A fresh, empty circuit."
  []
  {:elements [] :models {}})

(defn add-element
  [circuit element]
  (update circuit :elements conj element))

(defn- ground? [n] (or (= n "0") (= n "gnd")))

(defn- element-nodes
  "The (non-ground) net names an element touches."
  [{:keys [kind n1 n2 n-pos n-neg gate drain source bulk collector base emitter anode cathode]}]
  (case kind
    (:resistor :capacitor :inductor) [n1 n2]
    (:voltage-source :current-source) [n-pos n-neg]
    :mosfet [gate drain source bulk]
    :bjt [collector base emitter]
    :diode [anode cathode]))

(defn node-count
  "The number of unique non-ground nodes referenced across `circuit`'s elements."
  [circuit]
  (->> (:elements circuit)
       (mapcat element-nodes)
       (remove ground?)
       (into #{})
       count))

(defn element-count [circuit] (count (:elements circuit)))
