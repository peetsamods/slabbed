package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.compat.CompatHooks;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChiseledBookshelfBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.KelpPlantBlock;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.CaveVinesBodyBlock;
import net.minecraft.block.CaveVinesHeadBlock;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.HangingRootsBlock;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.SporeBlossomBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Central helper for slab support semantics.
 */
public final class SlabSupport {
    private static final Long2DoubleOpenHashMap CLIENT_VISUAL_Y_OFFSETS = new Long2DoubleOpenHashMap();
    private static final Object CLIENT_VISUAL_Y_OFFSETS_LOCK = new Object();
    private static volatile Predicate<BlockView> CHUNK_RENDERER_REGION_DETECTOR = ignored -> false;

    private SlabSupport() {
    }

    /**
     * Registers the client-only renderer-region type check without linking this common class to
     * client-only Minecraft classes. The client supplies an {@code instanceof} predicate, whose
     * class reference Loom remaps for the active runtime namespace.
     */
    public static void registerChunkRendererRegionDetector(Predicate<BlockView> detector) {
        CHUNK_RENDERER_REGION_DETECTOR = Objects.requireNonNull(detector, "detector");
    }

    /**
     * Returns true if the block is a THIN top-layer block (snow layers, carpet, pale moss carpet).
     *
     * <p>This is a pure THICKNESS statement and is used only where thickness is the question: a
     * cell one-sixteenth of a block tall cannot TRANSMIT a support's top face upward, so the
     * bounded column walks ({@link #hasSlabInColumn}, {@link #slabColumnYOffset},
     * {@link #hasLoweringSourceInColumnBelow}) terminate on it — whatever rests on a carpet rests
     * on the carpet, not on the slab underneath it.
     *
     * <p><b>It is NOT a lowering-eligibility test.</b> It used to be one, at three SUBJECT-side
     * sites; that was a live-confirmed regression (2026-08-06): a carpet read {@code dy 0.0} while
     * the stone it was lying on read {@code -0.5}, floating half a block in the air. Eligibility
     * now follows GEOMETRY alone, per the binding maintainer ruling of 2026-08-06: everything
     * should be able to lower, no exceptions — see {@code LAW.md}.
     *
     * <p><b>THE SNOW EXCLUSION IS GONE TOO (2026-08-06, second ruling) — read this before adding
     * another one.</b> {@code 112d1449} replaced the classname family with a narrower BEHAVIOUR
     * predicate, {@code isEnvironmentDepositedSurfaceFill} ({@code Properties.LAYERS} plus
     * {@code PowderSnowBlock}), to keep the one hazard {@code 8d3f105f} (2026-02-10, "prevent
     * client offset of thin top-layer blocks") and {@code 135d125f} (2026-06-10, powder snow)
     * actually closed: weather lays snow across whole biomes, so if it lowered wherever a slab
     * happened to lie beneath, half a continuous snowy surface would render at {@code -0.5} and
     * half at {@code 0.0} — a step through terrain the player never placed, cannot see the cause
     * of, and cannot align. A second maintainer ruling (2026-08-06, live-confirmed: powder snow
     * staying flush over a {@code -0.5} slab) removed that exclusion as well. The predicate, and
     * the separate {@code PowderSnowBlock} short-circuit at the head of {@link #getYOffset}, were
     * both deleted.
     *
     * <p><b>What closes the hazard now: geometry, not a block list.</b> The bounded column walks
     * ({@link #hasSlabInColumn}, {@link #slabColumnYOffset}) stop dead on the first
     * {@code isOpaqueFullCube} below the subject — the L5 world-hole guard, added by
     * {@code 8d1b42ef} AFTER the powder-snow exclusion and closing the same hazard class
     * generally. Snow lying on grass/dirt/stone/snow_block therefore cannot see a slab buried
     * under that terrain at all, however deep it is. Only snow resting DIRECTLY on a slab (or on a
     * lowered non-terrain object) lowers, and there it is geometrically right: it seats on the
     * surface it lies on instead of floating in vanilla's half-block gap. The exclusion had become
     * a narrower, hand-listed duplicate of a guard the codebase already had.
     *
     * <p><b>The three snow ids after the change.</b> {@code minecraft:snow} (this class, LAYERS)
     * and {@code minecraft:powder_snow} lower when they rest directly on a slab-height surface.
     * {@code minecraft:snow_block} is an ordinary opaque full cube, was never in either predicate,
     * and behaves exactly like stone — including staying flush on top of other terrain, which is
     * the world-hole guard, not a snow rule.
     *
     * <p><b>The residual reachable case</b> — Terrain Slabs, which turns natural terrain into
     * half-height surfaces world-wide, so weather snow lands directly on "slabs" across whole
     * biomes — is tracked for a live judgement. Pinned by
     * {@code ThinTopLayerLoweringTest}: three cells were INVERTED by this ruling, and
     * {@code #snowLayerOverNaturalTerrainStaysFlush} pins the guard that replaces them.
     */
    public static boolean isThinTopLayer(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SnowBlock
                || block instanceof CarpetBlock
                || block instanceof PaleMossCarpetBlock;
    }

    /**
     * Returns true if the block at {@code pos} is a slab whose top face can provide support.
     */
    public static boolean isSupportingSlab(WorldView world, BlockPos pos) {
        return isSupportingSlab(getBlockStateOrAir(world, pos));
    }

    /** Overload for BlockView contexts (shapes). */
    public static boolean isSupportingSlab(BlockView world, BlockPos pos) {
        return isSupportingSlab(getBlockStateOrAir(world, pos));
    }

    /**
     * Returns true if the state is a slab with a defined type.
     */
    public static boolean isSupportingSlab(BlockState state) {
        if (CompatHooks.shouldSkipSlabSupport(state)) {
            return false;
        }
        return state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE);
    }

    /** True if this state is a bottom slab. */
    public static boolean isBottomSlab(BlockState state) {
        return isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /**
     * True when Slabbed's placement-support override must not make the surface
     * eligible for ON_GROUND mob spawning.
     *
     * <p>This intentionally includes compatible custom bottom-like slabs, while
     * leaving top-like and double-like surfaces to their normal spawn predicates.
     */
    public static boolean isSpawnProofBottomLikeSurface(BlockState state) {
        return isBottomSlab(state)
                || CompatHooks.customSlabSurfaceKind(state) == CompatSlabSurfaceKind.BOTTOM_LIKE;
    }

    /** True if this state is a top slab. */
    public static boolean isTopSlab(BlockState state) {
        return isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.TOP;
    }

    /**
     * Single source of truth: returns true iff the state is a TOP slab
     * and the queried face is DOWN (i.e. the underside of a top slab).
     */
    public static boolean isTopSlabUndersideSupport(BlockState state, Direction face) {
        return face == Direction.DOWN && isTopLikeCeilingSurface(state);
    }

    /**
     * True for bottom-like custom slabs when vanilla asks whether the visible
     * underside can carry a small hanging attachment such as a lantern.
     */
    public static boolean isBottomLikeSlabUndersideHookSupport(BlockState state, Direction face) {
        return face == Direction.DOWN
                && CompatHooks.customSlabSurfaceKind(state) == CompatSlabSurfaceKind.BOTTOM_LIKE;
    }

    /** True if the block at {@code posAbove} is a top or double slab that can provide ceiling support. */
    public static boolean isCeilingSupportBottomSurface(WorldView world, BlockPos posAbove) {
        BlockState stateAbove = getBlockStateOrAir(world, posAbove);
        if (isTopLikeCeilingSurface(stateAbove)) {
            return true;
        }
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.get(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** Overload for shape/world views. */
    public static boolean isCeilingSupportBottomSurface(BlockView world, BlockPos posAbove) {
        BlockState stateAbove = getBlockStateOrAir(world, posAbove);
        if (isTopLikeCeilingSurface(stateAbove)) {
            return true;
        }
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.get(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** True if the block immediately below {@code pos} is a bottom slab providing its top face. */
    public static boolean hasBottomSlabBelow(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState below = getBlockStateOrNull(world, pos.down());
        return below != null && isBottomSlab(below);
    }

    public static boolean isDirectObjectSupportSurface(BlockView world, BlockPos pos, BlockState state) {
        return getDirectObjectSupportTopOffset(state) > 0.0;
    }

    public static boolean isDirectCustomSlabSupportedObject(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isDirectCustomSlabSupportSubject(world, pos, state)) {
            return false;
        }

        if (state.contains(Properties.BED_PART) && state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BedPart part = state.get(Properties.BED_PART);
            BlockPos otherPos = part == BedPart.FOOT
                    ? pos.offset(facing)
                    : pos.offset(facing.getOpposite());
            return hasDirectCustomBottomLikeSupportColumn(world, pos.down())
                    || hasDirectCustomBottomLikeSupportColumn(world, otherPos.down());
        }

        BlockPos supportPos = pos.down();
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            BlockState lowerState = getBlockStateOrAir(world, pos.down());
            if (lowerState.getBlock() != state.getBlock()
                    || !lowerState.contains(Properties.DOUBLE_BLOCK_HALF)
                    || lowerState.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
                return false;
            }
            supportPos = pos.down(2);
        }

        return hasDirectCustomBottomLikeSupportColumn(world, supportPos);
    }

    public static double getDirectObjectSupportTopOffset(BlockState state) {
        return switch (CompatHooks.customSlabSurfaceKind(state)) {
            case BOTTOM_LIKE -> 0.5;
            case TOP_LIKE, DOUBLE_LIKE -> 1.0;
            case NONE, UNKNOWN -> {
                if (isBottomSlab(state)) {
                    yield 0.5;
                }
                if (isTopSlab(state) || isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
                    yield 1.0;
                }
                yield 0.0;
            }
        };
    }

    /** Debug toggle (-Dslabbed.disableStepCull=true) to disable the step-face cull relaxation. */
    private static final boolean STEP_CULL_DISABLED = Boolean.getBoolean("slabbed.disableStepCull");

    /**
     * Gametest-only instrumentation for {@link #isSlabHeightStepFace}'s height-resolution budget.
     *
     * <p>{@code isSlabHeightStepFace} runs on the chunk-render hot path, up to four times per
     * block per section compile, and this project has shipped a lag regression twice. The
     * load-bearing perf property is therefore that ordinary terrain resolves ZERO heights — and a
     * property that important is pinned by a gametest, not by review
     * ({@code SlabHeightStepCullTest#fastPathsResolveZeroHeights}).
     *
     * <p>Ambient cost in a shipped build: the flag is never armed, so the counter is never
     * written; all that remains is one uncontended {@code volatile boolean} read, and only on the
     * branch that is already about to walk two block columns. The zero-resolution fast paths
     * never even reach the read.
     */
    private static volatile boolean stepCullHeightResolutionCounting;
    private static final AtomicLong STEP_CULL_HEIGHT_RESOLUTIONS = new AtomicLong();

    /** Arms {@link #stepCullHeightResolutionCount} and resets it to zero. Gametest use only. */
    public static void beginStepCullHeightResolutionCount() {
        STEP_CULL_HEIGHT_RESOLUTIONS.set(0L);
        stepCullHeightResolutionCounting = true;
    }

    /** Heights resolved by {@link #isSlabHeightStepFace} since {@link #beginStepCullHeightResolutionCount}. */
    public static long stepCullHeightResolutionCount() {
        return STEP_CULL_HEIGHT_RESOLUTIONS.get();
    }

    /** Disarms the counter. Always call from a {@code finally}. */
    public static void endStepCullHeightResolutionCount() {
        stepCullHeightResolutionCounting = false;
    }

    /**
     * True if the {@code direction} side face of the opaque cube {@code state} at
     * {@code pos} should be DRAWN even though the chunk mesher culls it, because exactly
     * one of this block and its {@code direction} neighbour is lowered — i.e. they sit at
     * different visual heights and the slab step exposes part of the shared face (the
     * see-through "window" / "doom-infinity" hole on a lowered cube in a terrace).
     *
     * <p>"Lowered" here is EITHER a {@link #isDirectCustomSlabSupportedObject} (an object
     * resting directly on a Terrain-Slabs-owned custom surface) OR a persistently-{@link
     * SlabAnchorAttachment#isAnchored anchored} opaque full cube — the ordinary "full block
     * placed on a slab" case that is this mod's own core, documented product intent,
     * which the original direct-custom-support-only check did not cover (a
     * live goblin-test session found ~80 see-through-hole diagnostic hits on plain anchored
     * dirt, none of them Terrain-Slabs-related). {@code isAnchored} is a cheap O(1) chunk-
     * attachment set lookup — NOT the deep column walk in {@link #getYOffset} — and already
     * has a {@code ChunkRendererRegion}-safe fallback path, so it is safe to call from this
     * per-face chunk culling hot path exactly like {@link #isDirectCustomSlabSupportedObject}.
     *
     * <p>GH#24 (github.com/peetsamods/slabbed/issues/24): the original subject/neighbour gate
     * required {@code state.isOpaqueFullCube()}, which a DOUBLE slab satisfies but a BOTTOM or
     * TOP slab never does — so a slab's OWN face toward a lowered full-block neighbour was never
     * evaluated at all (only the neighbour's face toward the slab was), leaving one side of the
     * seam invisible. Widened to also accept slab states so a slab can itself be the subject or
     * the neighbour of this check, same as an opaque full cube already was.
     *
     * <p>MAGNITUDE (live-confirmed 2026-08-06). "Lowered" is a boolean, but a height step is a
     * DIFFERENCE: two ANCHORED cubes at −1.0 and −0.5 both read lowered, so the old
     * {@code selfLowered != neighborLowered} evaluated {@code true != true} = false and culled a
     * real 0.5 seam. Commit {@code 3a3f57e7} fixed this on the TS-compat line with a dy
     * difference; this line never received it and independently evolved the boolean form, gaining
     * the slab widening above and losing magnitude. Both properties are kept: the boolean
     * prefilter still answers first, and the heights are compared only in the cases it cannot
     * answer (see the tiers below).
     *
     * <p>ELIGIBILITY IS NOT ANCHOR STATUS (maintainer ruling 2026-08-06 — everything should be
     * able to lower, no exceptions; see {@code LAW.md} — after a second live re-test still showed
     * see-through holes: dy −0.5 beside dy −1.0, {@code anchor=none} on BOTH,
     * {@code src=geometric}). The tier-1/2/3 dispatch above keys on
     * {@link #isLoweredOpaqueFullCubeForStepCull}, an ANCHOR BOOLEAN — so two geometrically
     * lowered, never-anchored cubes both read "not lowered", landed in tier 1, and the magnitude
     * comparison was never reached. That is the same bug class as the three fixed before it: an
     * anchor boolean standing in for "is this lowered", when geometric lowering exists with no
     * anchor at all. Tier 4 below closes it by ACTUAL RESOLVED HEIGHT.
     *
     * <p>PERFORMANCE — this is the chunk-render hot path, up to four calls per block per section
     * compile, and a lag regression has shipped twice. Height resolution ({@link #getYOffset}) is
     * a bounded walk but NOT free (a slab's lowering test allocates a BFS queue), and
     * {@link #getVisualYOffset} takes a global monitor, so it is reached only when it can change
     * the answer. Four tiers:
     * <ol>
     *   <li>NEITHER side carries a cheap "definitely lowered" marker AND neither passes the cheap
     *       structural {@link #mayBeLoweredForStepCull} screen — ordinary terrain, the
     *       overwhelming majority — false, ZERO heights resolved;</li>
     *   <li>exactly one side definitely lowered — true, ZERO heights resolved; the old boolean
     *       answer, kept verbatim;</li>
     *   <li>BOTH sides definitely lowered (rare) — resolve both and compare magnitude;</li>
     *   <li>NEITHER definitely lowered but at least one passes the structural screen (a slab, or a
     *       cube whose support could lower it) — resolve ONLY the screened side(s); a side the
     *       screen rejected is known-flush and contributes 0.0 without a resolution.</li>
     * </ol>
     * For a non-slab opaque full cube the screen is one {@code pos.down()} + {@code getBlockState}
     * plus a handful of precomputed, view-independent state flags — one short-lived {@code BlockPos}
     * per screened side, the same allocation class as the {@code pos.offset(direction)} this method
     * already made, and no reflection, logging, or collection allocation at all. So plain terrain (a
     * solid cube standing on a plain, flush, unanchored solid cube) still resolves ZERO heights — pinned by
     * {@code SlabHeightStepCullTest#fastPathsResolveZeroHeights}. Miss path (screen says "maybe"):
     * one {@link #getVisualYOffset} per screened side, i.e. AT MOST 2 per face — the same read
     * {@code OffsetBlockStateModel#emitQuads} already performs once per block, so the worst case
     * is a bounded constant multiple of an already-paid per-block cost, never an unbounded walk.
     *
     * <p>ONLY-ADDS (strict superset), re-derived for the four-tier form: tiers 2 and 3 are the old
     * tiers 2 and 3, byte-identical, so every input that returned {@code true} before still does.
     * The ONLY input class whose handling changed is "neither side definitely lowered", where the
     * old form unconditionally returned {@code false}; tier 4 either reproduces that {@code false}
     * or upgrades it to {@code true}. So no {@code true} can become {@code false}: this method can
     * only ever ADD faces, never cull one that is drawn today. Note this argument does NOT depend
     * on {@link #mayBeLoweredForStepCull} being a perfect superset of "dy ≠ 0" — a screen that is
     * wrongly negative merely leaves a face undrawn exactly as today (an under-draw, i.e. the
     * status quo), and can never remove a face. Tiers 2/3 are deliberately NOT merged into the
     * height comparison: merging them could flip an old {@code true} to {@code false} when an
     * anchored block and an unanchored-but-equally-lowered neighbour resolve to the same dy.
     *
     * <p>The height read is {@link #getVisualYOffset} — the same read the model path
     * ({@code OffsetBlockStateModel#emitQuads} → {@code YOffsetEmitter}) shifts the quads by, and
     * both cull paths share this predicate. Reading {@link #getYOffset} directly would bypass the
     * published per-position visual value that the mesh worker's {@code ChunkRendererRegion}
     * actually renders with, so the un-culled face could be decided from a different height than
     * the geometry it belongs to. It is also the cheaper read on the render worker (a cache hit
     * skips the column walk entirely).
     */
    public static boolean isSlabHeightStepFace(BlockView world, BlockPos pos, BlockState state, Direction direction) {
        if (STEP_CULL_DISABLED || world == null || pos == null || state == null || direction == null
                || !direction.getAxis().isHorizontal() || !isStepCullEligibleSubject(state)) {
            return false;
        }
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighbor = getBlockStateOrNull(world, neighborPos);
        if (neighbor == null || !isStepCullEligibleSubject(neighbor)) {
            return false;
        }
        boolean selfLowered = isLoweredOpaqueFullCubeForStepCull(world, pos, state);
        boolean neighborLowered = isLoweredOpaqueFullCubeForStepCull(world, neighborPos, neighbor);
        // Tier 2 — exactly one side DEFINITELY lowered: a step exists by construction, no magnitude
        // needed. ZERO heights resolved, and the old boolean answer is preserved verbatim, which is
        // half of what makes this method a strict superset of its previous results.
        if (selfLowered != neighborLowered) {
            return true;
        }
        // Tier 3 — both definitely lowered. The boolean form said "no step" here and was wrong
        // whenever the two lowerings differ in DEPTH (−1.0 beside −0.5 = a real 0.5 seam).
        if (selfLowered) {
            return stepHeightsDiffer(world, pos, state, neighborPos, neighbor, true, true);
        }
        // NEITHER side carries an anchor / TS-direct-support marker. The old form stopped here and
        // returned false, which is exactly how the live −0.5(anchor=none) | −1.0(anchor=none) pair
        // kept its culled seam. Screen both sides structurally first — that is what keeps tier 1
        // free — and resolve only the sides the screen could not rule out.
        boolean selfMaybe = mayBeLoweredForStepCull(world, pos, state);
        boolean neighborMaybe = mayBeLoweredForStepCull(world, neighborPos, neighbor);
        // Tier 1 — ordinary terrain. Nothing here can be lowered, so no step. ZERO heights resolved.
        if (!selfMaybe && !neighborMaybe) {
            return false;
        }
        // Tier 4 — geometric lowering. A screened-out side is known flush and contributes 0.0
        // WITHOUT a resolution, so a lowered cube beside plain terrain costs one resolution, not two.
        return stepHeightsDiffer(world, pos, state, neighborPos, neighbor, selfMaybe, neighborMaybe);
    }

    private static boolean stepHeightsDiffer(BlockView world, BlockPos pos, BlockState state,
                                             BlockPos neighborPos, BlockState neighbor,
                                             boolean resolveSelf, boolean resolveNeighbor) {
        double selfDy = resolveSelf ? resolveStepCullDy(world, pos, state) : 0.0;
        double neighborDy = resolveNeighbor ? resolveStepCullDy(world, neighborPos, neighbor) : 0.0;
        return Math.abs(selfDy - neighborDy) > 1.0e-6;
    }

    private static double resolveStepCullDy(BlockView world, BlockPos pos, BlockState state) {
        if (stepCullHeightResolutionCounting) {
            STEP_CULL_HEIGHT_RESOLUTIONS.incrementAndGet();
        }
        return getVisualYOffset(world, pos, state);
    }

    private static boolean isStepCullEligibleSubject(BlockState state) {
        return state.isOpaqueFullCube() || (state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE));
    }

    /**
     * Cheap, view-independent, O(1)-lookup "this block is DEFINITELY lowered" marker: an object
     * resting directly on a Terrain-Slabs-owned custom surface, or a persistently
     * {@link SlabAnchorAttachment#isAnchored anchored} opaque full cube / slab (the ordinary "full
     * block placed on a slab" case that is this mod's own core product intent).
     *
     * <p>It is a SUFFICIENT condition, never a necessary one — geometric lowering carries no
     * anchor at all. Used only to pick the dispatch tier in {@link #isSlabHeightStepFace}; the
     * "is this block eligible for lowering behaviour" question is answered by resolved HEIGHT
     * (tier 4), never by this boolean (see {@code LAW.md}: everything should be able to lower).
     */
    private static boolean isLoweredOpaqueFullCubeForStepCull(BlockView world, BlockPos pos, BlockState state) {
        return isDirectCustomSlabSupportedObject(world, pos, state)
                || ((state.isOpaqueFullCube() || state.getBlock() instanceof SlabBlock)
                        && SlabAnchorAttachment.isAnchored(world, pos));
    }

    /**
     * Structural screen for tier 4 of {@link #isSlabHeightStepFace}: could this
     * {@link #isStepCullEligibleSubject eligible} block possibly resolve to a non-zero
     * {@link #getVisualYOffset}, WITHOUT resolving it? Conservative by design — it answers
     * "maybe" whenever it cannot cheaply prove "no", and a wrong "no" costs only an undrawn face
     * (the status quo), never a wrongly culled one.
     *
     * <p>NON-SLAB OPAQUE FULL CUBE (the terrain case that must stay free). Enumerating the lowering
     * lanes {@code getYOffsetInner} can take for such a state, each needs something the block
     * BELOW must supply:
     * <ul>
     *   <li>anchor lane — excluded by the caller (tier 4 runs only when
     *       {@link #isLoweredOpaqueFullCubeForStepCull} is false);</li>
     *   <li>frozen-flat lane — returns a hard {@code 0.0}, screened out below;</li>
     *   <li>gap-fill and cantilever lanes — both gated on {@code getBlockState(pos.down()).isAir()};</li>
     *   <li>{@code directCustomSlabSupportDy} / {@code loweredCuratedCarrierDy} — need a Terrain
     *       Slabs surface, or a curated {@code isSlabSitCandidate} carrier, directly below;</li>
     *   <li>{@code shouldOffset} → {@code hasSlabInColumn} — its own natural-terrain stop returns
     *       false on the FIRST iteration when the block below is a plain, unanchored, non-slab
     *       opaque full cube, so the column can never reach a slab deeper down;</li>
     *   <li>every remaining branch is guarded by {@code state.isOpaqueFullCube() → return 0.0}.</li>
     * </ul>
     * So a plain opaque cube standing on a plain, flush, unanchored, non-curated opaque cube is
     * provably at dy 0 — one {@code getBlockState} plus a few precomputed state flags decides it,
     * and no height is resolved. Everything else answers "maybe".
     *
     * <p>SLAB SUBJECT. A slab's dy can come from a purely LATERAL source
     * ({@code isAdjacentSideSlabLowered} BFSs sideways through the connected slab chain), so no
     * bounded look at the block below is a sound screen for it. A slab therefore always answers
     * "maybe" unless it is frozen-flat. HONEST COST: an unanchored slab pair now pays up to two
     * {@link #getVisualYOffset} calls per face where it previously paid none. Slabs are a
     * vanishing fraction of terrain blocks (worldgen places none), the calls are the same ones
     * the model path already makes once per block, and the alternative — re-deriving a cheap
     * lateral superset here — would be a second copy of the BFS's stop rules and would rot out of
     * sync with it (the shared-predicate half-fix trap). Revisit only if a live FPS check bites.
     */
    private static boolean mayBeLoweredForStepCull(BlockView world, BlockPos pos, BlockState state) {
        // FREEZE-ON-PLACE is a hard 0.0 in both the slab and non-slab lanes of getYOffsetInner,
        // read immediately after the anchor lane the caller already ruled out.
        if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock) {
            return true;
        }
        // Bed / double-block halves resolve their support from a DIFFERENT cell than pos.down()
        // (the other half, or two blocks down), so the below-only reasoning does not apply.
        if (state.contains(Properties.BED_PART) || state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return true;
        }
        BlockPos belowPos = pos.down();
        BlockState below = getBlockStateOrNull(world, belowPos);
        return below == null                                        // outside the render region
                || below.isAir()                                    // gap-fill / cantilever lanes
                || below.getBlock() instanceof SlabBlock            // any slab support
                || !below.isOpaqueFullCube()                        // the column walk can pass through
                || getDirectObjectSupportTopOffset(below) > 0.0     // a Terrain Slabs custom surface
                || SlabAnchorAttachment.isAnchored(world, belowPos) // a lowered support below
                || isSlabSitCandidate(world, belowPos, below);      // GH#22 curated carrier
    }

    /**
     * Primary query: should this slab top face count as solid support.
     */
    public static boolean canTreatAsSolidTopFace(WorldView world, BlockPos pos) {
        BlockState state = getBlockStateOrAir(world, pos);
        return isSupportingSlab(state) || isDirectObjectSupportSurface(world, pos, state);
    }

    /** Overload for shape/world views. */
    public static boolean canTreatAsSolidTopFace(BlockView world, BlockPos pos) {
        BlockState state = getBlockStateOrAir(world, pos);
        return isSupportingSlab(state) || isDirectObjectSupportSurface(world, pos, state);
    }

    /** Max blocks to walk down when checking chain offset. */
    private static final int MAX_CHAIN_DEPTH = 16;

    /** Recursion guard: prevents StackOverflow when isSolidBlock triggers getOutlineShape → getYOffset. */
    private static final ThreadLocal<Boolean> IN_GET_Y_OFFSET = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> IGNORE_STORED_PLACEMENT_DY =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * ROLE, NOT CLASSNAME: true iff the block at {@code pos} — <em>in this state, right now</em> —
     * actually hangs from the cell above it, and must therefore keep following that support instead
     * of being height-locked to a support below.
     *
     * <p>The INTENT of this predicate is unchanged and is correct: a lantern on a chain, a hanging
     * sign, cave vines genuinely hang from above, and if the block above sits lower they must sit
     * lower with it. What was wrong was the IMPLEMENTATION — it was a list of block TYPES, so every
     * class that <em>can</em> be ceiling-mounted matched in <em>every</em> state. A lever on the
     * FLOOR matched {@code LeverBlock}; a floor button matched {@code ButtonBlock}; a chain
     * STANDING on a block matched (a chain is Y-axis either way); a TOP-half trapdoor matched even
     * though it is hinged to a side block and needs nothing above it. None of those hang from
     * anything, yet all were denied a height anchor and left deriving their height live from the
     * block BELOW them — so they popped the instant that block was broken. That was S-2's standing
     * RED (`chain_on_lowered_support_ceiling_scenery`, −0.5 → 0.0 on `break_directly_below`) and it
     * is LAW 2's exclude-by-BEHAVIOUR rule: ask what the block IS doing, never what its class could
     * do.
     *
     * <p>Three answer shapes, in order of how much context each family needs:
     * <ol>
     *   <li><b>Intrinsic</b> — hangs by definition in every state it has: {@code HANGING=true}
     *       (lantern), hanging sign, cave vines head/body, spore blossom, hanging roots. No query;
     *       their behaviour is deliberately left exactly as it was.</li>
     *   <li><b>Decided by the block's own vanilla property</b> — the class is ambiguous but the
     *       state is not: lever/button carry {@link Properties#BLOCK_FACE} (only {@code CEILING}
     *       hangs), a bell carries {@link Properties#ATTACHMENT} (only {@code CEILING} hangs), and
     *       pointed dripstone carries {@link Properties#VERTICAL_DIRECTION} (only the DOWN-pointing
     *       stalactite hangs; an UP-pointing stalagmite stands on the floor).</li>
     *   <li><b>Decided by the world</b> — vanilla gives a Y-axis chain and a TOP-half trapdoor no
     *       property at all that separates hung from standing, so these two ask
     *       {@link #hasCeilingSupportAbove}: is there actually something up there to attach to?</li>
     * </ol>
     */
    public static boolean isCeilingAttached(BlockView world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        // ── 1. Intrinsic hangers: true in every state, no query, behaviour deliberately unchanged.
        // HANGING property (lanterns): the floor variant is HANGING=false and was never matched.
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING)) {
            return true;
        }
        Block block = state.getBlock();
        if (block instanceof HangingSignBlock
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || block instanceof CaveVinesHeadBlock
                || block instanceof CaveVinesBodyBlock) {
            return true;
        }
        // ── 2. The block's own vanilla property already answers it.
        // Pointed dripstone: DOWN = stalactite (hangs), UP = stalagmite (stands on the floor).
        if (block instanceof PointedDripstoneBlock) {
            return state.contains(Properties.VERTICAL_DIRECTION)
                    && state.get(Properties.VERTICAL_DIRECTION) == Direction.DOWN;
        }
        // Lever / button: FLOOR / WALL / CEILING. Only CEILING hangs; a floor lever or floor
        // button rests on the block below and must be allowed to lock to it.
        if (block instanceof LeverBlock || block instanceof ButtonBlock) {
            return state.contains(Properties.BLOCK_FACE)
                    && state.get(Properties.BLOCK_FACE) == BlockFace.CEILING;
        }
        // Bell: FLOOR / CEILING / SINGLE_WALL / DOUBLE_WALL. Only CEILING hangs.
        if (block instanceof BellBlock) {
            return state.contains(Properties.ATTACHMENT)
                    && state.get(Properties.ATTACHMENT) == Attachment.CEILING;
        }
        // ── 3. No property distinguishes hung from standing — ask the world.
        if (isVerticalChain(state) || isTopHalfTrapdoor(state)) {
            return hasCeilingSupportAbove(world, pos);
        }
        return false;
    }

    /** A Y-axis chain: the shape that is identical whether it hangs or stands. */
    private static boolean isVerticalChain(BlockState state) {
        return state.getBlock() instanceof ChainBlock
                && state.contains(Properties.AXIS)
                && state.get(Properties.AXIS) == Direction.Axis.Y;
    }

    /** A TOP-half (ceiling-mounted) trapdoor: sits flush with the underside of the cell above. */
    private static boolean isTopHalfTrapdoor(BlockState state) {
        return state.getBlock() instanceof TrapdoorBlock
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.TOP;
    }

    /**
     * The world query behind role case 3: is there anything above this cell to attach to?
     *
     * <p>Walks up through a run of same-family members (a chain is normally a column of chains; a
     * ceiling trapdoor stack is a column of trapdoors — see {@code c611b60f}, which added TOP-half
     * trapdoors precisely so a 2× stack under a top slab both raise together). The run's TERMINATOR
     * is the answer: open air above means nothing to hang from, so the subject is STANDING; any
     * real block caps the run, so the subject hangs from it.
     *
     * <p>Two deliberate tie-breaks, both chosen to preserve genuinely-hanging behaviour:
     * <ul>
     *   <li>A chain that is <em>both</em> seated on a floor and capped by a ceiling is called
     *       hanging — vanilla renders the two identically, and answering "hanging" keeps the
     *       pre-existing result for that ambiguous cell rather than inventing a new one.</li>
     *   <li>A {@code null} state (outside a render region — see {@link #getBlockStateOrNull}) is
     *       treated as a cap for the same reason: when we cannot see the ceiling we must not
     *       downgrade a real hanger into a floor mount.</li>
     * </ul>
     * Depth is capped by {@link #MAX_CHAIN_DEPTH}, matching every other column walk in this file;
     * only the two ambiguous families ever reach it, so no other subject pays for it.
     */
    private static boolean hasCeilingSupportAbove(BlockView world, BlockPos pos) {
        BlockPos cursor = pos.up();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState above = getBlockStateOrNull(world, cursor);
            if (above == null) {
                return true;
            }
            if (above.isAir()) {
                return false;
            }
            if (isVerticalChain(above) || isTopHalfTrapdoor(above)) {
                cursor = cursor.up();
                continue;
            }
            return true;
        }
        return true;
    }

    /**
     * Master check: should the block at {@code pos} with state {@code state}
     * be visually offset by −0.5 Y?
     *
     * <p>Handles:
     * <ul>
     *   <li><b>Direct</b> — block directly above a bottom slab.</li>
     *   <li><b>Chain (recursive)</b> — block on top of a stack of non-air,
     *       non-slab blocks that ultimately sits on a bottom slab. Fixes
     *       stacking at any height (signs on fences on slabs, 3+ towers).</li>
     *   <li><b>Double-block</b> — upper half checks 2 blocks down.</li>
     *   <li><b>Bed</b> — both halves offset when <em>either</em> half has a
     *       bottom slab below.</li>
     * </ul>
     *
     * @return true if the block should receive a −0.5 Y offset
     */
    public static boolean shouldOffset(BlockView world, BlockPos pos, BlockState state) {
        // never offset slabs themselves
        if (state.getBlock() instanceof SlabBlock) {
            return false;
        }

        // NOTE (2026-08-06, second ruling): there is no longer a snow exclusion here. The
        // environment-deposited-fill guard that stood at this line was removed with its two
        // siblings — see isThinTopLayer's javadoc for the full history and for what carries the
        // snowy-terrain hazard now (the opaque-full-cube natural-terrain stop in the column walks).
        if (CompatHooks.shouldSkipOffset(state)) {
            return false;
        }

        // A Terrain-Slabs-owned on-top object over a BOTTOM_LIKE Terrain surface gets exactly ONE
        // offset — Terrain Slabs' own — so this column gate must answer false for it whether the
        // surface below is native or AUTHORED. An authored Terrain slab carries a stored height
        // and a placement anchor, and without this consult the column walk read that anchor as a
        // lowering source and double-offset the object by -0.5 (an ownership answer that depended
        // on surface authorship). Not a block-class exclusion: ownership is asked of Terrain
        // Slabs' own on-top authority, and every non-owned object keeps every lane
        // (TerrainOwnedOnTopConsistencySuite pins both sides).
        if (isTerrainOwnedOnTopObject(world, pos, state)) {
            return false;
        }

        // blocks under a top slab that get +0.5 UP via getYOffset should not
        // also get -0.5 DOWN. Use isCeilingAttached here (safe, no shape calcs)
        // since shouldOffset is called from paths outside the recursion guard.
        if (isCeilingAttached(world, pos, state)
                && isTopLikeCeilingSurface(getBlockStateOrAir(world, pos.up()))) {
            return false;
        }

        // ceiling-attached blocks further down a chain of ceiling blocks
        // leading to a top slab also get +0.5 UP; exclude from -0.5
        if (isCeilingAttached(world, pos, state)) {
            BlockPos cursor = pos.up();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = getBlockStateOrAir(world, cursor);
                if (isTopLikeCeilingSurface(cur)) {
                    return false;
                }
                if (isCeilingAttached(world, cursor, cur)) {
                    cursor = cursor.up();
                    continue;
                }
                break;
            }
        }

        // blocks hanging from above (lanterns, etc.) — don't offset DOWN by slab below
        // (they may get a separate +0.5 UP offset via getYOffset)
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING)) {
            return false;
        }

        // Always-ceiling-hung decorations (hanging roots, spore blossom, hanging signs, pale
        // hanging moss) attach to the block ABOVE; a slab BELOW them must NOT lower them. They
        // lack the HANGING property, so the guard above misses them, and under a FLUSH (non
        // top-like) ceiling the ceiling-chain exclusions above don't fire either — so they fell
        // through to hasSlabInColumn (a carrier placed beneath bridged the downward column-walk
        // to a slab, wrongly lowering them -0.5). Their real dy is computed by
        // ceilingHungDecorationDy in getYOffsetInner.
        if (isAlwaysCeilingHungDecoration(state)) {
            return false;
        }

        // ── bed: either half has a slab ───────────────────────────────
        if (state.contains(Properties.BED_PART)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BedPart part = state.get(Properties.BED_PART);
            BlockPos otherPos;
            if (part == BedPart.FOOT) {
                otherPos = pos.offset(facing);
            } else {
                otherPos = pos.offset(facing.getOpposite());
            }
            return hasSlabInColumn(world, pos) || hasSlabInColumn(world, otherPos);
        }

        // ── double-block: upper half checks two blocks down ───────────
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.UPPER) {
                return isBottomSlab(getBlockStateOrAir(world, pos.down(2)));
            }
            return hasBottomSlabBelow(world, pos);
        }

        // ── direct + recursive chain ──────────────────────────────────
        // Intended product behavior: ordinary full blocks may anchor to slab
        // columns as long as this remains in slab context.
        if (hasSlabInColumn(world, pos)) {
            return true;
        }

        // ── wall-attached blocks: check the block they're mounted on ──
        Block block = state.getBlock();
        if ((block instanceof WallSignBlock || block instanceof WallBannerBlock
                || block instanceof WallTorchBlock || block instanceof WallHangingSignBlock)
                && state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BlockPos attachedPos = pos.offset(facing.getOpposite());
            if (hasSlabInColumn(world, attachedPos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the Y offset for the block at {@code pos}.
     * <ul>
     *   <li>{@code -0.5} for blocks sitting above a bottom slab (or chain), compounding to
     *       {@code -1.0} for mixed/stacked lanes.</li>
     *   <li>{@code 0.0} otherwise (no offset). The old {@code +0.5} ceiling reach-up is
     *       DEPRECATED (2026-07-03 ruling) — ceiling-hung blocks sit flush.</li>
     * </ul>
     */
    public static double getYOffset(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null) {
            return 0.0;
        }
        // A TS-owned (shouldSkipOffset) SLAB's lowered dy is normally decided PURELY LIVE by
        // isAdjacentCustomSideSlabLowered — a BFS through the connected slab chain, recomputed
        // on every call. That BFS never consults this position's own PERSISTED anchor, so once
        // this early-return fires, the anchor lane deeper in getYOffsetInner is never reached at
        // all. The live "breaking the middle slab pops the far one up" bug: slab B is anchored
        // (qualifiesForLoweredSideSlabAnchor recorded the chain reaching slab A at PLACEMENT
        // time), then A is broken — the BFS can no longer reach a source, so this gate returns
        // 0.0 unconditionally, silently overriding B's own recorded anchor and defeating the
        // exact "survive a later neighbor change" guarantee anchors exist to provide. Scoped to
        // SLABS only (not every shouldSkipOffset block) — this is specifically the slab-chain
        // cantilever law, not a blanket exemption from "TS owns this block's offset".
        if (CompatHooks.shouldSkipOffset(state)
                && !SlabPlacementDyAttachment.hasStoredDy(world, pos)
                && !isAdjacentCustomSideSlabLowered(world, pos, state)
                && !(state.getBlock() instanceof SlabBlock && SlabAnchorAttachment.isAnchored(world, pos))) {
            return 0.0;
        }

        // NOTE (2026-08-06, second ruling): the PowderSnowBlock short-circuit that stood at this
        // line (135d125f) is gone. Powder snow now resolves like any other full cell of block:
        // lowered when it rests directly on a slab, flush when it rests on natural terrain (the
        // column walks' opaque-full-cube stop). See isThinTopLayer's javadoc.

        // Recursion guard: isSolidBlock → getCollisionShape → getOutlineShape (mixin) → getYOffset
        if (IN_GET_Y_OFFSET.get()) {
            return 0.0;
        }
        IN_GET_Y_OFFSET.set(Boolean.TRUE);
        try {
            return getYOffsetInner(world, pos, state);
        } finally {
            IN_GET_Y_OFFSET.set(Boolean.FALSE);
        }
    }

    /**
     * Computes the placement-time height without reading the cell's prior frozen value.
     *
     * <p>This is only for the transaction that has just let vanilla publish a final state. Normal
     * rendering, targeting, collision, and gameplay reads must use {@link #getYOffset} so the
     * frozen value remains authoritative.
     */
    public static double getUnstoredYOffset(BlockView world, BlockPos pos, BlockState state) {
        boolean previous = IGNORE_STORED_PLACEMENT_DY.get();
        IGNORE_STORED_PLACEMENT_DY.set(Boolean.TRUE);
        try {
            return getYOffset(world, pos, state);
        } finally {
            IGNORE_STORED_PLACEMENT_DY.set(previous);
        }
    }

    /**
     * Shared client visual-dy authority.
     *
     * <p>Main-thread client-world callers compute and publish the dy for {@code pos}.
     * Render-worker callers such as {@code ChunkRendererRegion} prefer that
     * published value, so chunk meshes, outline/raycast, and block-entity render use
     * the same per-position visual decision. A cache miss falls back to the
     * underlying calculation to preserve first-render behavior; dependent rerender
     * hooks prewarm the affected region before scheduling rebuilds.
     */
    public static double getVisualYOffset(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return 0.0;
        }
        if (isClientWorld(world)) {
            double dy = getYOffset(world, pos, state);
            // The cache may only hold values a REAL resolution produced. While this thread is
            // already resolving some other cell (the re-entrancy guard is active), a shape read
            // of a probed cell lands here and the getYOffset above answered the guard's 0.0 —
            // not this cell's dy. Publishing that transient answer poisons the render-worker
            // read path for the probed cell (drawn flush while lowered).
            // VisualDyCacheReentrantPublishClientGameTest pins this.
            if (!IN_GET_Y_OFFSET.get()) {
                putClientVisualYOffset(pos, dy);
            }
            return dy;
        }
        Double cached = cachedClientVisualYOffset(pos);
        if (cached != null) {
            return cached;
        }
        return getYOffset(world, pos, state);
    }

    /**
     * The real (uncollapsed) visual dy of a fence/wall/pane for connection-arm purposes.
     * Unlike the old model-render guard, this always reports {@link #getYOffset} — a fence
     * lowered on a vanilla slab is not treated differently from one on a Terrain Slabs
     * surface. See {@link #isSteppedConnectingNeighbor}: it is what prevents lowering the
     * model from drawing an illegal connector arm across the resulting height step.
     */
    public static double connectingBlockVisualDy(BlockView world, BlockPos pos, BlockState state) {
        return getYOffset(world, pos, state);
    }

    /**
     * True if {@code neighborState} is a fence/wall/pane sitting at a different visual
     * height than {@code state} — i.e. one was lowered onto a slab and the other was not.
     * Such a pair must stay as single posts instead of drawing a connector arm across the
     * height step. Cross-family joins (fence↔wall, pane↔glass, fence↔solid, …) are left
     * alone because the neighbour is not a connecting block here.
     */
    public static boolean isSteppedConnectingNeighbor(BlockView world, BlockPos pos, BlockState state,
                                                       BlockPos neighborPos, BlockState neighborState) {
        // The connecting family (fence/wall/pane/GATE) is defined ONCE in
        // SlabAnchorAttachment.isConnectingStructural — do not re-enumerate a subset here (an
        // adversarial audit found fence GATE silently dropped from this walk, so a fence/wall arm
        // was never broken across a height step toward a lowered gate).
        if (!SlabAnchorAttachment.isConnectingStructural(neighborState)) {
            return false;
        }
        double selfDy = connectingBlockVisualDy(world, pos, state);
        double neighborDy = connectingBlockVisualDy(world, neighborPos, neighborState);
        return Math.abs(selfDy - neighborDy) > 1.0e-6;
    }

    public static void refreshVisualYOffsetRegion(BlockView world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!isClientWorld(world)) {
            return;
        }
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    mutable.set(x, y, z);
                    BlockState state = getBlockStateOrNull(world, mutable);
                    if (state == null) {
                        removeClientVisualYOffset(mutable);
                    } else {
                        putClientVisualYOffset(mutable, getYOffset(world, mutable, state));
                    }
                }
            }
        }
    }

    public static void clearVisualYOffsetCache() {
        synchronized (CLIENT_VISUAL_Y_OFFSETS_LOCK) {
            CLIENT_VISUAL_Y_OFFSETS.clear();
        }
    }

    private static boolean isClientWorld(BlockView world) {
        return world instanceof World w && w.isClient();
    }

    private static void putClientVisualYOffset(BlockPos pos, double dy) {
        synchronized (CLIENT_VISUAL_Y_OFFSETS_LOCK) {
            CLIENT_VISUAL_Y_OFFSETS.put(pos.asLong(), dy);
        }
    }

    private static void removeClientVisualYOffset(BlockPos pos) {
        synchronized (CLIENT_VISUAL_Y_OFFSETS_LOCK) {
            CLIENT_VISUAL_Y_OFFSETS.remove(pos.asLong());
        }
    }

    private static Double cachedClientVisualYOffset(BlockPos pos) {
        synchronized (CLIENT_VISUAL_Y_OFFSETS_LOCK) {
            long packed = pos.asLong();
            return CLIENT_VISUAL_Y_OFFSETS.containsKey(packed)
                    ? CLIENT_VISUAL_Y_OFFSETS.get(packed)
                    : null;
        }
    }

    /**
     * Read-only peek at the client-side visual-dy cache for {@code pos}, for diagnostics only
     * ({@code /slabdy}). Returns {@code null} on a cache miss. Never writes, never falls back to
     * a fresh computation — callers that want the live value should call {@link #getYOffset}
     * directly and compare the two, which is exactly what a stale-cache bug looks like.
     */
    public static Double peekCachedClientVisualYOffset(BlockPos pos) {
        return cachedClientVisualYOffset(pos);
    }

    /** Bounded depth used by the client dependent-rerender pass (Fix 3). */
    public static int chainRerenderDepth() {
        return MAX_CHAIN_DEPTH;
    }

    public static boolean isLoweredSideSlabVisual(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (world == null
                || slabPos == null
                || slabState == null
                || !(slabState.getBlock() instanceof SlabBlock)
                || !slabState.contains(SlabBlock.TYPE)
                || !slabState.getFluidState().isEmpty()) {
            return false;
        }
        // Vertical support (a slab resting directly on a lowered TOP/DOUBLE slab or lowered full
        // block below): mirrors isVerticallyLoweredSlabSource, the SAME live check
        // getYOffsetInner's slab branch already uses (line ~918) to derive -0.5 for this exact
        // relationship. Without this, a slab resting on a lowered support rendered correctly at
        // placement time purely via that live derivation, but never persisted an anchor for it —
        // breaking the support later popped it flush even though it was never re-placed (live-
        // reported "pop upon breaking at the end").
        return isVerticallyLoweredSlabSource(world, slabPos, slabState)
                || isAdjacentSideSlabLowered(world, slabPos, slabState);
    }

    /**
     * LEAN gate for the client dependent-rerender mixin: true iff a bottom slab
     * or a persistent anchor lies in the bounded column directly below {@code pos}
     * (i.e. {@code pos} sits in a lowered column, so a change at/near it can shift
     * the rendered dy of stacked dependents above). Deliberately avoids the
     * adjacency / shape-triggering checks in {@link #hasSlabInColumn} so it is
     * cheap to call on every client block change.
     */
    public static boolean hasLoweringSourceInColumnBelow(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = getBlockStateOrNull(world, cursor);
            if (cur == null) {
                return false;
            }
            if (isBottomSlab(cur) || SlabAnchorAttachment.isAnchored(world, cursor)) {
                return true;
            }
            // ROOT CAUSE A, REMOVED (maintainer ruling, live-confirmed 2026-08-09): this branch
            // used to treat a Terrain Slabs BOTTOM_LIKE surface as a column lowering source,
            // reasoning "TS lowers everything stacked above it just like a vanilla bottom slab."
            // That premise was live-tested and found FALSE for the case that actually matters
            // here: a PLAIN SOLID FULL BLOCK resting on TS. isSlabSitCandidate already explicitly
            // excludes plain solid cubes from the one lane where TS legitimately lowers something
            // onto it (directCustomSlabSupportDy, the curated standing-object compat lane — still
            // correct, still live-confirmed, untouched by this fix). With this branch present, a
            // plain block placed via a real click on bare TS anchored -0.5 server-side while the
            // client's geometric read (this same function, minus this branch) stayed flush — the
            // disagreement was the live-reported snap-down, a LAW 1 violation. The branch fired on
            // the FIRST loop iteration (a TS slab found DIRECTLY below), not only deeper in a
            // column, so it was never only the narrower "deep column" case its comment described.
            // If a genuine deep-column TS interaction needs its own anchor treatment, it needs its
            // own live-verified fixture — this removal does not attempt to re-derive one.
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isThinTopLayer(cur)) {
                return false;
            }
            cursor = cursor.down();
        }
        return false;
    }

    /**
     * Returns true if {@code slabPos} holds any slab variant (BOTTOM, TOP, or
     * DOUBLE) that is adjacent to a solid full block sitting on a bottom slab —
     * the exact condition that gives the slab its -0.5 adjacent-side-slab dy.
     * Does not call getYOffset (safe inside the IN_GET_Y_OFFSET recursion guard).
     *
     * <p>BOTTOM → BS-FB-0.5S (slab visually fills the lower half beside the
     * lowered FB, world Y span [pos.y - 0.5, pos.y]).
     * <br>TOP → BS-FB-1S (slab visually fills the upper half beside the lowered
     * FB, world Y span [pos.y, pos.y + 0.5]).
     * <br>DOUBLE → full-cube alignment with the lowered FB.
     */
    private static boolean isAdjacentSideSlabLowered(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (!slabState.contains(SlabBlock.TYPE)) {
            return false;
        }
        // FLUSH-SEAT GUARD (live pass 2026-08-05, "interpenetration row"): side-
        // adjacency lowering is legal only when the destination volume is free. A BOTTOM/DOUBLE
        // slab at -0.5 occupies the upper half of the cell below it, so when its DIRECT support is
        // a flush full-height seat it may not qualify — it would render half inside the very block
        // it was clicked onto (the mega board's z=14 anchored stone_slab inside flush stone).
        // Folded INTO the predicate, not one call site, so the placement lane
        // (getYOffsetInner's slab-adjacency branch), the anchor qualifier
        // (isLoweredSideSlabVisual -> qualifiesForLoweredSideSlabAnchor) and every recursion-safe
        // mirror refuse TOGETHER instead of drifting apart. A TOP slab is exempt: at -0.5 it fills
        // the LOWER half of its own cell and rests ON the flush seat (the BS-FB-1S alignment) —
        // its destination volume is free.
        if (followerSinksIntoFlushSeat(slabState)) {
            BlockPos seatPos = slabPos.down();
            BlockState seatState = getBlockStateOrNull(world, seatPos);
            if (seatState != null && isFlushSeat(world, seatPos, seatState)) {
                return false;
            }
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(slabPos);
        visited.add(slabPos.asLong());

        while (!queue.isEmpty() && visited.size() <= MAX_CHAIN_DEPTH) {
            BlockPos current = queue.removeFirst();
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighborPos = current.offset(dir);
                BlockState neighbor = getBlockStateOrNull(world, neighborPos);
                if (neighbor == null) {
                    continue;
                }
                if (isLoweredSideSlabSource(world, neighborPos, neighbor)) {
                    return true;
                }
                if (neighbor.getBlock() instanceof SlabBlock
                        && neighbor.contains(SlabBlock.TYPE)
                        && visited.add(neighborPos.asLong())) {
                    queue.addLast(neighborPos);
                }
            }
        }
        return false;
    }

    /**
     * Recursion-safe live check: does {@code pos} have a horizontal neighbour that is itself
     * lowered (by anchor or column-source) and is a solid full block, a connecting-structural
     * block (fence/wall/pane/gate — see {@link SlabAnchorAttachment#isConnectingStructural}), OR
     * a block entity (hopper/chest/…)? Determines the neighbour's lowering from its sources
     * instead of {@code getYOffset}, so it is safe to call inside the {@code IN_GET_Y_OFFSET}
     * guard. Used to lower a cantilevered side-placed block live, before its own anchor syncs.
     *
     * <p>Renamed from {@code isAdjacentToLoweredFullBlock}: a live WYSIWYG bug (a fence/pane
     * placed beside an EXISTING lowered fence/pane, air below, froze FLAT/detached instead of
     * inheriting the neighbour's dy) traced to this filter excluding non-solid connecting-block
     * neighbours entirely — only full-cube neighbours were ever recognised as a lowering source.
     * Widened again for block entities (hopper/chest chained horizontally beside a lowered
     * block entity placed upward at vanilla height) for the identical reason.
     */
    private static boolean isAdjacentToLoweredSupport(BlockView world, BlockPos pos) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighbor = getBlockStateOrNull(world, neighborPos);
            if (neighbor == null || neighbor.isAir()) {
                continue;
            }
            Block block = neighbor.getBlock();
            if (block instanceof SlabBlock) {
                continue;
            }
            boolean solidNeighbor = neighbor.isSolidBlock(world, neighborPos);
            boolean connectingNeighbor = SlabAnchorAttachment.isConnectingStructural(neighbor);
            boolean blockEntityNeighbor = block instanceof BlockEntityProvider;
            if (!solidNeighbor && !connectingNeighbor && !blockEntityNeighbor) {
                continue;
            }
            if (SlabAnchorAttachment.isAnchored(world, neighborPos)
                    || hasLoweringSourceInColumnBelow(world, neighborPos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoweredSideSlabSource(BlockView world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof SlabBlock) {
            // A custom slab may resolve as lowered for itself, but it must not propagate that
            // offset horizontally into a neighboring vanilla slab.
            if (CompatHooks.shouldSkipOffset(state)) {
                return false;
            }
            return isVerticallyLoweredSlabSource(world, pos, state);
        }
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }
        if (hasBottomSlabBelow(world, pos)
                || SlabAnchorAttachment.isAnchored(world, pos)
                || isDirectCustomSlabSupportedObject(world, pos, state)) {
            return true;
        }
        // A support full block can also be lowered by its own COLUMN (a slab lower in the stack)
        // or by ADJACENCY (air below, beside another lowered full block — the getYOffsetInner:941
        // cantilever path, which renders -0.5 with anchor=none). A slab cantilevered off such a
        // support must follow it down too, or it reads 0.0 and freezeLoweredOnPlace locks it flat
        // (the live "cantilever placed 0.5 too high" bug). Both mirrors are recursion-safe.
        double ownDy = loweredFullHeightSupportDy(world, pos, state);
        if (Double.isFinite(ownDy) && ownDy < -1.0e-6) {
            return true;
        }
        return getBlockStateOrAir(world, pos.down()).isAir() && isAdjacentToLoweredSupport(world, pos);
    }

    private static boolean isVerticallyLoweredSlabSource(BlockView world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return true;
        }
        BlockPos belowPos = pos.down();
        BlockState below = getBlockStateOrNull(world, belowPos);
        return below != null
                && (hasLoweredNonSlabTopSupport(world, belowPos, below)
                || hasLoweredSlabSupport(world, belowPos, below));
    }

    private static boolean hasLoweredNonSlabTopSupport(BlockView world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (hasBottomSlabBelow(world, pos) || SlabAnchorAttachment.isAnchored(world, pos)) {
            return true;
        }
        double directCustomDy = directCustomSlabSupportDy(world, pos, state);
        if (Double.isFinite(directCustomDy) && directCustomDy < -1.0e-6) {
            return true;
        }
        return shouldOffset(world, pos, state) && slabColumnYOffset(world, pos) < -1.0e-6;
    }

    /**
     * True iff the SLAB at {@code pos} is a support that is itself rendering LOWERED, so a block
     * resting on it must follow it down.
     *
     * <p>Renamed from {@code hasLoweredTopLikeSlabSupport} — it no longer means "top-like". The old
     * name encoded the old bug: a BOTTOM slab was rejected UNCONDITIONALLY, on type, so "a vanilla
     * slab resting on a LOWERED vanilla BOTTOM slab" had no lane anywhere. That case fell between
     * this reject and {@link #hasLoweredNonSlabTopSupport}'s {@code instanceof SlabBlock} reject —
     * neither asking whether the support was actually sunk — and, since {@code shouldOffset} never
     * offsets slabs, landed on the class-based flush guard's hardcoded {@code 0.0} (live-confirmed:
     * a slab reading {@code dy=0.000} on an ANCHORED {@code -0.5} slab whose correct value is
     * {@code -1.0}). Per the maintainer ruling of 2026-08-06 (see {@code LAW.md} — everything
     * should be able to lower, no exceptions), eligibility follows GEOMETRY, never block type. It
     * is the same bug class as {@code b89c1f38}: a type/membership test standing in for a
     * magnitude.
     *
     * <p><b>EXCLUSION #13 RULED OUT (2026-08-06)</b>. The first pass of the rename above qualified
     * a BOTTOM-slab support only when it was itself ALREADY SUNK. Asked whether a slab resting on
     * a PLAIN bottom slab should stay flat at 0.0 — vanilla's half-block gap — the maintainer
     * ruled it lowers (WYSIWYG law). A bottom slab's top face sits half a block below the grid
     * <em>whether or not the slab itself is sunk</em>, so ANY block resting on it — including
     * another slab — must seat there. The "must already be sunk" condition is therefore gone: a
     * BOTTOM slab is unconditionally a lowering support, exactly as a Terrain Slabs BOTTOM_LIKE
     * surface already was.
     *
     * <p>No new depth math, and no new call: the answer comes from the existing resolver.
     * {@link #supportSeatDy} classifies a bottom slab as a HALF-HEIGHT seat (its own dy − 0.5), so
     * a plain bottom slab at {@code 0.0} yields {@code -0.5} — precisely the ruling's value — and
     * a sunk one still compounds. This arm is now strictly CHEAPER than before: it no longer calls
     * {@link #loweredBottomSlabSupportDy} at all, which matters because bottom slabs are the most
     * common support in the game and this predicate is reached from the render path.
     *
     * <p>Deliberately ONE edit in this shared helper so the render lane
     * ({@link #getYOffsetInner}'s slab branch) and the persistence qualifier
     * ({@link #isVerticallyLoweredSlabSource} → {@link #isLoweredSideSlabVisual} →
     * {@code qualifiesForLoweredSideSlabAnchor}) can never drift apart (shared-predicate law). Both
     * must move together here: a slab that RENDERS lowered on a plain bottom slab but records no
     * anchor would pop flush the moment its support is broken (never-pop law).
     *
     * <p>The FLUSH-SEAT guard is untouched and does not reopen: a BOTTOM slab is a half-height
     * seat, which {@link #isFlushSeat} has always excluded by construction ("A BOTTOM slab is never
     * a flush seat"). The guard's subject is a FLUSH FULL-BLOCK seat — flush stone, or a flush
     * TOP/DOUBLE slab — and none of those reach this arm.
     *
     * <p>Recursion-safe: never calls {@link #getYOffset}, and this arm now recurses not at all.
     */
    private static boolean hasLoweredSlabSupport(BlockView world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (isBottomSlab(state)) {
            // EXCLUSION #13: a bottom slab's top face is half a block below the grid, always.
            // supportSeatDy turns that into the follower's actual dy (support dy - 0.5).
            return true;
        }
        BlockPos belowPos = pos.down();
        BlockState below = getBlockStateOrAir(world, belowPos);
        return SlabAnchorAttachment.isAnchored(world, pos)
                || hasLoweredNonSlabTopSupport(world, belowPos, below)
                || isAdjacentSideSlabLowered(world, pos, state);
    }

    private static boolean isAdjacentCustomSideSlabLowered(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (!(slabState.getBlock() instanceof SlabBlock)
                || CompatHooks.customSlabSurfaceKind(slabState) == CompatSlabSurfaceKind.NONE) {
            return false;
        }
        return isAdjacentSideSlabLowered(world, slabPos, slabState);
    }

    /**
     * True for an always-ceiling-hung decoration — hanging roots, spore blossom, hanging signs,
     * pale hanging moss. These attach to the block ABOVE and have no floor variant, so their dy
     * must be a pure function of that support and must never be lowered by a block below them in
     * the column. Chains and pointed dripstone are deliberately NOT here: chains extend to reach
     * their support (ruling of record), and the speleothem family keeps its own merge grammar.
     * Lanterns are NOT here either: a standing lantern legitimately rests on a support, so it
     * keeps the normal path (HANGING lanterns are already excluded from the below-walk).
     */
    public static boolean isAlwaysCeilingHungDecoration(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof HangingRootsBlock
                || block instanceof SporeBlossomBlock
                || block instanceof HangingSignBlock
                || isPaleHangingMossBlock(state);
    }

    /**
     * Rendered dy for an always-ceiling-hung decoration, decided SOLELY by the support directly
     * ABOVE — never by any block below — so it cannot be dragged down by a carrier lower in the
     * column. Under a lowered authored support it follows the immutable support dy (a TOP slab
     * adds the +0.5 raised-attach baseline so it sits flush, not 0.5 too low). An unstored custom
     * slab stays in its compatibility owner's native coordinate space. Under a normal top-like
     * ceiling (directly or via a chain of ceiling-attached blocks) it floats +0.5, mirroring the
     * existing tail ceiling lanes; otherwise flush. Uses only the recursion-safe lowered-support
     * mirrors (never re-enters {@link #getYOffset}), so it is safe inside the
     * {@code IN_GET_Y_OFFSET} guard.
     */
    private static double ceilingHungDecorationDy(BlockView world, BlockPos pos, BlockState state) {
        BlockPos supportPos = pos.up();
        BlockState above = getBlockStateOrAir(world, supportPos);
        boolean authoredCustomSupport = CompatHooks.shouldSkipOffset(above)
                && SlabPlacementDyAttachment.hasStoredDy(world, supportPos);
        if (!CompatHooks.shouldSkipOffset(above) || authoredCustomSupport) {
            double slabSupportDy = loweredSlabUndersideSupportDy(world, supportPos, above);
            if (Double.isFinite(slabSupportDy) && slabSupportDy < -1.0e-6) {
                // A TOP slab's underside sits half a block higher than a hanger's natural
                // attach (support.y+1.5 vs hanger.y+1), so the hanger keeps its +0.5
                // raised-attach baseline on top of the slab's lowering.
                return hasRaisedSlabUnderside(above) ? slabSupportDy + 0.5 : slabSupportDy;
            }
            double fullBlockSupportDy = loweredFullHeightSupportDy(world, supportPos, above);
            if (Double.isFinite(fullBlockSupportDy) && fullBlockSupportDy < -1.0e-6) {
                return fullBlockSupportDy;
            }
        }
        BlockPos cursor = supportPos;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = getBlockStateOrAir(world, cursor);
            // L4: a TS support is never a lowering top-like ceiling (else +0.5 smooshes the hanger
            // up into the flush TS block). Same invariant as the getYOffsetInner walks below.
            if (isLoweringTopLikeCeiling(cur)) {
                return 0.5;
            }
            if (isCeilingAttached(world, cursor, cur)) {
                cursor = cursor.up();
                continue;
            }
            break;
        }
        return 0.0;
    }

    private static double getYOffsetInner(BlockView world, BlockPos pos, BlockState state) {
        // Always-ceiling-hung decoration (hanging roots, spore blossom, hanging sign, pale hanging
        // moss) hangs from the block ABOVE and has no floor variant, so its dy is a pure function
        // of that support. Dispatch it here, BEFORE every "object resting on a support below"
        // branch (anchors, gap-fill, directCustomSlabSupportDy, shouldOffset/slab-column) — those
        // wrongly lower it when a carrier sits lower in the column (e.g. a placed lantern bridges
        // the downward walk to a slab below, dragging the hanger down through a flush support).
        // Lanterns are NOT here: a standing lantern legitimately rests on a support, so it keeps
        // the normal path (its hanging form follows lowered supports via the hanger-owner lane).
        if (isAlwaysCeilingHungDecoration(state)) {
            return ceilingHungDecorationDy(world, pos, state);
        }
        // THE STORED PLACEMENT HEIGHT IS THE ANSWER, whoever recorded it (LAW 1: a placed height is
        // computed once and then frozen; every later read returns the stored value verbatim).
        //
        // This read used to be reachable only through anchoredCellDy, i.e. only inside an "if this
        // cell is anchored" branch. That was behaviourally identical for as long as every stored
        // fact belonged to an anchored cell — but it made "does this cell have a recorded height"
        // a question only anchored cells were permitted to ask, and LAW.md lane B is precisely the
        // cell that renders lowered while earning no anchor. Giving it a number and then reading
        // that number only for anchored cells would store a fact nobody consults.
        //
        // THE CONCERN THAT KEPT THIS READ POSITIONAL IS NOW STATED DIRECTLY INSTEAD. The
        // placement-dy author's reason for leaving it inside the anchor branch was that a top-level
        // read could silently change CEILING-HUNG behaviour the first time an unanchored cell
        // acquired a fact — a hanger must keep following the support ABOVE it, and a stored number
        // would pin it. That is now enforced where the fact is minted:
        // SlabAnchorAttachment.freezeLoweredOnPlace refuses to record for a block that hangs from
        // the cell above (isCeilingAttached — a ROLE predicate as of 3a7c17c0 — or
        // isAlwaysCeilingHungDecoration), so a hanger can never own a fact for this line to honour.
        // The always-ceiling-hung family is additionally dispatched above this line, exactly as
        // before, so its resolution is untouched either way.
        //
        // Absence is still indistinguishable from the behaviour that shipped: with no fact this
        // falls through to the identical lanes below, which is the migration-safety contract every
        // world saved before the store existed relies on.
        if (!IGNORE_STORED_PLACEMENT_DY.get()) {
            double placedDy = SlabPlacementDyAttachment.storedDy(world, pos);
            if (!Double.isNaN(placedDy)) {
                return placedDy;
            }
        }
        // Slab-on-offset-block: a slab placed on top of a solid block that sits on a bottom slab
        // inherits the same -0.5 dy so the stack stays visually continuous (no gap).
        if (state.getBlock() instanceof SlabBlock) {
            if (SlabAnchorAttachment.isAnchored(world, pos)) {
                // The anchor is a boolean membership set, not a stored height. anchoredCellDy
                // returns the height this slab was PLACED at when the store has it, and otherwise
                // resolves from the support's ACTUAL rendered top face exactly as before
                // (live-reported 0.5 floating gap for a slab placed on a support already at -1.0).
                return anchoredCellDy(world, pos, 0);
            }
            // FREEZE-ON-PLACE: a slab locked FLAT at placement stays at 0 — a lowered carrier
            // placed beside/under it later can no longer make it inherit a lowered position
            // (LAW 1, LAW.md: a placed block must not autonomously move / type-inherit on
            // neighbor change). Read before every geometric inheritance walk below.
            if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
                return 0.0;
            }
            BlockPos belowPos = pos.down();
            BlockState below = getBlockStateOrAir(world, belowPos);
            if (hasLoweredNonSlabTopSupport(world, belowPos, below)
                    || hasLoweredSlabSupport(world, belowPos, below)) {
                // GEOMETRIC MIRROR of the anchored slab branch above — same flat-constant defect,
                // folded into the same resolver so the two lanes can never disagree again (the
                // rig's follower_on_minus_one read birch_slab=-0.5 here with no anchor at all).
                return loweredFollowerDy(world, pos, 0);
            }
            // Adjacent-side-slab alignment: a bottom or double slab placed at the side of a
            // lowered full block must visually inherit the lowered -0.5 dy so model/outline/
            // raycast align with the neighbor. Use hasBottomSlabBelow directly: calling
            // getYOffset here would be short-circuited to 0.0 by the IN_GET_Y_OFFSET recursion
            // guard since this code runs inside getYOffsetInner.
            if (isAdjacentSideSlabLowered(world, pos, state)) {
                return -0.5;
            }
        }

        // Persistent slab-anchor: an ordinary FB placed directly on a bottom slab is
        // recorded on the chunk via SlabAnchorAttachment at placement time and cleared
        // when the FB itself is broken/replaced. Anchors persist across supporting BS
        // removal so the FB does not visually jump upward.
        // Only honour anchors for non-slab blocks; slabs were handled above.
        if (!(state.getBlock() instanceof SlabBlock)
                && com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, pos)) {
            // The anchor records THAT this block was placed on a lowered surface, never HOW FAR
            // down it went, so the dy must come from the support's ACTUAL rendered top face:
            //  - vanilla BOTTOM slab support -> the mixed-slab compound (-0.5 anchor + the slab's
            //    own drop), or a block anchored on a lowered mixed slab pops UP half a block the
            //    instant its placement anchor syncs (the crafting-table-on-mixed-slab pop);
            //  - solid NON-SLAB support -> inherit that support's dy outright. This half was
            //    missing: the old correction went only through loweredBottomSlabSupportDy, which
            //    reports NaN for anything that is not a bottom slab, so a follower on a -1.0
            //    stripped_jungle_log stayed at -0.5 with a 0.5 floating gap (live 2026-08-05).
            // loweredFollowerDy is recursion-safe and floors at -0.5, so every other anchor case
            // (block on a plain slab, persisted anchor after its slab was broken, column /
            // adjacent / below-anchored) is unchanged.
            double anchorDy = anchoredCellDy(world, pos, 0);
            if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                String side = (world instanceof net.minecraft.world.World w && w.isClient()) ? "CLIENT" : "SERVER";
                Slabbed.LOGGER.info("[ANCHOR] dy applied side={} pos={} state={} dy={}",
                        side, pos.toShortString(), state, anchorDy);
            }
            return anchorDy;
        }

        // FREEZE-ON-PLACE: a structural full block locked FLAT at placement stays at 0 — a slab
        // or lowered carrier added under/beside it later can no longer pull it down (LAW 1,
        // LAW.md: a placed block must not autonomously move). Read before every geometric lowering lane
        // below (gap-fill, cantilever-adjacency, Terrain-Slabs combining, column walk).
        // Decorative followers are never frozen-flat, so they keep tracking their supports.
        if (!(state.getBlock() instanceof SlabBlock)
                && com.slabbed.anchor.SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return 0.0;
        }

        // Gap-fill (live + recursion-safe): an ordinary solid block sitting directly below
        // an anchored lowered block belongs to that lowered column, so it must lower to
        // match — even when its own support below is air (a broken-out gap that the
        // column/direct checks can't see). Computing this live (isAnchored never calls
        // getYOffset) means a refilled block lowers on the very first client frame instead
        // of un-lowering and z-fighting the anchored block above until its own anchor syncs.
        if (!(state.getBlock() instanceof SlabBlock)
                && getBlockStateOrAir(world, pos.down()).isAir()) {
            boolean isBlockEntitySubject = state.getBlock() instanceof BlockEntityProvider;
            boolean solidSubject = !isBlockEntitySubject && state.isSolidBlock(world, pos);
            if (solidSubject) {
                BlockPos abovePos = pos.up();
                BlockState above = getBlockStateOrAir(world, abovePos);
                if (!(above.getBlock() instanceof SlabBlock)
                        && com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, abovePos)) {
                    return -0.5;
                }
            }
            // Cantilevered perpendicular side placement: a block placed beside a lowered
            // full block (or, for a connecting block — fence/wall/pane/gate — beside a
            // lowered connecting block of its own family, the live "pane placed beside a
            // lowered pane froze flat/detached" bug; or a BLOCK ENTITY — hopper/chest/… —
            // beside a lowered block entity/chest, the live "next horizontally chained
            // hopper places upward" bug) with air below it lowers only via its (server-side,
            // one-tick-late) adjacent anchor. Detecting the lowered neighbour live makes the
            // first client mesh already -0.5, so it never renders full height first (the
            // snap), a connecting block no longer freezes flat before its anchor would have
            // synced, AND — since qualifiesForBlockEntityLoweredAnchor's sole criterion is
            // this same getYOffset going negative — a chained block entity now also gets its
            // PERSISTED anchor recorded automatically, not just the live render. The "gap-fill
            // from above" lane just above stays solid-only (BE/connecting unverified there).
            if ((solidSubject || SlabAnchorAttachment.isConnectingStructural(state) || isBlockEntitySubject)
                    && isAdjacentToLoweredSupport(world, pos)) {
                return -0.5;
            }
        }

        double directCustomSurfaceDy = directCustomSlabSupportDy(world, pos, state);
        if (!Double.isNaN(directCustomSurfaceDy)) {
            // Combined-slab compound. directCustomSlabSupportDy returns a flat -0.5: the drop to
            // sit on a half-height bottom-type surface (a Terrain Slabs BOTTOM_LIKE slab). Two
            // corrections keep stacked/combined slabs FLUSH instead of floating half a block:
            //  (1) If the immediate support is itself a lowered vanilla BOTTOM slab (a "mixed
            //      slab" — a vanilla bottom slab capping the terrain — or a stack of them),
            //      follow its drop too. Applies to objects, full blocks, AND vanilla slabs
            //      stacked on a mixed slab (vanilla-only: terrain slabs are skip-offset and
            //      never reach here). loweredBottomSlabSupportDy is recursion-safe.
            //  (2) A vanilla TOP slab caps from the UPPER half of its own block, so it needs an
            //      extra -0.5 to sit flush on a bottom-type surface (else a half-block gap shows
            //      underneath — the vanilla-TOP-slab-on-terrain bug). BOTTOM/DOUBLE slabs and
            //      non-slab objects are unaffected.
            // The result is capped at minResolvedDy(), so deeper niche combos (e.g. a TOP slab on
            // a mixed slab) settle at the cap rather than being drawn where the offset-aware pick
            // window cannot reach them. (The old wording named the window as {C, C.up, C.down};
            // that stopped being true when the window widened to +/-WINDOW_RADIUS on 2026-08-07.
            // Naming the cap instead of the window keeps this comment from going stale again.)
            double dy = directCustomSurfaceDy;
            double supportLoweredDy = loweredBottomSlabSupportDy(world, pos.down());
            if (Double.isFinite(supportLoweredDy) && supportLoweredDy < -1.0e-6) {
                dy += supportLoweredDy;
            }
            if (state.getBlock() instanceof SlabBlock
                    && state.contains(SlabBlock.TYPE)
                    && state.get(SlabBlock.TYPE) == SlabType.TOP) {
                dy += -0.5;
            }
            // THE CAP IS THE SHARED ONE (2026-08-07, Stage 2). This site used to write the
            // magnitude out by hand as `if (dy < -1.0) dy = -1.0;`, and read as correct only
            // because the constant it duplicated happened to hold the same number. It is the SAME
            // refusal-to-go-deeper as the support resolver's, applied on the Terrain Slabs lane
            // instead of the vanilla one, so it must move when the cap moves or the two lanes
            // would answer differently about the same tower. Byte-identical at -1.0: Math.max
            // agrees with the old branch on every input, NaN and -0.0 included.
            // ClampUnificationTest resolves ONE tower down both lanes and asserts one answer.
            dy = Math.max(dy, minResolvedDy());
            return dy;
        }

        if (shouldOffset(world, pos, state)) {
            // Compound case: non-slab block above a bottom slab that is itself an adjacent-side
            // slab lowered by -0.5.  The block must drop an additional -0.5 to align with the
            // slab's visual top surface, for a total of -1.0.
            BlockState belowSlab = getBlockStateOrAir(world, pos.down());
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, pos.down(), belowSlab)) {
                return -1.0;
            }
            double columnDy = slabColumnYOffset(world, pos);
            if (columnDy != 0.0) {
                return columnDy;
            }
            return -0.5;
        }

        // ── Decorative hangers under a LOWERED support follow it down ──────────
        // A lantern / soul lantern / spore blossom / hanging roots / pale hanging
        // moss hanging beneath a support that itself renders lowered must inherit
        // the support's negative dy, or the lowered support's underside clips down
        // into the hanger's top (the lantern-jammed-into-log artifact). Chains are
        // EXCLUDED so they keep extending to reach the support. Runs BEFORE the
        // +0.5 ceiling branch; the helpers return 0.0/NaN for a normal
        // (non-lowered) support so the already-correct flush and +0.5 cases stay
        // untouched. The helpers are recursion-safe mirrors of the dy logic above
        // and never call getYOffset (safe inside the IN_GET_Y_OFFSET guard).
        if (isLoweredUndersideHangerOwner(state)) {
            BlockPos supportPos = pos.up();
            BlockState supportState = getBlockStateOrAir(world, supportPos);
            double slabSupportDy = loweredSlabUndersideSupportDy(world, supportPos, supportState);
            if (Double.isFinite(slabSupportDy) && slabSupportDy < -1.0e-6) {
                // A TOP slab's underside sits half a block higher than a hanger's
                // natural attach (support.y+1.5 vs hanger.y+1), so the hanger keeps
                // its +0.5 raised-attach baseline on top of the slab's lowering.
                return hasRaisedSlabUnderside(supportState) ? slabSupportDy + 0.5 : slabSupportDy;
            }
            double fullBlockSupportDy = loweredFullHeightSupportDy(world, supportPos, supportState);
            if (Double.isFinite(fullBlockSupportDy) && fullBlockSupportDy < -1.0e-6) {
                return fullBlockSupportDy;
            }
        }

        // GH #22 (slab-log DODO): an ordinary opaque full cube (e.g. a worldgen leaf-less log's
        // grass/dirt cap, or any command-authored block) sitting directly on a lowered log-family
        // carrier must share that carrier's dy, or it stays flush while the log beneath it dropped
        // -0.5 — a visible half-block gap between the log and the block on top ("tries to connect
        // with the log half a block above it"). Checked BEFORE the generic opaque-full-cube-stays-
        // flush guard below so it isn't shadowed by it; narrow to log-family supports ONLY (not
        // arbitrary opaque terrain) so ordinary stone-on-stone/terrain still stays flush and the
        // world-hole guard is untouched.
        double loweredCarrierDy = loweredCuratedCarrierDy(world, pos, state);
        if (Double.isFinite(loweredCarrierDy) && loweredCarrierDy < -1.0e-6) {
            return loweredCarrierDy;
        }

        // ── ceiling-attached blocks under a top slab: +0.5 UP ────────
        // Only explicit ceiling-mounted cases may float into the slab space.
        // Note: isSolidBlock is safe here because getYOffset has a recursion guard.
        if (isClassFlushPinnedSubject(world, pos, state)) {
            return 0.0;
        }

        // A non-solid object (lantern, etc.) standing ON TOP of a full-block support that is
        // itself rendered lowered must follow that support down, or it floats above the
        // support's lowered top face (the reported still-floating-lantern bug: a standing
        // lantern on a lowered grass/dirt/planks block). The object's own shouldOffset column
        // walk above can MISS this lowering — e.g. when the support is lowered by a persisted
        // anchor or by adjacency but its own column below is air (a cantilever stops the walk)
        // or a full-height solid block (no slab in the lantern's column at all). Resolve the
        // support's actual rendered dy via the recursion-safe loweredFullHeightSupportDy
        // (anchor / direct-custom-TS / lowered column / compound -1.0; adjacency-lowered
        // cantilevers report through their persisted anchor), which never re-enters getYOffset,
        // and inherit it. Reached only by non-solid objects (solid blocks returned 0.0 just
        // above), so the common render path pays nothing; returns NaN/0.0 for a non-lowered
        // support so flush cases stay untouched.
        BlockPos sitSupportPos = pos.down();
        BlockState sitSupport = getBlockStateOrAir(world, sitSupportPos);
        double sitSupportDy = loweredFullHeightSupportDy(world, sitSupportPos, sitSupport);
        if (Double.isFinite(sitSupportDy) && sitSupportDy < -1.0e-6) {
            return sitSupportDy;
        }
        // Cantilever fallback: a support lowered LIVE purely by adjacency to a lowered full
        // block (no persisted anchor yet, e.g. the first client frame after a side-placement)
        // is not yet reported by loweredFullHeightSupportDy, so detect it directly.
        if (!(sitSupport.getBlock() instanceof SlabBlock)
                && !(sitSupport.getBlock() instanceof BlockEntityProvider)
                && sitSupport.isSolidBlock(world, sitSupportPos)
                && isAdjacentToLoweredSupport(world, sitSupportPos)) {
            return -0.5;
        }

        // A non-solid object (lantern/chain/…) standing on a TOP or DOUBLE slab that is
        // itself rendered lowered must follow that slab down, or it floats above the slab's
        // lowered top face (the reported TS+VS floating-lantern bug: a vanilla TOP/DOUBLE slab
        // sitting on a lowered Terrain-Slabs/full-block column). BOTTOM-slab supports are
        // already handled by the shouldOffset path above (which yields -0.5 or the compound
        // -1.0), and the object's own top face sits at the slab's top, so the object inherits
        // the slab's rendered dy. loweredSlabUndersideSupportDy is recursion-safe (never calls
        // getYOffset) and returns 0.0 for a non-lowered slab so flush cases stay untouched.
        if (sitSupport.getBlock() instanceof SlabBlock
                && sitSupport.contains(SlabBlock.TYPE)
                && sitSupport.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
            double slabSitDy = loweredSlabUndersideSupportDy(world, sitSupportPos, sitSupport);
            if (Double.isFinite(slabSitDy) && slabSitDy < -1.0e-6) {
                return slabSitDy;
            }
        }

        BlockState above = getBlockStateOrAir(world, pos.up());

        // direct: ceiling-attached blocks (lantern, chain, dripstone, cave vine, top trapdoor,
        // bell/lever/button) directly under a top slab. A TS surface is EXCLUDED via
        // isLoweringTopLikeCeiling — otherwise it smooshes the hanger +0.5 up into the flush TS
        // block (L4). This is the sibling of the ceilingHungDecorationDy walk fixed for the
        // always-hung family; both must share the guard or lanterns/chains/dripstone regress.
        if (isCeilingAttached(world, pos, state) && isLoweringTopLikeCeiling(above)) {
            return 0.5;
        }

        // cascading: ceiling-attached block below other ceiling-attached blocks
        // leading up to a top slab (e.g. 2nd dripstone, 2nd vine segment)
        if (isCeilingAttached(world, pos, state)) {
            BlockPos cursor = pos.up();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = getBlockStateOrAir(world, cursor);
                if (isLoweringTopLikeCeiling(cur)) {
                    return 0.5;
                }
                if (isCeilingAttached(world, cursor, cur)) {
                    cursor = cursor.up();
                    continue;
                }
                break;
            }
        }

        return 0.0;
    }

    /**
     * CLASS-BASED FLUSH GUARD, extracted to ONE place: true iff {@link #getYOffsetInner} pins this
     * subject at {@code 0.0} before ever reaching the "object follows its lowered support down"
     * lanes below it. Extracted so {@link #loweredStandingObjectDy} can ask the SAME question the
     * dy path answers, instead of keeping a second copy that would rot out of sync with it (the
     * shared-predicate half-fix trap). It no longer carries ANY thin-layer / snow term: the
     * {@code isThinTopLayer} entry went in BUG A (2026-08-06) and its narrowed successor
     * {@code isEnvironmentDepositedSurfaceFill} went with the snow ruling the same day. What is
     * left is pure geometry.
     *
     * <p>Opaque full cubes always stay flush here. {@code state.isSolidBlock(world,pos)} is
     * VIEW-DEPENDENT (it does region-clamped {@code getOutlineShape} reads) and returns FALSE for a
     * solid cube under {@code ChunkRendererRegion} on the render thread, letting natural terrain
     * fall through to the object lanes → {@code -0.5} model shift while face-culling stays at the
     * grid voxel → see-through world holes. {@code isOpaqueFullCube()} is a precomputed,
     * view-independent flag and is tested FIRST of the two, so it pins terrain flush on every
     * thread; only non-opaque states can reach the view-dependent term.
     *
     * <p>NOTE: fence GATE is intentionally NOT folded into this flush guard yet. The fix is
     * designed (use {@code SlabAnchorAttachment.isConnectingStructural} here, as
     * {@code isSteppedConnectingNeighbor} now does) but could NOT be RED-proven headlessly — the
     * anchor-lowered-support-with-no-slab-in-column fixture did not reproduce — and this project
     * does not ship a fix without a reproducing RED. Tracked internally.
     */
    private static boolean isClassFlushPinnedSubject(BlockView world, BlockPos pos, BlockState state) {
        Block blk = state.getBlock();
        return blk instanceof SlabBlock
                || blk instanceof StairsBlock
                || blk instanceof FenceBlock
                || blk instanceof WallBlock
                || blk instanceof PaneBlock
                || state.isAir()
                || !state.getFluidState().isEmpty()
                || state.isOpaqueFullCube()
                || state.isSolidBlock(world, pos);
    }

    /**
     * Reentrancy guard for {@link #loweredStandingObjectDy}. The probe resolves the support BELOW
     * the object it is asked about, which can re-enter {@code shouldOffset → hasSlabInColumn} one
     * cell lower; the flag caps that at exactly ONE extra level. The real geometry never needs
     * more (the object's support answers from a bottom slab / anchor / TS surface directly), and a
     * hard O(1) cap keeps a descending column of alternating objects and solids off the
     * chunk-render hot path — the lag class this project has already shipped twice.
     */
    private static final ThreadLocal<Boolean> IN_STANDING_OBJECT_PROBE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * RESOLVED dy of a STANDING OBJECT cell (flower pot, lantern, candle, torch, …) — a cell whose
     * own rendered height comes from {@link #getYOffsetInner}'s "non-solid object standing on a
     * lowered full-block support" lane. Returns {@link Double#NaN} when the cell is not such an
     * object or is not lowered.
     *
     * <p><b>The potted-flower oscillation</b> (live-confirmed 2026-08-06). A stone block
     * oscillated {@code -0.5 ↔ 0.0} in lockstep with the block below it being
     * {@code minecraft:flower_pot} vs {@code minecraft:potted_cornflower} — <b>the pot's own dy was
     * {@code -0.500} in both frames</b>, so the support never moved; only its block IDENTITY
     * changed. Cause: the bounded column walks scored that cell with
     * {@code SlabAnchorAttachment.isAnchored(...)}, <b>an anchor boolean standing in for "is this
     * cell lowered"</b> — a recurring bug class on this line. Potting a flower is an in-place
     * block-KIND change, so {@code onStateReplaced} fires and {@code replacementPreservesAnchor}
     * clears the pot's anchor (tracked internally, 1j) while its rendered height, which
     * comes from a wholly different lane, is untouched. The flag vanished, the geometry did not,
     * and the placed block above it JUMPED — a LAW 1 (never-pop) violation; see {@code LAW.md}.
     *
     * <p>Asking for the HEIGHT instead makes the answer independent of which flower is in the pot,
     * of whether it holds a flower at all, and of whether anything was ever anchored. 1j is
     * deliberately left open: this makes the recorded sequence impossible regardless of it.
     *
     * <p>Recursion-safe: guarded by {@link #IN_STANDING_OBJECT_PROBE} and strictly descending.
     */
    private static double loweredStandingObjectDy(BlockView world, BlockPos pos, BlockState state) {
        if (IN_STANDING_OBJECT_PROBE.get()) {
            return Double.NaN;
        }
        // Only cells whose OWN dy is decided by the object lanes qualify. A class-flush-pinned cell
        // (glass, a fence, a stair, terrain) renders 0.0 itself, so treating it as a lowering
        // source would make what stacks on it disagree with it.
        if (isClassFlushPinnedSubject(world, pos, state)) {
            return Double.NaN;
        }
        BlockPos belowPos = pos.down();
        BlockState below = getBlockStateOrNull(world, belowPos);
        if (below == null) {
            return Double.NaN;
        }
        IN_STANDING_OBJECT_PROBE.set(Boolean.TRUE);
        try {
            double supportDy = loweredFullHeightSupportDy(world, belowPos, below);
            return Double.isFinite(supportDy) && supportDy < -1.0e-6 ? supportDy : Double.NaN;
        } finally {
            IN_STANDING_OBJECT_PROBE.set(Boolean.FALSE);
        }
    }

    /**
     * Decorative ceiling hangers that must FOLLOW a lowered support down so they
     * stay flush instead of clipping up into it: lanterns, soul lanterns, spore
     * blossoms, hanging roots, and pale hanging moss. Chains are deliberately
     * EXCLUDED — they extend to reach their support rather than tracking its dy.
     */
    private static boolean isLoweredUndersideHangerOwner(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return state.isOf(Blocks.LANTERN)
                || state.isOf(Blocks.SOUL_LANTERN)
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || isPaleHangingMossBlock(state);
    }

    private static boolean isPaleHangingMossBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        var id = Registries.BLOCK.getId(state.getBlock());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return "pale_hanging_moss".equals(path) || "pale_hanging_moss_tip".equals(path);
    }

    /**
     * Recursion-safe rendered dy of a SLAB support directly above a hanger,
     * mirroring the slab branch of {@link #getYOffsetInner} without re-entering
     * {@link #getYOffset}. Returns {@code 0.0} for a non-lowered slab (the caller
     * gates on {@code < -1e-6}) or {@link Double#NaN} if not a slab.
     */
    private static double loweredSlabUndersideSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        // Unstored custom slabs keep their compatibility owner's native coordinate space. Once a
        // custom slab has an authored placement fact, that immutable height is the shared support
        // authority for every underside consumer. This check precedes the visual cache so native
        // and authored custom slabs cannot be conflated by a derived value.
        if (CompatHooks.shouldSkipOffset(state)) {
            double stored = SlabPlacementDyAttachment.storedDy(world, pos);
            return Double.isFinite(stored) ? stored : 0.0;
        }
        Double cachedDy = cachedClientVisualYOffset(pos);
        if (cachedDy != null) {
            return cachedDy;
        }
        // FOURTH mirror of the flat-constant defect — the exact shape of the getYOffsetInner slab
        // branch (anchored / lowered-support-below), so it folds into the same resolver. Only the
        // adjacency case below stays a flat -0.5: that is a SIDE relationship, and the resolver
        // reads the support BELOW.
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return anchoredCellDy(world, pos, 0);
        }
        BlockPos belowPos = pos.down();
        BlockState below = getBlockStateOrAir(world, belowPos);
        if (hasLoweredNonSlabTopSupport(world, belowPos, below)
                || hasLoweredSlabSupport(world, belowPos, below)) {
            return loweredFollowerDy(world, pos, 0);
        }
        if (isAdjacentSideSlabLowered(world, pos, state)) {
            return -0.5;
        }
        return 0.0;
    }

    private static boolean hasRaisedSlabUnderside(BlockState state) {
        CompatSlabSurfaceKind customKind = CompatHooks.customSlabSurfaceKind(state);
        if (customKind != CompatSlabSurfaceKind.NONE) {
            return customKind == CompatSlabSurfaceKind.TOP_LIKE;
        }
        return isTopSlab(state);
    }

    /**
     * The DEVELOPER OVERRIDE that arms the deeper offset alphabet unconditionally.
     * {@code slabbed.deepDyAlphabet}.
     *
     * <p>Named on the same pattern as this line's other switches ({@code slabbed.lawGate},
     * {@code slabbed.offsetRaycast}, {@code slabbed.serverHitTolerance}), read ONCE at class init
     * into a static final so nothing on the resolver path pays a {@code System.getProperty} per
     * call. A Gradle daemon {@code -D} does NOT reach a forked JavaExec, so {@code build.gradle}
     * forwards this name into {@code runGameTest} explicitly, exactly as it already does for
     * {@code slabbed.lawGate}.
     *
     * <p><b>Since the consent stage this is an OVERRIDE, not the setting.</b> The setting itself is
     * per-world consent state ({@link com.slabbed.anchor.DeepDyConsentAttachment}); this property
     * forces the deep leg ON regardless of what any world says, and it is what lets the suite run
     * both legs from one tree. It is never consulted to turn the alphabet OFF — a world that has
     * consented stays consented.
     */
    public static final String DEEP_DY_ALPHABET_PROPERTY = "slabbed.deepDyAlphabet";

    /**
     * THE DEVELOPER OVERRIDE'S VALUE — is the deeper offset alphabet force-armed for this whole
     * JVM? <b>Default {@code false}.</b>
     *
     * <p><b>Why the alphabet is gated at all, given LAW 1.</b> A cell WITH a stored placement
     * height never moves when this flips — {@code storedDy} is returned verbatim by
     * {@link #anchoredCellDy} and no cap is consulted on that path. But every cell WITHOUT one
     * takes the LIVE resolver: worlds saved before the placement store landed ({@code d4f38510},
     * this dev cycle), worldgen, {@code /setblock}, {@code /slabrig}, and {@code LAW.md} lanes A
     * and C-F. Those cells would visibly drop half a block the first time they re-rendered after an
     * update. The maintainer ruling of 2026-08-06 explicitly DECLINED to call that "acceptable as a
     * repair": no existing build shifts under anyone without warning. The maintainer ruling of
     * 2026-08-09 settled how it does reach players — per-world CONSENT, not a silent migration.
     *
     * <p><b>What OFF guarantees.</b> {@link #minResolvedDy()} reads {@link #SHIPPED_MIN_RESOLVED_DY}
     * and every other constant on this line was already derived from the window contract rather
     * than from the cap (Stages 1-3), so no other value moves. That the OFF leg is unchanged is not
     * asserted, it is MEASURED: {@code ClampUnificationTest}'s 196-column, 588-reading battery must
     * reproduce the fingerprint pinned before Stage 2, token for token.
     */
    public static final boolean DEEP_DY_ALPHABET =
            Boolean.parseBoolean(System.getProperty(DEEP_DY_ALPHABET_PROPERTY, "false"));

    /**
     * The cap every build has shipped to date, and the value {@link #minResolvedDy()} keeps while
     * no world has consented and {@link #DEEP_DY_ALPHABET} is off.
     *
     * <p>Kept as a NAMED constant rather than inlined into the conditional so the OFF leg has
     * something to be identical TO: the tests that pin unchanged behaviour compare against this
     * name, not against a literal they would have to keep in step by hand.
     */
    public static final double SHIPPED_MIN_RESOLVED_DY = -1.0;

    /**
     * THE CAP — the deepest dy this line will ever RESOLVE, and the one constant every clamp site
     * in this file reads.
     *
     * <p><b>Derived, not asserted.</b> This is not {@code -1.0} "because the offset alphabet is
     * {@code {-1.0, -0.5, 0.0}}" — that alphabet is a CONSEQUENCE of the cap, not its reason. What
     * fixes the cap is the pick window. A block drawn at {@code dy} is clickable only if the
     * offset-aware raycast tests the cell layers its shape actually occupies, and
     * {@link SlabbedOffsetRaycast} sizes that window from its own contract constant
     * {@link SlabbedOffsetRaycast#DEEPEST_TARGETABLE_DY}, deriving
     * {@link SlabbedOffsetRaycast#WINDOW_RADIUS} as {@code ceil(-DEEPEST_TARGETABLE_DY)}. So the
     * standing identity across the two files is
     *
     * <blockquote>{@code minResolvedDy() >= DEEPEST_TARGETABLE_DY}</blockquote>
     *
     * the resolver may never PRODUCE a height deeper than the window undertakes to attribute, or
     * the block is drawn somewhere the player cannot aim. Neither constant can be moved on its own
     * without reading the other: {@code ClampUnificationTest} asserts the identity, and
     * {@code DeepDyWindowCharacterisationTest} asserts the radius derivation.
     *
     * <p><b>The identity closes to EQUALITY exactly when {@link #DEEP_DY_ALPHABET} is on.</b> With
     * the flag OFF — the shipped default — this cap is {@code -1.0} against a window built for
     * {@code -2.0}, so the pick path leads the alphabet and its permanent cost ships and live-tests
     * on its own: the identity is an INEQUALITY and the slack is deliberate. With the flag ON this
     * constant IS {@link SlabbedOffsetRaycast#DEEPEST_TARGETABLE_DY} — not a second copy of
     * {@code -2.0} but the same field read — so the cap and the window cannot be given different
     * values, and {@code minResolvedDy() == -(window radius)} is a DERIVABLE invariant rather than a
     * magic number. That is the maintainer ruling of 2026-08-06 and the whole reason the ruled cap
     * is {@code -2.0} instead of the {@code -1.5} originally asked for: Stage 0 MEASURED the required
     * radius as 1 at {@code -1.0} and 2 at BOTH {@code -1.5} and {@code -2.0}, so stopping at
     * {@code -1.5} would pay the entire radius-2 cost and leave the constant magic anyway.
     *
     * <p>Stages 1-3 exist so that this is a ONE-CONSTANT change: the pick window derives its radius
     * from the window contract, the depth budget derives from the same contract, and every site
     * that refuses to go deeper reads this name. Nothing else moves when the flag flips.
     *
     * <p><b>WHY THIS IS A METHOD OVER A CACHED FIELD AND NOT A {@code static final}.</b> The
     * alphabet is no longer a JVM-wide switch: it is per-world consent state, held server-side by
     * {@link com.slabbed.anchor.DeepDyConsentAttachment}. A {@code static final} derived from a
     * system property cannot express "this world said yes and that one did not". But this value is
     * read on the resolver's hot path — the path Stage 1 measured growing 3.06x on the pick alone —
     * so it may not become a map lookup, an attachment read, or anything else that touches a world
     * per call. What it is instead: a single {@code volatile double}, written once when a world
     * loads (and once on the client when the server's value arrives), read here with one load and
     * no branch, no boxing and no allocation. {@code DeepDyConsentTest} pins that the hot path
     * performs ZERO authoritative store reads across a whole pick battery — that is the assertion
     * that fails the day someone "simplifies" this back into a per-call lookup.
     *
     * <p><b>What "every site" means, and why it is now enforced.</b> Two independent lanes can
     * saturate the same tower: the support resolver ({@link #loweredFollowerDy} and the column
     * walk), and the direct-custom surface lane in {@link #getYOffsetInner} — the lane Terrain
     * Slabs geometry takes. Until 2026-08-07 the second wrote its magnitude out by hand instead of
     * naming this constant, so moving the cap would have left a TS tower and a vanilla tower at
     * different heights in the same world for the same reason. {@code ClampUnificationTest} builds
     * ONE tower, resolves it down both lanes, and asserts the two answers are the same number.
     *
     * <p><b>Not every {@code -1.0} in this file belongs here.</b> Several mean "half a block of
     * support drop plus half a block of seat" — a magnitude that is one full block because that is
     * the shape, not because anything refused to go deeper. Those must NOT move with the cap; see
     * {@link #getYOffsetInner}'s compound branch, its mirror in {@link #cellTopSupportDy}, and the
     * column walk's historical seat constant. Only a site whose job is to REFUSE TO GO DEEPER
     * reads this name.
     */
    private static volatile double minResolvedDy =
            DEEP_DY_ALPHABET ? SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY : SHIPPED_MIN_RESOLVED_DY;

    /**
     * Reads THE CAP. See the field above for what it is and why it is cached rather than resolved.
     *
     * <p>One volatile {@code double} load. No branch, no world, no allocation.
     */
    public static double minResolvedDy() {
        return minResolvedDy;
    }

    /**
     * Sets the cap for the world this JVM currently has open. <b>Called only by
     * {@link com.slabbed.anchor.DeepDyConsentAttachment}</b>, on world load (server side) and when
     * the server's value arrives (client side).
     *
     * <p><b>The developer override wins and may only ever arm, never disarm.</b> With
     * {@code -Dslabbed.deepDyAlphabet=true} the suite's deep leg must exercise the deep cap in
     * every world including an unconsented one, so this refuses to lower the cap in that mode.
     * Without the override the value is exactly what the world said.
     *
     * <p>The derivation is written HERE, once, and never at a call site: armed, the cap IS
     * {@link SlabbedOffsetRaycast#DEEPEST_TARGETABLE_DY} — the same field, not a second copy of the
     * number — so {@code cap == -(window radius)} cannot be written wrong.
     */
    public static void armDeepAlphabet(boolean armed) {
        minResolvedDy = capFor(armed);
    }

    /**
     * The derivation on its own, as a PURE function of the consent bit — so a test can exercise
     * both answers without writing the live cap that every other test in the suite is reading.
     * That is not a convenience: mutating the cached cap mid-suite would make neighbouring cells
     * order-dependent, and an order-dependent law suite is a false green waiting to happen.
     */
    public static double capFor(boolean armed) {
        return (armed || DEEP_DY_ALPHABET)
                ? SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY
                : SHIPPED_MIN_RESOLVED_DY;
    }

    /**
     * THE DEEPEST ONE COURSE CAN ADD — the worst-case seat drop a single level of the walk can
     * contribute, and the divisor the depth budget below is sized by.
     *
     * <p>{@link #supportSeatDy} has exactly three arms. The HALF-HEIGHT arm (a bottom-slab
     * support, whose top face renders half a block below its own grid line) hands its follower
     * {@code supportDy - 0.5}; the FULL-HEIGHT arm passes the support's dy through unchanged; the
     * FLUSH arm answers {@code 0.0}. So {@code 0.5} is the largest step any one course can take,
     * and a budget of {@code ceil(-cap / 0.5)} courses is exactly enough to reach {@code cap}
     * from {@code 0.0}.
     *
     * <p><b>This is a SHAPE, not a refusal — do NOT move it with {@link #minResolvedDy()}</b>,
     * for the reason the column walk's own {@code -1.0} records at length: "half a block of slab
     * top face" is a fact about geometry and stays {@code 0.5} at any cap. It is written in one
     * other place, the half-height arm itself, and the two are pinned together by measurement
     * rather than by hope: {@code SupportDepthBudgetTest} builds a real ladder, measures the drop
     * one course actually takes, and asserts it equals this number.
     */
    private static final double DEEPEST_SEAT_DROP_PER_COURSE = 0.5;

    /**
     * THE DEPTH BUDGET — how many courses of support-of-a-support {@link #loweredFollowerDy} will
     * follow before it gives up. Bound on the walk {@link #loweredFollowerDy} →
     * {@link #supportSeatDy} → the recursion-safe support mirrors → possibly another anchored
     * support; one unit is spent per course, so a budget of {@code N} walks {@code N} courses
     * down before {@link #loweredFollowerDy} refuses.
     *
     * <p><b>DERIVED FROM THE CAP, not chosen (Stage 3, 2026-08-07).</b> It used to be a bare
     * {@code 4} justified by a sentence — "the clamp at {@code minResolvedDy()} means two levels
     * already saturate" — which is a sizing ASSUMPTION about a constant living in another
     * paragraph, and the single most reliable way for the two to drift apart. The budget is now
     * computed the way {@link SlabbedOffsetRaycast#WINDOW_RADIUS} is computed from
     * {@link SlabbedOffsetRaycast#DEEPEST_TARGETABLE_DY} (Stage 1) and the way every clamp site
     * now reads {@link #minResolvedDy()} (Stage 2), so all three move together:
     *
     * <blockquote>{@code ceil(-cap / DEEPEST_SEAT_DROP_PER_COURSE)} courses to SATURATE, {@code + 2}
     * headroom</blockquote>
     *
     * <p><b>Which cap.</b> {@link SlabbedOffsetRaycast#DEEPEST_TARGETABLE_DY}, the deepest height
     * this build undertakes to make clickable — NOT {@link #minResolvedDy()}, the deepest height
     * it will currently resolve. The two are an inequality today by design
     * ({@code minResolvedDy() >= DEEPEST_TARGETABLE_DY}); sizing the budget from the deeper of the
     * pair means the walk already carries the ruled {@code -2.0} cap before the cap arrives, which
     * is the same "lead the alphabet, ship the cost alone" shape Stage 1 used for the pick window.
     * When Stage 4 closes the inequality this constant does not move — it is already right.
     *
     * <p><b>What the {@code + 2} buys.</b> Saturation needs {@code ceil(-cap / 0.5)} courses:
     * 2 at {@code -1.0}, 4 at {@code -2.0}. One further course is needed to CONFIRM the course
     * below is itself saturated rather than merely to reach the value, and one more is slack for
     * a lane that spends a unit without descending a course. At today's {@code -2.0} window that
     * is {@code 4 + 2 = 6}; evaluated at the OLD {@code -1.0} it reproduces the historical
     * {@code 2 + 2 = 4} exactly, which is why this is a re-derivation of the existing number and
     * not a new one.
     *
     * <p>Still the hard stop that keeps a pathological column from walking forever — the budget is
     * larger, never absent.
     */
    public static final int MAX_SUPPORT_RESOLVE_DEPTH =
            (int) Math.ceil(-SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY / DEEPEST_SEAT_DROP_PER_COURSE) + 2;

    /**
     * THE SUPPORT-DY RESOLVER — the single source of truth for "how far down does a block sit
     * because of the support directly BELOW it".
     *
     * <p>Live-reported 2026-08-05 (symptom 1 of that pass): a block placed
     * on a support already lowered to {@code -1.0} rendered at {@code -0.5}, leaving a 0.5
     * floating gap, across four families (birch_slab, birch_fence, lantern, oak_sign). Root cause:
     * the persistent anchor is a boolean membership set, not a stored height, so every lane that
     * consumed it answered from the KIND of support below rather than that support's ACTUAL dy —
     * a flat {@code -0.5} constant. The GEOMETRIC lane already resolved the same relationship
     * correctly ({@code OffsetRaycastTargetingTest#lanternOnCompoundMinusOneSupportInheritsMinusOne}
     * has always asserted {@code -1.0}); this makes the anchor lanes and their mirrors AGREE with
     * it rather than inventing new behaviour.
     *
     * <p>Floors at {@code -0.5}: when the support resolves to nothing deeper (a flat bottom slab,
     * a persisted anchor whose support was broken away, air, a TOP/DOUBLE slab), every caller
     * keeps the exact value it returned before this resolver existed. Clamped at
     * {@link #minResolvedDy()}.
     *
     * <p>Never calls {@link #getYOffset} — safe inside the {@code IN_GET_Y_OFFSET} guard.
     */
    /**
     * THE ANCHORED-CELL HEIGHT — asked in exactly one place, so a stored placement height and the
     * live resolver can never be consulted by only half the lanes.
     *
     * <p>{@code LAW.md} lane G, proven live by {@code NeighborUpdateInvarianceTest}: an anchor is
     * presence, not magnitude, so every lane holding one used to answer by deriving the number
     * afresh from whatever happened to still be standing underneath — and a neighbour edit that
     * removed that support silently moved a placed block. When
     * {@link SlabPlacementDyAttachment} holds the height this cell was placed at, that number IS
     * the answer and no surrounding block gets a vote. With no stored fact — every cell of every
     * world saved before the store existed — this is {@link #loweredFollowerDy} verbatim, so
     * absence is indistinguishable from the behaviour that shipped.
     *
     * <p>Folded into the shared helper rather than patched at one call site, per this project's
     * shared-predicate law: six lanes ask an anchored cell how high it sits (the two
     * {@link #getYOffsetInner} branches, the two support mirrors, the column walk, and the
     * bottom-slab support mirror). If one answered from the store and another from the live walk,
     * the two would disagree about the same cell — the split this file has already paid for.
     */
    private static double anchoredCellDy(BlockView world, BlockPos pos, int depth) {
        double stored = SlabPlacementDyAttachment.storedDy(world, pos);
        if (!Double.isNaN(stored)) {
            return stored;
        }
        return loweredFollowerDy(world, pos, depth);
    }

    private static double loweredFollowerDy(BlockView world, BlockPos pos, int depth) {
        // NO FACTS AT ALL keeps the historical -0.5 floor. A null world or pos is not "the tower
        // is deeper than I looked" — it is "there is nothing here to look at", the same condition
        // as the NaN seat at the bottom of this method, and it must answer the same way.
        if (world == null || pos == null) {
            return -0.5;
        }
        // BUDGET EXHAUSTED — CLAMP DOWN, NEVER UP (Stage 3, 2026-08-07). This used to return the
        // bare -0.5 floor above, which says "I found nothing" about a walk that in fact found
        // MAX_SUPPORT_RESOLVE_DEPTH consecutive lowered courses and ran out of budget mid-descent.
        // The floor is SHALLOWER than the cap, so the first course past the budget read HIGHER
        // than the course beneath it: a tower that steps down, down, down and then pops back up a
        // full block. Exhaustion means "at least this deep, and I stopped counting", so the honest
        // answer is the deepest value this line will resolve.
        //
        // Invisible at today's cap and provably so — MEASURED, not argued, by Stage 0's B-1 and
        // re-measured by SupportDepthBudgetTest: every consumer of this value either clamps it to
        // minResolvedDy() after subtracting one course's drop, or compares it against a threshold
        // that -0.5 and minResolvedDy() are on the same side of, for as long as
        // minResolvedDy() == -(one course's drop) * 2. It stops being invisible the instant the
        // cap moves, which is exactly why it is repaired BEFORE Stage 4 rather than with it.
        if (depth >= MAX_SUPPORT_RESOLVE_DEPTH) {
            return minResolvedDy();
        }
        double seat = supportSeatDy(world, pos.down(), depth + 1);
        if (Double.isFinite(seat)) {
            if (seat < -0.5 - 1.0e-6) {
                return Math.max(seat, minResolvedDy());
            }
            // FLUSH-SEAT GUARD (live pass 2026-08-05, "interpenetration row"): a
            // seat of exactly 0.0 means the support's top face is AT the grid line (flush solid
            // non-slab block, or a flush TOP/DOUBLE slab). The historical "anchored ⇒ at least
            // -0.5" floor would sink the follower half a block INSIDE that support (the mega
            // board's z=14 anchored stone_slab z-fighting its flush stone), so a follower whose
            // lowered volume would enter the seat reads 0.0 — it rises OUT of its support, the
            // WYSIWYG-correct direction. A TOP-slab follower keeps the floor: at -0.5 it fills
            // the LOWER half of its own cell and rests ON the flush seat (BS-FB-1S). NaN seats
            // (air / non-solid below — the legitimate cantilever alignment, or a persisted
            // anchor whose support was broken away) never reach here and keep the -0.5 floor.
            if (seat > -1.0e-6) {
                BlockState follower = getBlockStateOrNull(world, pos);
                if (follower != null && followerSinksIntoFlushSeat(follower)) {
                    return 0.0;
                }
            }
        }
        return -0.5;
    }

    /**
     * The dy a block resting at {@code supportPos.up()} must take to sit flush on the ACTUAL
     * rendered top face of the support at {@code supportPos}.
     *
     * <ul>
     *   <li>HALF-HEIGHT seat (vanilla bottom slab): the top face is half a block below the grid,
     *       so the follower takes the slab's own dy PLUS {@code -0.5}. This is exactly the
     *       pre-existing "mixed slab" compound.</li>
     *   <li>FULL-HEIGHT seat (any non-slab support whose top face is at its own cell top — see
     *       {@link #presentsCellTopAsTopFace}): the follower's grid bottom already coincides with
     *       the support's grid top, so it inherits the support's dy unchanged. This is the lane
     *       that was missing — {@code loweredBottomSlabSupportDy} reports {@link Double#NaN} for
     *       anything that is not a bottom slab, so the live {@code stripped_jungle_log} support
     *       contributed nothing at all.</li>
     * </ul>
     *
     * <p><b>THE {@code -0.5} FLOOR IN {@link #loweredFollowerDy} IS THIS METHOD'S {@code NaN}, AND
     * IT MASKED THIS BUG FOR AS LONG AS THE TRUE ANSWER WAS ALSO {@code -0.5}.</b> Every support
     * this method cannot classify sends its follower to that floor, and until a {@code -1.0}
     * support existed the floor coincided with the right answer everywhere, so nothing was
     * observably wrong. The floor is KEPT, deliberately and narrowly, because it is the only thing
     * holding a persisted anchor whose support was broken away (removing it would pop such a cell
     * to {@code 0.0} in every world saved before the placement-dy store existed — a LAW 1
     * violation, and the exact regression class this file exists to prevent).
     *
     * <p><b>What it still masks, now that the full-height arm is geometric</b> — say it out loud
     * rather than let the next {@code -1.5} lattice discover it: supports that occupy a REAL,
     * sub-cell fraction of their own cell. A carpet ({@code 1/16}), a snow layer, a torch, a
     * standing lantern, a closed bottom trapdoor ({@code 3/16}), a honey block ({@code 15/16}) and
     * a non-{@code UP} wall post ({@code 14/16}) all draw a genuine top face at a height this
     * resolver has no vocabulary for, because this line's whole offset alphabet is
     * {@code {-1.0, -0.5, 0.0}} ({@code DY_SPEC} CS-CAP). Anything resting on one of those still
     * takes the floor. That is a bounded, named residual — not "the support type is excluded".</p>
     *
     * <p>FLUSH seat ({@code 0.0}): the support is present and its top face is AT the grid line —
     * a flush solid non-slab full block, or a flush TOP/DOUBLE slab (top face at the cell top).
     * Reported distinctly from "no qualifying seat" so {@link #loweredFollowerDy} can refuse the
     * {@code -0.5} floor there instead of sinking the follower half a block inside its own
     * support (the live-reported "interpenetration row").
     *
     * <p>Returns {@link Double#NaN} for a support that is none of these (air, a non-solid object,
     * a cantilever-lowered block), leaving the caller on its pre-existing {@code -0.5} floor.
     */
    private static double supportSeatDy(BlockView world, BlockPos supportPos, int depth) {
        BlockState support = getBlockStateOrNull(world, supportPos);
        if (support == null || support.isAir()) {
            return Double.NaN;
        }
        double bottomSlabDy = loweredBottomSlabSupportDy(world, supportPos, depth);
        if (!Double.isNaN(bottomSlabDy)) {
            return bottomSlabDy - 0.5;
        }
        // ASKED OF THE SEAT, NOT OF ITS CLASS. cellTopSupportDy is loweredFullHeightSupportDy
        // without the `instanceof SlabBlock` reject, because a seat is a FACE and a TOP or DOUBLE
        // slab draws its top face at exactly its own cell top (MEASURED: cullingShape maxY = 1.0
        // and outline maxY = 1.0 for both; a DOUBLE slab is additionally an opaque full cube).
        // A BOTTOM slab cannot arrive here at all — the half-height arm above claims every bottom
        // slab unconditionally — and it would be refused anyway (MEASURED: all four terms of
        // presentsCellTopAsTopFace are false, cull/outline maxY = 0.5). The reject is kept on the
        // wrapper for the UNDERSIDE and lowering-source consumers, which ask a different question
        // about the same block; see loweredFullHeightSupportDy.
        double fullBlockDy = cellTopSupportDy(world, supportPos, support, depth);
        if (!Double.isNaN(fullBlockDy)) {
            return fullBlockDy;
        }
        if (isFlushSeat(world, supportPos, support)) {
            return 0.0;
        }
        return Double.NaN;
    }

    /**
     * FLUSH-SEAT classifier: true iff the block at {@code seatPos} is a full-height seat whose top
     * face renders AT the grid line — a solid non-slab full block that is not lowered by any lane,
     * or a TOP/DOUBLE slab that is not lowered (its top face is at the cell top, so a {@code -0.5}
     * follower would interpenetrate its upper half exactly like full stone). A BOTTOM slab is
     * never a flush seat (half-height — the mixed-slab compound owns that case), and a Terrain
     * Slabs skip-offset surface is never classified here (it owns its own geometry). Conservative
     * by construction: any lowering source (anchor, column, direct-custom, and — for a solid block
     * with AIR below — the cantilever/gap-fill lanes) declassifies the seat, keeping every
     * previously-correct {@code -0.5} case byte-identical. Recursion-safe: only descends.
     */
    private static boolean isFlushSeat(BlockView world, BlockPos seatPos, BlockState seat) {
        if (world == null || seatPos == null || seat == null || seat.isAir()
                || !seat.getFluidState().isEmpty()
                || CompatHooks.shouldSkipOffset(seat)) {
            return false;
        }
        if (seat.getBlock() instanceof SlabBlock) {
            if (!seat.contains(SlabBlock.TYPE) || isBottomSlab(seat)) {
                return false;
            }
            // TOP/DOUBLE: flush iff nothing lowers it (vertical sources incl. its own anchor,
            // or side adjacency — whose own flush-seat guard recurses strictly downward).
            return !isVerticallyLoweredSlabSource(world, seatPos, seat)
                    && !isAdjacentSideSlabLowered(world, seatPos, seat);
        }
        if (!seat.isSolidBlock(world, seatPos)) {
            return false;
        }
        double ownDy = loweredFullHeightSupportDy(world, seatPos, seat);
        if (Double.isFinite(ownDy) && ownDy < -1.0e-6) {
            return false;   // the seat itself renders lowered
        }
        // The remaining lowering lanes for a solid block (cantilever adjacency, gap-fill from an
        // anchored block above) require AIR below it; a solid seat standing on a non-air block can
        // only render flush (0.0 — genuinely flush or frozen-flat). An air-below solid seat stays
        // unclassified (NaN upstream), preserving the historical floor for cantilever supports.
        BlockState belowSeat = getBlockStateOrNull(world, seatPos.down());
        return belowSeat != null && !belowSeat.isAir();
    }

    /**
     * Follower-volume half of the flush-seat guard: does this follower's {@code -0.5} volume
     * ENTER the cell below it? True for every non-slab follower and for BOTTOM/DOUBLE slabs
     * (their lowered volume occupies the upper half of the cell below). False ONLY for a TOP
     * slab: at {@code -0.5} it fills the LOWER half of its own cell ({@code [y, y+0.5]}) and
     * rests ON a flush seat instead of entering it — the BS-FB-1S alignment.
     */
    private static boolean followerSinksIntoFlushSeat(BlockState follower) {
        return !isTopSlab(follower);
    }

    /**
     * TOP-FACE GEOMETRY, ASKED OF THE BLOCK ITSELF — the height of this state's own top face
     * within its own cell, as a fraction of a block ({@code 1.0} = the cell top). {@code NaN}
     * when the state occupies no volume at all.
     *
     * <p>Precomputed into the state's shape cache at registry bootstrap, so this is a field read:
     * no allocation, no world access, no per-call shape maths, and view-independent (it answers
     * identically on the render thread and the server thread, unlike {@code isSolidBlock}, which
     * returns FALSE for a solid cube under {@code ChunkRendererRegion} — the trap documented on
     * {@link #isClassFlushPinnedSubject}).
     *
     * <p><b>It is a CULLING shape, so it is empty for anything that does not occlude</b> — a door,
     * a glass pane, iron bars all report empty here even though they visibly reach their cell top.
     * That is why this is an ACCEPT-only term inside {@link #presentsCellTopAsTopFace} and never a
     * reject, and why {@link #rawOutlineReachesCellTop} exists behind it.
     */
    private static boolean cullingShapeReachesCellTop(BlockState state) {
        VoxelShape culling = state.getCullingShape();
        return !culling.isEmpty() && culling.getMax(Direction.Axis.Y) >= 1.0 - 1.0e-6;
    }

    /**
     * Reentrancy fence for the raw-shape probe below, and the reason
     * {@link #rawOutlineReachesCellTop} can ask for an outline at all.
     *
     * <p>{@code SlabSupportStateMixin} OFFSETS {@code getOutlineShape} (and the raycast shape) by
     * the very dy this resolver is computing, and {@code CarpetDyShapeMixin} does the same for
     * carpets. An unguarded read here would classify a support's geometry from a shape that had
     * already been moved by the answer — and, on a client world, would additionally publish the
     * recursion guard's {@code 0.0} into the shared visual-dy cache for that position. While this
     * flag is set every one of those hooks returns the vanilla shape untouched, so what comes back
     * is the block's own un-offset geometry and nothing is written anywhere.
     */
    private static final ThreadLocal<Boolean> IN_RAW_SHAPE_PROBE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** True while {@link #rawOutlineReachesCellTop} is reading a raw shape — see that field. */
    public static boolean isRawShapeProbeActive() {
        return IN_RAW_SHAPE_PROBE.get();
    }

    /**
     * Memo for {@link #rawOutlineReachesCellTop}. Keyed on {@link BlockState}, which is a permanent
     * registry singleton with identity equality, so this map is bounded by the number of distinct
     * states ever asked and can never leak. It exists so the cold path costs ONE shape read per
     * state per JVM rather than one per call: this resolver runs during chunk meshing and this
     * project has shipped a per-block hot-path lag regression twice.
     */
    private static final java.util.concurrent.ConcurrentHashMap<BlockState, Boolean>
            RAW_OUTLINE_REACHES_CELL_TOP = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Does this state's own, un-offset outline reach the top of its cell?
     *
     * <p>Asked against {@link EmptyBlockView} at {@link BlockPos#ORIGIN} on purpose: the outline of
     * every family this term exists for (doors, panes, iron bars, gates, ladders, chains,
     * trapdoors) is a pure function of the STATE — vanilla bakes the neighbour connections into the
     * state's own properties — so a position-independent read gives the identical answer and is
     * memoisable. A block with genuinely dynamic bounds answers from its default geometry, which
     * can only under-admit (falling back to the pre-existing behaviour), never invent a height.
     */
    private static boolean rawOutlineReachesCellTop(BlockState state) {
        Boolean cached = RAW_OUTLINE_REACHES_CELL_TOP.get(state);
        if (cached != null) {
            return cached;
        }
        boolean reaches;
        // Save/restore rather than set/clear: the mixins honour this flag by returning early, so a
        // nested probe is impossible today, but a future hook that is NOT guarded would otherwise
        // have the flag cleared out from under it by the inner finally.
        boolean outerProbe = IN_RAW_SHAPE_PROBE.get();
        IN_RAW_SHAPE_PROBE.set(Boolean.TRUE);
        try {
            VoxelShape outline = state.getOutlineShape(
                    net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN,
                    net.minecraft.block.ShapeContext.absent());
            reaches = !outline.isEmpty() && outline.getMax(Direction.Axis.Y) >= 1.0 - 1.0e-6;
        } catch (RuntimeException ignored) {
            // A block whose outline genuinely needs world context (a block entity read, a
            // neighbour query) may object to the empty view. That is not a reason to fail a render
            // — it is a reason to leave that support unclassified, exactly as it was before this
            // term existed.
            reaches = false;
        } finally {
            IN_RAW_SHAPE_PROBE.set(outerProbe);
        }
        RAW_OUTLINE_REACHES_CELL_TOP.put(state, reaches);
        return reaches;
    }

    /**
     * Does the block at {@code pos} present its OWN CELL TOP as a top face — i.e. is a block
     * resting on it seated at this block's own dy, with no further drop?
     *
     * <p>This is the question {@code isSolidBlock} was standing in for and OVER-asking. Solidity is
     * a VOLUME statement ("is the collision box a full cube"); a seat only needs a FACE statement
     * ("is the top at the cell top"). A fence, a door, a wall, a pane and a set of iron bars all
     * draw their top face at exactly the cell top and all fail the volume test, which is why a
     * block resting on any of them lost its support's height entirely (live pass
     * 2026-08-06: sign / lantern / log at {@code -0.5} on a
     * {@code birch_fence} at {@code -1.0}).
     *
     * <p>Four terms, ordered so the common cases never pay for the rare ones:
     * <ol>
     *   <li>{@link BlockState#isOpaqueFullCube()} — a precomputed, view-independent flag. Ordinary
     *       terrain never gets past this line.</li>
     *   <li>{@link #cullingShapeReachesCellTop} — the state's culling shape is precomputed into
     *       the shape cache at registry bootstrap, so this is a field read: no allocation, no
     *       world access, no shape maths. It catches fences, walls, stairs and top/double slabs.
     *       It is only ever used to ACCEPT: a culling shape is a subset of the block's volume, so
     *       "the culling shape reaches the cell top" implies the block does. It can never
     *       false-accept, and where it is empty (doors, panes — culling is about occluding
     *       neighbours, and a transparent block occludes nothing) the next terms answer.</li>
     *   <li>{@code isSolidBlock} — kept as a widening term so every state admitted before this
     *       predicate existed is still admitted byte-identically. Nothing was removed here.</li>
     *   <li>{@link #rawOutlineReachesCellTop} — the memoised, probe-guarded outline read, for the
     *       cold tail the first three miss (doors, panes, bars, gates, ladders, trapdoors).</li>
     * </ol>
     *
     * <p>NOT a classname list, and it must never become one: {@code LAW 2} says eligibility follows
     * geometry, and this campaign has now found exclude-by-classname NINE times — the ninth was a
     * {@code instanceof SlabBlock} line in {@link #loweredFullHeightSupportDy} that kept this
     * predicate from ever being asked about a TOP or DOUBLE slab, which both pass it. If a block
     * type appears to need adding here, the shape it draws is the thing to ask about instead.
     */
    private static boolean presentsCellTopAsTopFace(BlockView world, BlockPos pos, BlockState state) {
        return state.isOpaqueFullCube()
                || cullingShapeReachesCellTop(state)
                || state.isSolidBlock(world, pos)
                || rawOutlineReachesCellTop(state);
    }

    /**
     * Recursion-safe rendered dy of a NON-SLAB support whose top face is at its own cell top —
     * the support a follower's grid bottom rests directly on, whether that support is above the
     * caller (a hanger's ceiling) or below it (a seat). Mirrors the anchor / direct-custom /
     * column branches of {@link #getYOffsetInner} without re-entering {@link #getYOffset}. Returns
     * a negative lowered dy ({@code -0.5} / {@code -1.0}), {@code 0.0} (not lowered), or
     * {@link Double#NaN} (this support does not present a full-height top face).
     *
     * <p><b>Renamed from {@code loweredFullBlockUndersideSupportDy} (2026-08-06).</b> The old name
     * and the old {@code isSolidBlock} gate both said "full BLOCK" when the geometry only ever
     * needed "full-HEIGHT top face". Under the old gate a fence, a door, a wall and a pane matched
     * no arm of {@link #supportSeatDy} at all, so everything resting on one fell to
     * {@link #loweredFollowerDy}'s {@code -0.5} floor — invisible while the true answer happened to
     * be {@code -0.5}, and a visible half-block gap the moment a real {@code -1.0} support existed
     * (live pass 2026-08-06: sign/lantern/log at {@code -0.5} on a
     * {@code birch_fence} at {@code -1.0}). See {@link #presentsCellTopAsTopFace}.
     */
    private static double loweredFullHeightSupportDy(BlockView world, BlockPos pos, BlockState state) {
        return loweredFullHeightSupportDy(world, pos, state, 0);
    }

    private static double loweredFullHeightSupportDy(BlockView world, BlockPos pos, BlockState state,
                                                     int depth) {
        // THE SLAB REJECT LIVES HERE, ON THE WRAPPER, AND NOWHERE ELSE. Its four remaining callers
        // do NOT ask the top-face question this method's body answers:
        //   * the two hanger lanes (ceilingHungDecorationDy, getYOffsetInner's underside-hanger
        //     branch) read the support ABOVE, where the relevant surface is the UNDERSIDE. Both
        //     call loweredSlabUndersideSupportDy first, which carries the TOP slab's +0.5
        //     underside term; answering a slab from the cell-top body would drop that term and
        //     misplace the hanger by half a block.
        //   * getYOffsetInner's object-on-a-support lane has its own TOP/DOUBLE slab branch a few
        //     lines below, routed through loweredSlabUndersideSupportDy so it honours
        //     shouldSkipOffset (a Terrain Slabs slab renders flush) and the client visual-dy
        //     cache. Answering slabs here would preempt that branch and drop both guards — the
        //     live lantern-under-TS regression.
        //   * loweredStandingObjectDy is a LOWERING-SOURCE probe for the bounded column walks, and
        //     slabs already have their own source predicates there (hasLoweredSlabSupport,
        //     isVerticallyLoweredSlabSource). Admitting them here would widen what counts as a
        //     source, which is the twice-burned Terrain Slabs over-lowering hazard.
        // The remaining two call sites (isLoweredSideSlabSource, isFlushSeat) branch on slabs
        // before they reach this method, so the reject is unreachable for them either way.
        // supportSeatDy — the SEAT question — deliberately calls the body directly.
        if (state != null && state.getBlock() instanceof SlabBlock) {
            return Double.NaN;
        }
        return cellTopSupportDy(world, pos, state, depth);
    }

    /**
     * The body of {@link #loweredFullHeightSupportDy}, asked of ANY block whose top face is at its
     * own cell top — including a TOP or DOUBLE slab.
     *
     * <p><b>Why the split (live-confirmed 2026-08-06).</b>
     * The wrapper's {@code instanceof SlabBlock} line was a CLASS test standing in for the top-face
     * question, the ninth of that shape found in this campaign, and
     * {@code SlabAnchorAttachment.recordPlacementDy} already carried a note describing the hole it
     * left: a lowered TOP or DOUBLE slab support matched none of {@link #supportSeatDy}'s arms, so
     * anything resting on one took {@link #loweredFollowerDy}'s {@code -0.5} floor — and, because
     * that read happens at placement time, the floor is the number LAW 1 then froze. Live:
     * {@code smooth_stone_slab[type=double]} at {@code dy=-1.0000}, a {@code stripped_jungle_log}
     * placed on it captured {@code -0.5000}. The same session's fence, chain and BOTTOM-slab
     * supports were all correct, which is what identified the reject rather than the arithmetic.
     *
     * <p>{@code shouldOffset} never offsets a slab, so for a slab this body can only answer from
     * the stored placement height or the anchor lane — the two facts LAW 1 says are authoritative
     * — and returns {@link Double#NaN} otherwise, leaving pre-store worlds on the path they always
     * took.
     */
    private static double cellTopSupportDy(BlockView world, BlockPos pos, BlockState state,
                                           int depth) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || !state.getFluidState().isEmpty()
                || !presentsCellTopAsTopFace(world, pos, state)) {
            return Double.NaN;
        }
        // THE STORED PLACEMENT HEIGHT IS THE ANSWER, asked here exactly as getYOffsetInner asks it
        // at the top of its own body — for EVERY cell, not only anchored ones. LAW 1 says a placed
        // height is frozen and every later read returns it verbatim, and this mirror is a "later
        // read". Reading it only inside the anchor branch below would leave lane B's cells (lowered
        // placements that earn no anchor, whose height freezeLoweredOnPlace records WITHOUT one)
        // resolving one way for themselves and another way for whatever rests on them — the
        // two-lanes-disagree split this file has already paid for repeatedly. Absent facts answer
        // NaN, so every world saved before the store existed takes the identical path below.
        double storedDy = SlabPlacementDyAttachment.storedDy(world, pos);
        if (!Double.isNaN(storedDy)) {
            return storedDy;
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            // An anchor records THAT this block was placed on a lowered surface, not HOW FAR down
            // it went. anchoredCellDy supplies the stored placement height when there is one, and
            // otherwise resolves from this block's own support exactly as before.
            return anchoredCellDy(world, pos, depth);
        }
        double directCustomDy = directCustomSlabSupportDy(world, pos, state);
        if (!Double.isNaN(directCustomDy)) {
            return directCustomDy;
        }
        if (shouldOffset(world, pos, state)) {
            BlockState belowSlab = getBlockStateOrAir(world, pos.down());
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, pos.down(), belowSlab)) {
                return -1.0;
            }
            double columnDy = slabColumnYOffset(world, pos, depth);
            if (columnDy != 0.0) {
                return columnDy;
            }
            return -0.5;
        }
        return Double.NaN;
    }

    /**
     * Shared ownership rule for client raycast/outline retargeting of lowered
     * block-entity-style blocks (e.g. chests) sitting above a bottom slab.
     *
     * <p>When a block-entity block is visually lowered by -0.5 (its model, via
     * {@code BlockEntityOffsetMixin}, and its outline/raycast shapes, via
     * {@code SlabSupportStateMixin}), the lower half of its visible footprint
     * overflows into {@code pos.down()}'s voxel. Vanilla DDA raycast traversal
     * cannot see that overflowed portion at {@code pos} and instead hits the
     * slab below. This helper is the single source of truth for detecting
     * that case so raycast retarget and outline agree.
     *
     * @return true iff {@code state} is a {@link BlockEntityProvider} block
     *         at {@code pos} whose {@link #getYOffset} is exactly {@code -0.5}.
     */
    public static boolean isLoweredBlockEntityVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!(state.getBlock() instanceof BlockEntityProvider)) {
            return false;
        }
        return getVisualYOffset(world, pos, state) == -0.5;
    }

    public static boolean isLoweredTorchVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (!(block instanceof net.minecraft.block.TorchBlock
                || block instanceof net.minecraft.block.WallTorchBlock)) {
            return false;
        }
        // compound dy (-1.0) also qualifies: torch above an adjacent-lowered bottom slab
        return getVisualYOffset(world, pos, state) < 0.0;
    }

    public static boolean isLoweredBedVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        return state.getBlock() instanceof net.minecraft.block.BedBlock
                && state.contains(Properties.BED_PART)
                && getVisualYOffset(world, pos, state) == -0.5;
    }

    public static boolean isLoweredCustomSupportedObjectVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        return isDirectCustomSlabSupportedObject(world, pos, state)
                && getVisualYOffset(world, pos, state) == -0.5;
    }

    /**
     * Redstone dust support surface — treat slab tops like valid ground for placement/survival.
     */
    public static boolean isRedstoneSupportTopSurface(BlockView world, BlockPos pos) {
        BlockState state = getBlockStateOrAir(world, pos);

        if (state.isSideSolidFullSquare(world, pos, Direction.UP)) {
            return true;
        }

        return isSupportingSlab(state) && (isBottomSlab(state) || isTopSlab(state));
    }

    /**
     * Category predicate for the generic slab-column lowering fallback in
     * {@link #shouldOffset}.
     *
     * <p>Returns {@code true} for blocks that should visually sit on slabs
     * when a bottom slab exists somewhere in their column:
     * <ul>
     *   <li>Every {@link BlockEntityProvider} block — chests, hoppers,
     *       furnaces, jukeboxes, spawners, end portal frames, beacons,
     *       banners, signs (standing), etc. This matches the
     *       {@link #isLoweredBlockEntityVisual} contract and ensures
     *       full-cube BE blocks (jukebox, spawner, …) lower alongside
     *       non-full-cube BE blocks (chest, hopper, …).</li>
     *   <li>Log-family blocks in {@link BlockTags#LOGS} — logs, wood,
     *       stripped variants, and nether stems, once block tags are bound.</li>
     *   <li>Any block that is not a full solid cube — fences, walls, panes,
     *       torches, buttons, pressure plates, wall signs, etc.
     *       ({@code !state.isSolidBlock}).</li>
     * </ul>
     *
     * <p>Explicitly excludes plain solid world cubes (stone, dirt, planks,
     * cobblestone, sand, gravel, terracotta, …) so natural terrain does not
     * visually drop when a slab happens to sit below it.
     */
    private static boolean isSlabSitCandidate(BlockView world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockEntityProvider) {
            return true;
        }
        if (block instanceof CraftingTableBlock) {
            return true;
        }
        if (state.isOf(Blocks.BOOKSHELF)
                || state.isOf(Blocks.DRIED_KELP_BLOCK)
                || block instanceof ChiseledBookshelfBlock) {
            return true;
        }
        if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
            return true;
        }
        if (isLogFamilySlabSitCandidate(state)) {
            return true;
        }
        // Plain solid world cubes (stone, dirt, grass, sand, ore, …) are NOT slab-sit
        // objects: lowering an opaque full cube -0.5 while the chunk mesher still culls at
        // its grid-height voxel tears see-through holes across natural Terrain Slabs terrain
        // (the released-hotfix world-hole bug). They must stay at grid height — only the
        // curated object cubes / non-solid objects handled above lower onto a TS surface.
        //
        // The terrain test MUST be world-view-independent. The render mesher runs this on a
        // ChunkRendererRegion, where state.isSolidBlock(world,pos) diverges from ClientWorld:
        // isSolidBlock -> solidBlockPredicate -> isFullCube -> isShapeFullCube ->
        // getCollisionShape -> getOutlineShape (which SlabSupportStateMixin offsets) resolves
        // against the region-clamped view and can come back NOT-full-cube for a plain stone
        // cube, making !isSolidBlock TRUE on the render thread only -> terrain lowers on the
        // worker mesh while main-thread culling stays put -> world holes (the empirically
        // traced render-thread divergence). state.isOpaqueFullCube() reads a precomputed
        // boolean field baked at blockstate construction (AbstractBlockState.opaqueFullCube),
        // so it returns the SAME answer under ChunkRendererRegion and ClientWorld. Use it to
        // pin natural terrain flush regardless of which world view asks. Non-opaque / non-cube
        // objects (fences, panes, torches, lanterns, slabs-on-objects, …) are not opaque full
        // cubes, so they still fall through to the !isSolidBlock candidate test and keep
        // lowering exactly as before.
        if (state.isOpaqueFullCube()) {
            return false;
        }
        return !state.isSolidBlock(world, pos);
    }

    private static boolean isLogFamilySlabSitCandidate(BlockState state) {
        try {
            return state.isIn(BlockTags.LOGS);
        } catch (IllegalStateException e) {
            if ("Tags not bound".equals(e.getMessage())) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Recursion-safe rendered dy of a lowered log-family (a {@link BlockTags#LOGS} member that is
     * itself a {@link #isSlabSitCandidate} lowered onto a Terrain Slabs / bottom-slab surface)
     * carrier directly beneath {@code pos}, used so an ordinary opaque full cube resting on that
     * log shares its drop instead of staying flush (GH #22 slab-log DODO). Deliberately narrow:
     * ONLY a direct log-family support qualifies — walking through arbitrary opaque terrain would
     * reopen the world-hole guard this same method sits beside. Mirrors
     * {@link #loweredFullHeightSupportDy} in shape but for a below-support (not an
     * underside-hanger) query, and never calls {@link #getYOffset} so it is safe to call from
     * inside the {@code IN_GET_Y_OFFSET} recursion guard.
     */
    private static double loweredCuratedCarrierDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isOpaqueFullCube()) {
            return Double.NaN;
        }
        BlockPos belowPos = pos.down();
        BlockState below = getBlockStateOrNull(world, belowPos);
        // The carrier may be ANY curated slab-sit candidate (log family, jukebox and other block
        // entities, crafting table, bookshelf, dried kelp, chiseled bookshelf), not just logs —
        // GH #22 applies verbatim to a full cube on any of them (adversarial audit: jukebox
        // carrier left a gap). isSlabSitCandidate is view-independent for the opaque-cube world-
        // hole guard, so natural terrain (stone/dirt) stays excluded exactly as before; the
        // directCustomSlabSupportDy < 0 guard below fires ONLY for a carrier actually lowered onto
        // a TS/bottom-slab surface, so a plain carrier on solid ground still returns NaN (flush).
        // KNOWN-PARTIAL: this shares the carrier's DIRECT -0.5; a carrier itself lowered -1.0 on a
        // compound (mixed) slab is NOT yet followed to -1.0 (tracked internally).
        if (below == null || !isSlabSitCandidate(world, belowPos, below)) {
            return Double.NaN;
        }
        double directCustomDy = directCustomSlabSupportDy(world, belowPos, below);
        if (Double.isFinite(directCustomDy) && directCustomDy < -1.0e-6) {
            return directCustomDy;
        }
        return Double.NaN;
    }

    /**
     * A top-like ceiling surface that <em>Slabbed itself lowers</em> — i.e. one that a
     * ceiling-attached block below should follow UP by +0.5 (raised-attach). A Terrain Slabs
     * surface is EXCLUDED: TS owns its own vertical offset ({@link CompatHooks#shouldSkipOffset}),
     * so treating it as a lowering top-like ceiling pushes the hanger UP into the flush TS block
     * (the "smoosh"). This is the L4 invariant in ONE place, for EVERY dy-computing ceiling walk
     * (both the {@code ceilingHungDecorationDy} walk and the two {@code getYOffsetInner} walks) —
     * so the guard can never again be applied to one walk and forgotten on the others.
     *
     * <p>NOTE: placement/attachment callers use {@link #isTopLikeCeilingSurface} directly — a
     * lantern may still ATTACH to a TS underside; only its rendered dy must stay flush.
     */
    private static boolean isLoweringTopLikeCeiling(BlockState state) {
        // DEPRECATED (2026-07-03 maintainer ruling, live): the +0.5 "reach-up" for ceiling-attached
        // objects (lantern / dripstone / chain / …) under a top slab is deprecated — everything
        // hangs FLUSH now. In live testing the reach-up smooshed those objects UP into the slab;
        // flush looked better. Returning false disables the +0.5 at ALL three ceiling walks (the
        // ceilingHungDecorationDy cursor loop + the two getYOffsetInner walks) from ONE place, so
        // the ruling is trivially reversible if it regresses (ruled "subject to further review").
        // The `slabSupportDy + 0.5` flush-COMPENSATION for a LOWERED top slab (SlabSupport.java:817
        // and :1012) is a DIFFERENT path — it nets 0.0 (flush against the lowered underside), not a
        // reach-up — and deliberately stays. Prior body: !shouldSkipOffset && isTopLikeCeilingSurface.
        return false;
    }

    private static boolean isTopLikeCeilingSurface(BlockState state) {
        CompatSlabSurfaceKind customKind = CompatHooks.customSlabSurfaceKind(state);
        if (customKind == CompatSlabSurfaceKind.TOP_LIKE || customKind == CompatSlabSurfaceKind.DOUBLE_LIKE) {
            return true;
        }
        return isTopSlab(state)
                || isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    private static double directCustomSlabSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (!isDirectCustomSlabSupportedObject(world, pos, state)) {
            return Double.NaN;
        }
        return -0.5;
    }

    /**
     * Recursion-safe rendered dy of a vanilla BOTTOM-slab support directly beneath a
     * standing object, used to compound a "mixed slab" lowering so the object follows
     * its support's own drop in addition to the normal sit-on-bottom-slab -0.5. Mirrors
     * the lowered cases of the slab branch in {@link #getYOffsetInner} without ever
     * re-entering {@link #getYOffset}. Returns {@link Double#NaN} when the support is not
     * a vanilla bottom slab and {@code 0.0} when it is a bottom slab that is not lowered,
     * so callers (which gate on {@code < -1e-6}) leave the flush case untouched.
     */
    private static double loweredBottomSlabSupportDy(BlockView world, BlockPos supportPos) {
        return loweredBottomSlabSupportDy(world, supportPos, 0);
    }

    private static double loweredBottomSlabSupportDy(BlockView world, BlockPos supportPos, int depth) {
        BlockState s = getBlockStateOrNull(world, supportPos);
        if (s == null
                || !(s.getBlock() instanceof SlabBlock)
                || !s.contains(SlabBlock.TYPE)
                || !isBottomSlab(s)
                || !s.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isAnchored(world, supportPos)) {
            // Same anchor-is-not-a-height correction as loweredFullHeightSupportDy: an
            // anchored bottom slab may itself sit deeper than -0.5, and its own placement height
            // wins when the store has it.
            return anchoredCellDy(world, supportPos, depth);
        }
        double directCustomDy = directCustomSlabSupportDy(world, supportPos, s);
        if (Double.isFinite(directCustomDy) && directCustomDy < -1.0e-6) {
            return directCustomDy;
        }
        if (isAdjacentSideSlabLowered(world, supportPos, s)) {
            return -0.5;
        }
        return 0.0;
    }

    private static boolean hasDirectCustomBottomLikeSupportColumn(BlockView world, BlockPos supportPos) {
        BlockPos cursor = supportPos;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState supportState = getBlockStateOrNull(world, cursor);
            if (supportState == null) {
                return false;
            }
            if (CompatHooks.customSlabSurfaceKind(supportState) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
                return true;
            }
            if (isDirectCustomSlabSupportSubject(world, cursor, supportState)) {
                cursor = cursor.down();
                continue;
            }
            return false;
        }
        return false;
    }

    /**
     * True iff Terrain Slabs OWNS the on-top offset of the object at {@code pos}: the object is
     * one Terrain Slabs' own on-top authority claims (vegetation, snow) and it rests directly on
     * a BOTTOM_LIKE Terrain surface. Such an object gets exactly ONE offset — Terrain Slabs' own
     * — so every Slabbed contribution lane must answer ZERO for it, over a NATIVE surface
     * (worldgen-shaped, no facts) and over an AUTHORED one (player-placed, stored height,
     * anchored) alike: the ownership answer must not depend on surface authorship. This is the
     * SAME predicate pair as the placement transaction's fact-minting gate
     * ({@code BlockItemPlacementIntentMixin}'s Terrain-owned check), held in ONE place with its
     * direct-support consumer so the live lanes and the transaction cannot drift apart (the
     * shared-predicate law).
     */
    private static boolean isTerrainOwnedOnTopObject(BlockView world, BlockPos pos, BlockState state) {
        return CompatHooks.terrainSlabsHandlesObjectOffset(state)
                && CompatHooks.customSlabSurfaceKind(getBlockStateOrNull(world, pos.down()))
                == CompatSlabSurfaceKind.BOTTOM_LIKE;
    }

    private static boolean isDirectCustomSlabSupportSubject(BlockView world, BlockPos pos, BlockState state) {
        Block block = state == null ? null : state.getBlock();
        boolean kelpFamily = block instanceof KelpBlock || block instanceof KelpPlantBlock;
        if (state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock && !isVanillaDirectCustomSlabSubject(state)
                || isTerrainOwnedOnTopObject(world, pos, state)
                // NOTE (2026-08-06, second ruling): no snow exclusion here either. All three
                // SUBJECT-side sites dropped it together — leaving one behind would be the
                // shared-predicate half-fix trap, with snow lowering on a vanilla slab but floating
                // on a Terrain Slabs surface in a TS-enabled live setup.
                || (!state.getFluidState().isEmpty() && !kelpFamily)
                || CompatHooks.shouldSkipOffset(state)) {
            return false;
        }
        return isVanillaDirectCustomSlabSubject(state) || isSlabSitCandidate(world, pos, state);
    }

    private static boolean isVanillaDirectCustomSlabSubject(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.contains(SlabBlock.TYPE)) {
            return false;
        }
        var id = Registries.BLOCK.getId(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    /**
     * Walks down from {@code pos} through non-air, non-slab blocks looking
     * for a bottom slab. Returns true as soon as one is found.
     *
     * <p>An anchored full block in the column also terminates the walk as a
     * positive — the anchor records that this block is itself lowered by -0.5
     * (its visible top sits at slab height), so anything stacked on top of it
     * inherits the same lowered surface even after the original BS support
     * has been broken.
     *
     * <p>A slab encountered anywhere in the bounded column walk that is itself
     * a lowered adjacent-side slab — i.e. a 1S/0.5S/double slab horizontally
     * beside an anchored or BS-supported FB — also counts as a positive: its
     * visible top face sits at the lowered support height, so anything stacked
     * above it (directly or through intermediate full blocks) must inherit the
     * same -0.5 dy. Vanilla top slabs that are not lowered still terminate the
     * walk false via the slab terminator below. Walk remains bounded by
     * {@link #MAX_CHAIN_DEPTH}.
     */
    private static boolean hasSlabInColumn(BlockView world, BlockPos pos) {
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = getBlockStateOrNull(world, cursor);
            if (cur == null) {
                return false;
            }
            if (isBottomSlab(cur)) {
                return true;
            }
            if (SlabAnchorAttachment.isAnchored(world, cursor)) {
                return true;
            }
            if (cur.getBlock() instanceof SlabBlock
                    && isAdjacentSideSlabLowered(world, cursor, cur)) {
                return true;
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isThinTopLayer(cur)) {
                return false;
            }
            // Natural-terrain stop: a SOLID full cube below means this block rests on solid
            // ground, NOT on a slab — so do not walk through it to a slab deeper in the column.
            // Walking through solid terrain lowered natural Stone/Dirt that merely had a Terrain
            // Slabs surface 1-16 blocks beneath it, tearing see-through world holes. A genuine
            // placed tower chains via its per-block anchor (checked above), not this raw walk.
            // Kept BEFORE the standing-object probe so terrain is excluded view-independently.
            if (cur.isOpaqueFullCube()) {
                return false;
            }
            // BUG B: a STANDING OBJECT cell (flower pot, lantern, …) is a lowering source iff it
            // RENDERS lowered — asked by height, not by whether it happens to hold an anchor. The
            // isAnchored test above is the same question asked of a flag, and potting a flower
            // clears that flag without moving the pot, which made everything stacked above it JUMP.
            if (Double.isFinite(loweredStandingObjectDy(world, cursor, cur))) {
                return true;
            }
            cursor = cursor.down();
        }
        return false;
    }

    private static double slabColumnYOffset(BlockView world, BlockPos pos) {
        return slabColumnYOffset(world, pos, 0);
    }

    private static double slabColumnYOffset(BlockView world, BlockPos pos, int depth) {
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = getBlockStateOrNull(world, cursor);
            if (cur == null) {
                return 0.0;
            }
            if (cur.getBlock() instanceof SlabBlock
                    && (SlabAnchorAttachment.isAnchored(world, cursor)
                    || isAdjacentSideSlabLowered(world, cursor, cur))) {
                // FIFTH mirror of the flat-constant defect, and the one the player SEES as a
                // snap-down. These two constants are a CLASS test plus two hardcoded numbers
                // standing in for the seat's actual top face, and they are only correct while the
                // slab sits at -0.5: a BOTTOM slab there seats at -1.0 and a TOP/DOUBLE slab there
                // seats at -0.5. A slab at -1.0 breaks both readings, and the -0.5 arm breaks
                // VISIBLY — live-recorded: a stripped_jungle_log
                // on a smooth_stone_slab[type=double] at dy=-1.0000 read -0.5000 here. Because
                // this is the LIVE lane, it is what the client draws until the server's stored
                // number reaches it; the server's own dyPlaceBefore was -0.5000 in the same
                // frames, so the disagreement was never about sync.
                //
                // supportSeatDy is the question these constants were approximating: what dy must a
                // block resting on this cell take to sit on its real top face. It is asked at the
                // cursor, so a BOTTOM slab still gets its own dy - 0.5 and a TOP/DOUBLE slab still
                // gets its own dy, exactly as the constants assumed at -0.5 — but read rather than
                // assumed. Recursion-safe: supportSeatDy only ever descends, shouldOffset refuses
                // slabs so cellTopSupportDy cannot re-enter this walk, and the depth is carried.
                //
                // NARROWED ON PURPOSE — this may only ever go DOWN. The seat is taken only when it
                // is strictly deeper than the constant it replaces, so a NaN seat, a flush seat, or
                // any shallower reading keeps the historical number byte-identical. The BOTTOM arm
                // is therefore provably unreachable by this change: its constant already equals
                // minResolvedDy(), so nothing can be strictly deeper and survive the clamp.
                //
                // THAT LAST SENTENCE IS CONTINGENT ON THE CAP'S VALUE, NOT ON THIS ARM (noted
                // 2026-08-07, Stage 2). The -1.0 below is a SHAPE — "a bottom slab at -0.5 presents
                // its top face one full block down" — while minResolvedDy() is a REFUSAL. The two
                // are equal today by coincidence of magnitude only, which is exactly why this
                // constant must NOT be rewritten to read minResolvedDy(): that would move a shape
                // whenever the cap moved. What does change when the cap deepens is the
                // unreachability argument above — the BOTTOM arm becomes reachable, correctly, and
                // nothing here needs editing, but do not re-derive "provably unreachable" from it.
                double historicalDy = isBottomSlab(cur) ? -1.0 : -0.5;
                double seatDy = supportSeatDy(world, cursor, depth + 1);
                if (Double.isFinite(seatDy) && seatDy < historicalDy - 1.0e-6) {
                    return Math.max(seatDy, minResolvedDy());
                }
                return historicalDy;
            }
            if (isBottomSlab(cur)) {
                return -0.5;
            }
            if (SlabAnchorAttachment.isAnchored(world, cursor)) {
                // Third mirror of the flat-constant defect: the anchored block in this column may
                // itself render deeper than -0.5, and whatever stacks on it takes its ACTUAL top
                // face. Same helper as the other anchor lanes, so a stored placement height is
                // seen here too; floors at -0.5.
                return anchoredCellDy(world, cursor, depth);
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isThinTopLayer(cur)) {
                return 0.0;
            }
            // Natural-terrain stop (see hasSlabInColumn): never walk through a solid full cube to
            // a slab deeper in the column — that lowered natural terrain over Terrain Slabs -> holes.
            if (cur.isOpaqueFullCube()) {
                return 0.0;
            }
            // BUG B, magnitude twin of hasSlabInColumn's standing-object lane. MUST move with it or
            // the boolean and the value disagree (shared-predicate law): hasSlabInColumn would say
            // "lowered" and this would answer 0.0, collapsing the caller onto its -0.5 default.
            double standingObjectDy = loweredStandingObjectDy(world, cursor, cur);
            if (Double.isFinite(standingObjectDy)) {
                return Math.max(standingObjectDy, minResolvedDy());
            }
            cursor = cursor.down();
        }
        return 0.0;
    }

    /**
     * Region-boundary-safe read: beyond a chunk-render region's bounds the lookup ENDS, answering
     * air — the same bounded-lookup remedy the renderer-boundary crash fix established, held in
     * ONE accessor so no walk or probe can crash a mesh worker by wandering past the region edge
     * (a Terrain-slab-dense chunk makes the side-contagion walk reach it routinely). Real worlds
     * never throw here, so gameplay reads are unchanged, and a non-region out-of-bounds still
     * rethrows so genuine defects surface.
     */
    private static BlockState getBlockStateOrAir(BlockView world, BlockPos pos) {
        BlockState state = getBlockStateOrNull(world, pos);
        return state == null ? Blocks.AIR.getDefaultState() : state;
    }

    private static BlockState getBlockStateOrNull(BlockView world, BlockPos pos) {
        try {
            return world.getBlockState(pos);
        } catch (IndexOutOfBoundsException e) {
            if (isChunkRendererRegion(world)) {
                return null;
            }
            throw e;
        }
    }

    private static boolean isChunkRendererRegion(BlockView world) {
        return world != null && CHUNK_RENDERER_REGION_DETECTOR.test(world);
    }
}
