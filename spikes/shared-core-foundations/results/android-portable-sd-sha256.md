# Sanitized full-file hashing measurements

> Experimental Android evidence only. This does not select a production hash or scan policy.

- Device label: `android-galaxy-tab-s10-fe-5g-sdcard`
- Runtime: `Python 3.14.6`
- OS: `Android 16`
- Architecture: `aarch64`
- Device model: `Samsung Galaxy Tab S10 FE 5G`
- OS build: `BP4A.251205.006.X528USQU9CZE9`
- Kernel: `6.6.102-android15-8-abX528USQU9CZE9-4k`
- Storage context: `portable SD raw-path access through Termux`
- Filesystem: `Android-exposed FUSE mount, 131072-byte reported blocks`
- Generated at: `2026-07-23T01:25:23.384857+00:00`
- Logical identity: A full-file hash is integrity evidence and is never a logical Track ID.

| Size (bytes) | Chunk (bytes) | Run | Elapsed (s) | CPU (s) | MiB/s | SHA-256 |
|---:|---:|---:|---:|---:|---:|---|
| 1048576 | 1048576 | 1 | 0.006695 | 0.004258 | 149.370 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 1048576 | 1048576 | 2 | 0.007735 | 0.004199 | 129.288 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 1048576 | 1048576 | 3 | 0.009105 | 0.005873 | 109.831 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 16777216 | 1048576 | 1 | 0.047627 | 0.044466 | 335.947 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 16777216 | 1048576 | 2 | 0.031168 | 0.029367 | 513.351 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 16777216 | 1048576 | 3 | 0.032514 | 0.030918 | 492.094 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 67108864 | 1048576 | 1 | 0.124747 | 0.120375 | 513.038 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 67108864 | 1048576 | 2 | 0.118459 | 0.113448 | 540.271 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 67108864 | 1048576 | 3 | 0.116847 | 0.112240 | 547.727 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 268435456 | 1048576 | 1 | 0.481521 | 0.465080 | 531.649 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |
| 268435456 | 1048576 | 2 | 0.445855 | 0.434700 | 574.178 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |
| 268435456 | 1048576 | 3 | 0.434309 | 0.424095 | 589.442 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |

## Resource observations

- Cpu: process CPU time recorded per run; utilization not instrumented
- Memory: not captured by this benchmark
- Battery Or Power: before and after: 40%, unplugged and discharging; charge counter changed from 4096540 to 4086450
- Thermal: battery temperature was 33.9 degrees Celsius before and after; no long-duration thermal measurement was captured
- Cancellation: not captured by this benchmark
