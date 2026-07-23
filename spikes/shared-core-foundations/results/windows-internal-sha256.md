# Sanitized full-file hashing measurements

> Experimental Windows evidence only. This does not select a production hash or scan policy.

- Device label: `windows-surface-book-3`
- Runtime: `Python 3.14.3`
- OS: `Microsoft Windows 11 Pro 25H2 build 26200.8894`
- Architecture: `64-bit OS; X64 process`
- Device model: `Microsoft Surface Book 3`
- OS build: `10.0.26200.8894`
- Storage context: `primary internal system volume`
- Filesystem: `NTFS`
- Generated at: `2026-07-23T21:58:30.364231+00:00`
- Logical identity: A full-file hash is integrity evidence and is never a logical Track ID.

| Size (bytes) | Chunk (bytes) | Run | Elapsed (s) | CPU (s) | MiB/s | SHA-256 |
|---:|---:|---:|---:|---:|---:|---|
| 1048576 | 1048576 | 1 | 0.041271 | 0.015625 | 24.230 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 1048576 | 1048576 | 2 | 0.007518 | 0.000000 | 133.012 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 1048576 | 1048576 | 3 | 0.006000 | 0.015625 | 166.658 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 16777216 | 1048576 | 1 | 0.119363 | 0.046875 | 134.045 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 16777216 | 1048576 | 2 | 0.071939 | 0.062500 | 222.411 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 16777216 | 1048576 | 3 | 0.072605 | 0.078125 | 220.370 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 67108864 | 1048576 | 1 | 0.364113 | 0.328125 | 175.770 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 67108864 | 1048576 | 2 | 0.375133 | 0.375000 | 170.606 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 67108864 | 1048576 | 3 | 0.470796 | 0.421875 | 135.940 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 268435456 | 1048576 | 1 | 1.680415 | 1.531250 | 152.343 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |
| 268435456 | 1048576 | 2 | 1.375560 | 1.343750 | 186.106 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |
| 268435456 | 1048576 | 3 | 1.101029 | 1.078125 | 232.510 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |

## Resource observations

- Cpu: process CPU time recorded per run; utilization not instrumented
- Memory: peak process working set recorded in resource_measurements
- Battery Or Power: AC online and battery 100% before and after the 36-second evidence capture; no battery delta was resolvable
- Thermal: ACPI returned only zero-valued thermal fields and battery temperature was unavailable; no usable temperature measurement
- Cancellation: 64 MiB worker canceled after 0.5 seconds; worker exit 130; no final artifact; temporary cleanup passed
- Peak Process Working Set: 27963392 bytes (PeakWorkingSetSize for the benchmark process)
