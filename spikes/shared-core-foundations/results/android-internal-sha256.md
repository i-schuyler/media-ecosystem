# Sanitized full-file hashing measurements

> Experimental Android evidence only. This does not select a production hash or scan policy.

- Device label: `android-galaxy-tab-s10-fe-5g`
- Runtime: `Python 3.14.6`
- OS: `Android 16`
- Architecture: `aarch64`
- Device model: `Samsung Galaxy Tab S10 FE 5G`
- OS build: `BP4A.251205.006.X528USQU9CZE9`
- Kernel: `6.6.102-android15-8-abX528USQU9CZE9-4k`
- Storage context: `Termux-private internal storage`
- Filesystem: `F2FS, 4096-byte filesystem blocks`
- Generated at: `2026-07-23T00:57:39.860858+00:00`
- Logical identity: A full-file hash is integrity evidence and is never a logical Track ID.

| Size (bytes) | Chunk (bytes) | Run | Elapsed (s) | CPU (s) | MiB/s | SHA-256 |
|---:|---:|---:|---:|---:|---:|---|
| 1048576 | 1048576 | 1 | 0.002442 | 0.002391 | 409.541 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 1048576 | 1048576 | 2 | 0.001962 | 0.001970 | 509.787 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 1048576 | 1048576 | 3 | 0.002106 | 0.002060 | 474.936 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 16777216 | 1048576 | 1 | 0.025570 | 0.025256 | 625.737 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 16777216 | 1048576 | 2 | 0.025001 | 0.024632 | 639.969 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 16777216 | 1048576 | 3 | 0.026864 | 0.026370 | 595.589 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 67108864 | 1048576 | 1 | 0.100727 | 0.098875 | 635.382 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 67108864 | 1048576 | 2 | 0.097804 | 0.095751 | 654.368 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 67108864 | 1048576 | 3 | 0.098917 | 0.096646 | 647.009 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 268435456 | 1048576 | 1 | 0.425888 | 0.417175 | 601.098 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |
| 268435456 | 1048576 | 2 | 0.425569 | 0.416441 | 601.548 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |
| 268435456 | 1048576 | 3 | 0.425325 | 0.416942 | 601.893 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |

## Resource observations

- Cpu: process CPU time recorded per run; utilization not instrumented
- Memory: not captured by this benchmark
- Battery Or Power: post-run only: 43%, unplugged and discharging; no pre-run battery delta was captured
- Thermal: post-run battery temperature was 33.9 degrees Celsius; no long-duration thermal measurement was captured
- Cancellation: not captured by this benchmark
