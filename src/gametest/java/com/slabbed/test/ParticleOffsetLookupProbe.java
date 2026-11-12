package com.slabbed.test;

/** Counts real height lookups only during an explicitly bounded test action. */
public final class ParticleOffsetLookupProbe {
    private static final ThreadLocal<int[]> ACTIVE = new ThreadLocal<>();
    public static void begin() { ACTIVE.set(new int[1]); }
    public static void record() { int[] count = ACTIVE.get(); if (count != null) count[0]++; }
    public static int count() { return ACTIVE.get()[0]; }
    public static void end() { ACTIVE.remove(); }
}
