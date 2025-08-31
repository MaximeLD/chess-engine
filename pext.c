// pext.c
#include <stdint.h>
#if defined(_MSC_VER)
  #include <immintrin.h>
  #include <intrin.h>
#else
  #include <immintrin.h>
  #include <cpuid.h>
#endif

#ifdef _WIN32
  #define API __declspec(dllexport)
#else
  #define API
#endif

#ifdef __cplusplus
extern "C" {
#endif

API uint64_t pext64(uint64_t src, uint64_t mask) {
    return _pext_u64(src, mask);
}
API uint32_t pext32(uint32_t src, uint32_t mask) {
    return _pext_u32(src, mask);
}

// CPUID leaf 7, EBX bit 8 = BMI2
API int has_bmi2(void) {
#if defined(_MSC_VER)
    int cpuInfo[4] = {0};
    __cpuidex(cpuInfo, 7, 0);
    return (cpuInfo[1] & (1 << 8)) != 0;
#else
    unsigned int a, b, c, d;
    if (__get_cpuid_count(7, 0, &a, &b, &c, &d))
        return (b & (1u << 8)) != 0;
    return 0;
#endif
}

#ifdef __cplusplus
}
#endif
