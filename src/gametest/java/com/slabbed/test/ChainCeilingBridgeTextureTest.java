package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.ChainBridgeTextureVariant;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Regression coverage for the chain-ceiling-bridge TEXTURE fix (2026-07-06) plus a lock-in of the
 * (investigated, intentional) bridged-chain dy contract.
 *
 * <p><b>The live-reported bug.</b> A {@code waxed_copper_chain} (a {@link ChainBlock}) placed directly
 * below a lowered/anchored {@code oak_slab[type=double]} ceiling rendered as a PLAIN iron chain, and
 * breaking the supporting slab flipped it back to the correct copper texture — even though the block
 * itself was always {@code waxed_copper_chain}. Root cause: a Y-axis chain under a slab ceiling is
 * rendered with an extended "bridge" model emitted INSTEAD of the block's own model
 * ({@code ChainCeilingGeometry}); that bridge model hardcoded the {@code iron_chain} texture, so every
 * non-iron chain rendered as iron while bridged and snapped back to its real texture once the slab
 * was removed and the bridge lane deactivated. Fixed by baking one bridge model per chain texture and
 * selecting it from the chain's registry id ({@link ChainBridgeTextureVariant}).
 *
 * <p><b>The dy is NOT a bug (investigated).</b> The live recorder also showed the bridged chain at
 * dy=0.0 (flush) rather than inheriting the support's -0.5. That grid-height value is DELIBERATE — a
 * bridged chain stays at grid height and the extended model spans the gap to the ceiling
 * (chandelier-style ceiling-mount geometry), a design the suite already pins as the "CONTROL: iron
 * chain under a lowered support keeps its own grid-height chain behavior" case in
 * {@link Slabbed2612RestingDyTest}, and which the maintainer has previously ruled intentional. So the dy is
 * left unchanged; {@link #waxedCopperChainUnderLoweredDoubleStaysBridgedGridHeight} locks that
 * contract in so a future accidental change is caught.
 */
public final class ChainCeilingBridgeTextureTest {

    private static final double EPS = 1.0e-6;

    /**
     * Route discriminator for the lowered-TOP ghost bridge: a chain whose already-frozen model dy is
     * lowered must use its normal 16px model/selection, while the flush dy=0 TOP control keeps the
     * deliberate 24px bridge. This calls the shared server-loadable policy directly so its RED is a
     * missing or incorrect production route, not a test-side approximation.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredTopChainFrozenDySelectsNormalRouteWhileFlushTopSelectsBridge(GameTestHelper helper) {
        BlockState chain = Blocks.IRON_CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        BlockState top = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState doubleSlab = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.DOUBLE);

        boolean loweredRoute = SlabSupport.usesCeilingBridgeGeometry(chain, top, -1.5d);
        boolean flushRoute = SlabSupport.usesCeilingBridgeGeometry(chain, top, 0.0d);
        boolean loweredDoubleRoute = SlabSupport.usesCeilingBridgeGeometry(chain, doubleSlab, -1.5d);
        boolean flushDoubleRoute = SlabSupport.usesCeilingBridgeGeometry(chain, doubleSlab, 0.0d);

        if (loweredRoute) {
            throw helper.assertionException(BlockPos.ZERO,
                    "a lowered TOP chain at frozen dy=-1.5 must use the normal shifted 16px route, "
                            + "not the 24px ceiling bridge");
        }
        if (!flushRoute) {
            throw helper.assertionException(BlockPos.ZERO,
                    "the flush TOP control at frozen dy=0 must retain the 24px ceiling bridge");
        }
        if (loweredDoubleRoute) {
            throw helper.assertionException(BlockPos.ZERO,
                    "changing the support from TOP to DOUBLE must not enable the bridge for an "
                            + "already-lowered chain at frozen dy=-1.5");
        }
        if (!flushDoubleRoute) {
            throw helper.assertionException(BlockPos.ZERO,
                    "the existing DOUBLE control at frozen dy=0 must retain its bridge route");
        }
        helper.succeed();
    }

    /**
     * RED anchor for the fix: the pure selector maps every chain block to the bridge texture variant
     * matching its OWN texture (waxed variants share their unwaxed texture). Before the fix there was
     * a single iron bridge; mutating {@link ChainBridgeTextureVariant#forBlock} to always return IRON
     * turns this RED for every non-iron chain.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainBridgeTextureVariantMapsEachChainToItsOwnTexture(GameTestHelper helper) {
        assertVariant(helper, "iron_chain", ChainBridgeTextureVariant.IRON);

        assertVariant(helper, "copper_chain", ChainBridgeTextureVariant.COPPER);
        assertVariant(helper, "waxed_copper_chain", ChainBridgeTextureVariant.COPPER);
        assertVariant(helper, "exposed_copper_chain", ChainBridgeTextureVariant.EXPOSED_COPPER);
        assertVariant(helper, "waxed_exposed_copper_chain", ChainBridgeTextureVariant.EXPOSED_COPPER);
        assertVariant(helper, "weathered_copper_chain", ChainBridgeTextureVariant.WEATHERED_COPPER);
        assertVariant(helper, "waxed_weathered_copper_chain", ChainBridgeTextureVariant.WEATHERED_COPPER);
        assertVariant(helper, "oxidized_copper_chain", ChainBridgeTextureVariant.OXIDIZED_COPPER);
        assertVariant(helper, "waxed_oxidized_copper_chain", ChainBridgeTextureVariant.OXIDIZED_COPPER);

        helper.succeed();
    }

    private static void assertVariant(GameTestHelper helper, String path, ChainBridgeTextureVariant expected) {
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path));
        if (!(block instanceof ChainBlock)) {
            throw helper.assertionException(BlockPos.ZERO,
                    "test fixture: minecraft:" + path + " is not a ChainBlock (got " + block + ")");
        }
        ChainBridgeTextureVariant actual = ChainBridgeTextureVariant.forBlock(block.defaultBlockState());
        if (actual != expected) {
            throw helper.assertionException(BlockPos.ZERO,
                    "minecraft:" + path + " must bridge with the " + expected + " texture variant, got " + actual
                            + " — a chain must never render its ceiling bridge with another chain's texture");
        }
    }

    /**
     * The shipped per-variant bridge model files actually reference the texture the selector promises.
     * This is the closest HEADLESS proof that a bridged copper chain is copper-textured (the on-screen
     * render is only observable live, per lesson S7): it catches any drift between the enum and the
     * JSON, e.g. a copper variant left pointing at {@code iron_chain}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainBridgeModelFilesReferenceTheExpectedTexture(GameTestHelper helper) {
        for (ChainBridgeTextureVariant variant : ChainBridgeTextureVariant.values()) {
            String resource = "/assets/slabbed/models/block/" + variant.modelPath() + ".json";
            String json = readClasspath(helper, resource);
            if (!json.contains("\"" + variant.expectedTexture() + "\"")) {
                throw helper.assertionException(BlockPos.ZERO,
                        variant + " bridge model (" + resource + ") must reference texture "
                                + variant.expectedTexture() + " but did not — the bridged chain would render "
                                + "with the wrong texture");
            }
        }
        // Belt-and-braces: the iron model must NOT be the only one referencing iron (i.e. the copper
        // models must have actually been retextured off iron_chain).
        String copperJson = readClasspath(helper, "/assets/slabbed/models/block/" + ChainBridgeTextureVariant.COPPER.modelPath() + ".json");
        if (copperJson.contains("iron_chain")) {
            throw helper.assertionException(BlockPos.ZERO,
                    "the COPPER bridge model still references iron_chain — copper chains would render as iron");
        }
        helper.succeed();
    }

    private static String readClasspath(GameTestHelper helper, String resource) {
        try (InputStream in = ChainCeilingBridgeTextureTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw helper.assertionException(BlockPos.ZERO,
                        "missing bridge model resource on classpath: " + resource
                                + " — every registered chain-bridge variant must ship a model file");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw helper.assertionException(BlockPos.ZERO, "failed reading " + resource + ": " + e);
        }
    }

    /**
     * dy-contract lock-in (investigated, intentional). The live recorder's exact scene — a
     * waxed_copper_chain placed via the REAL useOn path directly below a lowered/anchored DOUBLE oak
     * slab ceiling — the chain reads grid-height dy=0.0 and IS recognised as a bridged vertical chain
     * directly under a ceiling support. That 0.0 is the deliberate bridge geometry, NOT a lowering
     * bug; this test fails if the dy is ever changed to inherit the support's -0.5 (which would break
     * the extended-bridge model contract) or if the bridge lane stops recognising the scene.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waxedCopperChainUnderLoweredDoubleStaysBridgedGridHeight(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();

        // Anchored lowered DOUBLE oak slab as the ceiling support (recorder's exact config).
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos doubleAbs = helper.absolutePos(new BlockPos(3, 3, 2));   // anchored lowered DOUBLE (ceiling)
        BlockPos chainAbs = helper.absolutePos(new BlockPos(3, 2, 2));    // chain cell directly below it
        BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 2));       // solid the chain places against

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));

        BlockState doubleState = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        w.setBlock(doubleAbs, doubleState, 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, doubleAbs, w.getBlockState(doubleAbs));
        double supportDy = SlabSupport.getYOffset(w, doubleAbs, w.getBlockState(doubleAbs));
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "SETUP: the DOUBLE ceiling support must be anchored lowered to -0.5, got " + supportDy);
        }
        w.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);

        // Real useOn placement of a waxed_copper_chain (recorder's exact item).
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(chainAbs.getX() + 0.5, chainAbs.getY() + 1, chainAbs.getZ() + 0.5);
        Item chainItem = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("waxed_copper_chain"));
        ItemStack stack = new ItemStack(chainItem);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, floor, false)));

        BlockState chainState = w.getBlockState(chainAbs);
        if (!(chainState.getBlock() instanceof ChainBlock)
                || !chainState.hasProperty(BlockStateProperties.AXIS)
                || chainState.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
            throw helper.assertionException(new BlockPos(3, 2, 2),
                    "SETUP: the placed block must be a Y-axis chain, got " + chainState);
        }

        // It must be recognised as a bridged vertical chain directly under the ceiling support...
        if (!SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(w, chainAbs, chainState)) {
            throw helper.assertionException(new BlockPos(3, 2, 2),
                    "the chain directly under a lowered DOUBLE ceiling must qualify for the ceiling bridge");
        }
        // ...and its own selector must pick the copper bridge (so it renders copper, not iron).
        if (ChainBridgeTextureVariant.forBlock(chainState) != ChainBridgeTextureVariant.COPPER) {
            throw helper.assertionException(new BlockPos(3, 2, 2),
                    "the bridged waxed_copper_chain must select the COPPER bridge texture, got "
                            + ChainBridgeTextureVariant.forBlock(chainState));
        }
        // ...at the deliberate grid-height dy=0.0 (the bridge geometry spans the gap; NOT a -0.5 follow).
        double chainDy = SlabSupport.getYOffset(w, chainAbs, chainState);
        if (Math.abs(chainDy) > EPS) {
            throw helper.assertionException(new BlockPos(3, 2, 2),
                    "a bridged vertical chain must stay at grid-height dy=0.0 (intentional ceiling-mount "
                            + "bridge geometry), got " + chainDy);
        }
        if (!SlabSupport.usesCeilingBridgeGeometry(chainState, doubleState, chainDy)) {
            throw helper.assertionException(new BlockPos(3, 2, 2),
                    "the existing DOUBLE ceiling control must retain its grid-height bridge route");
        }
        helper.succeed();
    }
}
