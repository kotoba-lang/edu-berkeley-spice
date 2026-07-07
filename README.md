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
per ADR-2607010930, not a per-pixel/per-frame hot loop).

Nonlinear devices (MOSFET/BJT/diode) are now solved via
`spice.analysis/solve-dc-op-nonlinear`, which goes beyond the original
Rust restoration: each nonlinear device is re-linearized into a companion
MNA model (conductance/transconductance + equivalent current source) at
every Newton-Raphson iteration and the resulting linear system is re-solved
with the same Gaussian-elimination code `solve-dc-op` uses. `solve-dc-op`
itself is unchanged — it remains the original purely-linear fast path and
still ignores nonlinear devices entirely.

Stated simplifications of the nonlinear models (educational restore, not
full SPICE):
- **Diode** — Shockley equation only (`Is`/`n`/`Vt`, defaults
  `1e-14`/`1.0`/`0.02585`); no series resistance, no junction capacitance,
  no reverse breakdown. The exponential argument is clamped to `+-40` so
  far-off Newton-Raphson guesses can't overflow.
- **MOSFET** — simple square-law (Level-1-style) model, saturation +
  triode regions, defaults `Vth=0.7`/`Kp=2e-4` (already W/L-scaled — W/L
  is not tracked separately). **No channel-length modulation** (`lambda`
  is not modeled) and **no body effect**. Gate is ideal (zero DC current).
- **BJT** — simplified Ebers-Moll, **forward-active region only** (no
  saturation/cutoff/reverse-active regions, no Early effect), defaults
  `Is=1e-16`/`beta=100`/`Vt=0.02585`.
- **No temperature sweep** — thermal voltage `Vt` is a fixed room-temperature
  constant throughout (no `.temp`/`.dc temp` support).

Convergence uses damped Newton (each node's per-iteration voltage step is
capped, empirically chosen to avoid the oscillation a plain undamped
update produces when the V=0 initial guess puts a diode/BJT junction far
out on its exponential) with a tolerance of `1e-6` V and a `100`-iteration
safety cap; the result map adds `:iterations`/`:converged?` on top of
`solve-dc-op`'s `{:node-voltages :branch-currents :time-points}` shape.

## Develop

```bash
clojure -M:test
```
