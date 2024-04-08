# stemwright

A drill desk for inflectional paradigms.

The thing a learner is drilled on is a **cell**: one lexeme crossed with one
feature bundle, such as `oikos` in `gen.sg`. A cell is not a card. Cells
collapse into one another when the paradigm profile for that inflection class
declares the two bundles syncretic, and profiles are revised over time, so which
cells count as the same answer depends on which revision was in force when the
learner typed it.

Everything downstream inherits that dependency. Credit for a correct answer
propagates to the syncretic twins. Twins get buried out of the same day's queue.
Coverage of a paradigm is counted over distinct surface forms, not over bundles.
A report run against last term's revision does not match one run today.

## Status

Early. The domain layer is being built from the bottom up.
