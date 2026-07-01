# kotoba-lang/spice

Zero-dep portable `.cljc` — restored from the legacy `kami-engine/kami-spice`
Rust crate (deleted in kotoba-lang/kami-engine PR #82 "Remove Rust workspace
from kami-engine") as part of the **clj-wgsl migration** (ADR-2607010930,
`com-junkawasaki/root`).

Modified Nodal Analysis (MNA) based SPICE simulator.

| Namespace | Restored from | Purpose |
|---|---|---|
| `spice.circuit` | `circuit` | Circuit elements (R/C/L/V/I/MOSFET/BJT/diode) + circuit container |
| `spice.analysis` | `analysis` | DC operating-point solver (MNA + Gaussian elimination w/ partial pivoting) |
| `spice.model` | `model` | MOSFET/BJT/diode compact-model parameters + named model library |
| `spice.netlist` | `netlist` | SPICE netlist parser/exporter, engineering-notation values ("1k" -> 1000.0) |

Depends on `kotoba-lang/engineer` for shared contracts (constraint/DRC/etc).

## Status

Restored — all 4 modules ported from the original 809-line Rust `lib.rs`,
with all 5 original Rust unit tests mirrored 1:1 in `test/spice_test.cljc`
(+1 smoke test). Pure data + pure functions throughout; matrix ops use
plain nested vectors (circuit sizes are node-count scale — coarse-grained
per ADR-2607010930, not a per-pixel/per-frame hot loop). Nonlinear devices
(MOSFET/BJT/diode) are not yet solved in DC-op — matches the original,
which also left Newton-Raphson iteration as future work.

## Develop

```bash
clojure -M:test
```
