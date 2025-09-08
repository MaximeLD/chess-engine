#include <jni.h>
#include <stdint.h>
#include "fathom/tbprobe.h"   // Fathom API (bitboards, no structs)

JNIEXPORT jint JNICALL Java_max_chess_engine_tb_syzygy_SyzygyNative_tbInit
  (JNIEnv* env, jclass cls, jstring jpath)
{
    (void)cls;
    const char* path = (*env)->GetStringUTFChars(env, jpath, 0);
    int ok = tb_init(path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return ok;
}

/* WDL: wrapper requires rule50=0 and castling=0; turn: true=white, false=black */
JNIEXPORT jint JNICALL Java_max_chess_engine_tb_syzygy_SyzygyNative_tbProbeWdl
  (JNIEnv* env, jclass cls,
   jlong white, jlong black,
   jlong kings, jlong queens, jlong rooks, jlong bishops, jlong knights, jlong pawns,
   jint rule50_unused, jint castling_unused, jint epSquare, jboolean whiteToMove)
{
    (void)env; (void)cls; (void)rule50_unused; (void)castling_unused;
    const unsigned ep   = (epSquare >= 0 && epSquare <= 63) ? (unsigned)epSquare : 0U;
    const unsigned res = tb_probe_wdl(
        (uint64_t)white, (uint64_t)black,
        (uint64_t)kings, (uint64_t)queens, (uint64_t)rooks,
        (uint64_t)bishops, (uint64_t)knights, (uint64_t)pawns,
        0U, 0U, ep, (whiteToMove ? true : false)); // <- correct turn
    return (jint)res;
}

/* Root: pass the actual castling mask; turn true=white, false=black */
JNIEXPORT jint JNICALL Java_max_chess_engine_tb_syzygy_SyzygyNative_tbProbeRoot
  (JNIEnv* env, jclass cls,
   jlong white, jlong black,
   jlong kings, jlong queens, jlong rooks, jlong bishops, jlong knights, jlong pawns,
   jint rule50, jint castling_unused, jint epSquare, jboolean whiteToMove,
   jintArray jMeta /* [wdl, dtz] */)
{
    (void)env; (void)cls;
    const unsigned ep   = (epSquare >= 0 && epSquare <= 63) ? (unsigned)epSquare : 0U;
    const unsigned res = tb_probe_root(
        (uint64_t)white, (uint64_t)black,
        (uint64_t)kings, (uint64_t)queens, (uint64_t)rooks,
        (uint64_t)bishops, (uint64_t)knights, (uint64_t)pawns,
        (unsigned)rule50, 0U, ep, (whiteToMove ? true : false), NULL);

    if (jMeta && res != TB_RESULT_FAILED) {
        jint meta[2];
        meta[0] = (jint)(TB_GET_WDL(res));
        meta[1] = (jint) TB_GET_DTZ(res);        // DTZ (abs)
        (*env)->SetIntArrayRegion(env, jMeta, 0, 2, meta);
    }
    return (jint)res;
}
