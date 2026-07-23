# Sanitized full-file hashing measurements

> Experimental VPS evidence only. This does not select a production hash or scan policy.

- Device label: `vps-linux-x86_64`
- Runtime: `Python 3.12.13`
- OS: `Linux 5.15.0-185-generic`
- Architecture: `x86_64`
- Generated at: `2026-07-23T00:18:20.010042+00:00`
- Logical identity: A full-file hash is integrity evidence and is never a logical Track ID.

| Size (bytes) | Chunk (bytes) | Run | Elapsed (s) | CPU (s) | MiB/s | SHA-256 |
|---:|---:|---:|---:|---:|---:|---|
| 1048576 | 1048576 | 1 | 0.001427 | 0.001430 | 700.944 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 1048576 | 1048576 | 2 | 0.001495 | 0.001464 | 669.028 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 1048576 | 1048576 | 3 | 0.001616 | 0.001580 | 618.845 | `022213443630fac7fb7976c9da951cc774fa24c1015336911485a706d40ef1fd` |
| 16777216 | 1048576 | 1 | 0.041164 | 0.014953 | 388.690 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 16777216 | 1048576 | 2 | 0.019978 | 0.015957 | 800.884 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 16777216 | 1048576 | 3 | 0.022341 | 0.014582 | 716.160 | `893012b0ba99dfd34fc3d67a6e6566beded12df444cedd965dadfa4fad38929d` |
| 67108864 | 1048576 | 1 | 0.078748 | 0.076539 | 812.715 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 67108864 | 1048576 | 2 | 0.061474 | 0.059045 | 1041.083 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 67108864 | 1048576 | 3 | 0.068369 | 0.066128 | 936.094 | `222bb77cc2557b244dc3990cff745843969a7e4a837f50fd5da01def685e38a6` |
| 268435456 | 1048576 | 1 | 0.734521 | 0.412189 | 348.526 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |
| 268435456 | 1048576 | 2 | 0.277668 | 0.254187 | 921.965 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |
| 268435456 | 1048576 | 3 | 0.256482 | 0.248922 | 998.121 | `870c7f1f798cd11e1877d46bb01acd99bbe2c21f0eb31c58bf6c2368853f04ed` |

## Resource observations

- Cpu: process CPU time recorded per run; utilization not instrumented
- Memory: streaming chunk is bounded; peak process memory not instrumented
- Battery Or Power: not available on this VPS
- Cancellation: KeyboardInterrupt leaves generated files inside an automatically disposed temporary directory
