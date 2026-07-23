# Experimental decision note: shared-core proof harness

- Status: experiment-only, retain for the duration of Phase 1 comparison
- Date: 2026-07-23
- Scope: the disposable proof harness for issues #6 through #10 only

CPython 3.12 plus the standard library was selected because the VPS already had
CPython 3.12.13, equivalent 3.12 runtimes are readily available in Termux and
Windows 11, and the required proof primitives need no third-party dependency.
This keeps the experiment reproducible and allows the exact JSON corpora to run
through one entry point on all three environments.

This note governs this proof harness only. It does not select the application
stack, establish a production framework, make the experimental schemas
canonical, or approve Python for production. It does not close issue #11. It
may be discarded after Phase 1.

