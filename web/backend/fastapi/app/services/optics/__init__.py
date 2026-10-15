"""DAPE optics services — deterministic image-processing pipelines.

These operate on raw integer/float arrays (no ML framework required) so results
are reproducible and unit-testable. Where a "CNN" is named in the design doc,
we implement the *deterministic signal-processing core* (contrast/attenuation
maps, optical-flow vector fields) and leave the learned weights as a swap-in
point — see each module's docstring.
"""
