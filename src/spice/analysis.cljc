(ns spice.analysis
  "DC operating-point analysis via Modified Nodal Analysis (MNA) + Gaussian
  elimination with partial pivoting. Restored from kami-spice's `analysis`
  module (deleted PR #82). Matrix ops use plain nested vectors — circuit
  sizes here are node-count scale (tens, not per-pixel/per-frame), matching
  ADR-2607010930's 'coarse-grained dispatch' CLJC scope, not a hot loop.")

(defn- build-node-map
  "Sorted non-ground net name -> matrix index."
  [circuit]
  (let [nodes (->> (:elements circuit)
                    (mapcat (fn [{:keys [kind n1 n2 n-pos n-neg]}]
                              (case kind
                                (:resistor :capacitor :inductor) [n1 n2]
                                (:voltage-source :current-source) [n-pos n-neg]
                                nil)))
                    (remove nil?)
                    (remove #(or (= % "0") (= % "gnd")))
                    (into (sorted-set)))]
    (into {} (map-indexed (fn [i n] [n i]) nodes))))

(defn- node-idx [node-map name]
  (when-not (or (= name "0") (= name "gnd"))
    (get node-map name)))

(defn- mat-set [mat r c v] (assoc-in mat [r c] v))
(defn- mat-get [mat r c] (get-in mat [r c]))
(defn- mat-add [mat r c v] (update-in mat [r c] + v))

(defn- gaussian-eliminate
  "Gaussian elimination with partial pivoting on augmented `size x (size+1)`
  matrix `mat`. Returns the reduced matrix (mutates conceptually — actually
  threads a fresh persistent matrix through)."
  [mat size]
  (reduce
   (fn [mat col]
     (let [max-row (reduce (fn [best row]
                              (if (> (Math/abs (double (mat-get mat row col)))
                                      (Math/abs (double (mat-get mat best col))))
                                row best))
                            col (range (inc col) size))
           mat (if (= max-row col) mat
                 (assoc mat col (nth mat max-row) max-row (nth mat col)))
           pivot (mat-get mat col col)]
       (if (< (Math/abs (double pivot)) 1e-15)
         mat
         (reduce
          (fn [mat row]
            (let [factor (/ (mat-get mat row col) pivot)]
              (reduce (fn [mat j] (mat-add mat row j (- (* factor (mat-get mat col j)))))
                      mat (range col (inc size)))))
          mat (range (inc col) size)))))
   mat (range size)))

(defn- back-substitute [mat size]
  (reduce
   (fn [x i]
     (let [row (nth mat i)
           sum (reduce (fn [s j] (- s (* (nth row j) (nth x j))))
                       (nth row size)
                       (range (inc i) size))
           diag (nth row i)]
       (assoc x i (if (> (Math/abs (double diag)) 1e-15) (/ sum diag) 0.0))))
   (vec (repeat size 0.0))
   (range (dec size) -1 -1)))

(defn solve-dc-op
  "Solve DC operating point via MNA + Gaussian elimination. Returns
  `{:node-voltages {name -> [v]} :branch-currents {vsrc-name -> [i]}
  :time-points []}`. Nonlinear devices (MOSFET/BJT/diode) are ignored in
  this linear DC-op solve (matches the original — they require iterative
  Newton-Raphson, not yet implemented)."
  [circuit]
  (let [node-map (build-node-map circuit)
        n (count node-map)
        vsrcs (filterv #(= :voltage-source (:kind %)) (:elements circuit))
        vsrc-count (count vsrcs)
        size (+ n vsrc-count)
        mat0 (vec (repeat size (vec (repeat (inc size) 0.0))))
        [mat _]
        (reduce
         (fn [[mat vsrc-idx] el]
           (case (:kind el)
             :resistor
             (let [g (/ 1.0 (:value el))
                   a (node-idx node-map (:n1 el))
                   b (node-idx node-map (:n2 el))
                   mat (cond-> mat
                         a (mat-add a a g)
                         b (mat-add b b g)
                         (and a b) (mat-add a b (- g))
                         (and a b) (mat-add b a (- g)))]
               [mat vsrc-idx])

             :voltage-source
             (let [row (+ n vsrc-idx)
                   a (node-idx node-map (:n-pos el))
                   b (node-idx node-map (:n-neg el))
                   mat (cond-> mat
                         a (-> (mat-add row a 1.0) (mat-add a row 1.0))
                         b (-> (mat-add row b -1.0) (mat-add b row -1.0)))
                   mat (mat-set mat row size (:dc-value el))]
               [mat (inc vsrc-idx)])

             :current-source
             (let [a (node-idx node-map (:n-pos el))
                   b (node-idx node-map (:n-neg el))
                   mat (cond-> mat
                         a (mat-add a size (- (:dc-value el)))
                         b (mat-add b size (:dc-value el)))]
               [mat vsrc-idx])

             ;; MOSFET/BJT/diode: nonlinear, skipped in linear DC op
             [mat vsrc-idx]))
         [mat0 0]
         (:elements circuit))
        mat (gaussian-eliminate mat size)
        x (back-substitute mat size)
        node-voltages (into {} (map (fn [[name idx]] [name [(nth x idx)]]) node-map))
        branch-currents (into {}
                               (map-indexed
                                (fn [i vsrc] [(:name vsrc) [(nth x (+ n i))]])
                                vsrcs))]
    {:node-voltages node-voltages :branch-currents branch-currents :time-points []}))
