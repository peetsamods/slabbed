package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.test.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Fence ceiling connections preserve the real placed seat (LAW.md). */
public final class FenceCeilingConnectionTest {
    private static final BlockPos OWNER = new BlockPos(2, 3, 2);
    private static final double EPS = 1.0e-6d;

    private static void withFrozen(Runnable body) {
        boolean before=SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED=true;
        try { body.run(); } finally { SlabAnchorAttachment.FROZEN_DY_ENABLED=before; }
    }
    private static void store(ServerWorld w, BlockPos p, double dy) {
        SlabAnchorAttachment.writePlacementDyBatch(w,java.util.Map.of(p,Double.doubleToRawLongBits(dy)));
    }
    private static void topSlab(ServerWorld w, BlockPos owner, double dy) {
        w.setBlockState(owner, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), 2);
        store(w, owner, dy);
    }
    private static void place(TestContext h,BlockPos owner,Direction face,double y) {
        var stack=new ItemStack(Items.OAK_FENCE);
        var player=h.createMockSurvivalPlayer();
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,stack);
        stack.useOnBlock(new net.minecraft.item.ItemUsageContext(player,net.minecraft.util.Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(new Vec3d(owner.getX()+0.5d,y,owner.getZ()+0.5d),face,owner,false)));
    }
    private static void seat(TestContext h, BlockPos post, double expected) {
        var w=h.getWorld();
        double saved=SlabAnchorAttachment.storedPlacementDy(w,post);
        if (!w.getBlockState(post).isOf(Blocks.OAK_FENCE) || Math.abs(saved-expected)>EPS
                || !Double.isFinite(saved) || Math.abs(SlabSupport.getYOffset(w,post,w.getBlockState(post))-expected)>EPS) {
            throw new net.minecraft.test.GameTestException("placed fence must keep its saved seat "+expected+", got "+saved);
        }
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void placedPostConnectsWhenTheCeilingIsAdded(TestContext h) {
        withFrozen(() -> {
        var w=h.getWorld();var owner=h.getAbsolutePos(OWNER);var post=owner.down();var floor=post.down();
        w.setBlockState(floor,Blocks.STONE.getDefaultState(),2);
        place(h,floor,Direction.UP,floor.getY()+1.0d);
        seat(h,post,0.0d);
        topSlab(w,owner,0.0d);
        var shape=w.getBlockState(post).getOutlineShape(w,post);
        if (Math.abs(post.getY()+shape.getMax(Direction.Axis.Y)-(owner.getY()+0.5d))>EPS) {
            throw new net.minecraft.test.GameTestException("the existing post must meet the top-slab underside");
        }
        Vec3d start=new Vec3d(post.getX()-1.0d,post.getY()+1.25d,post.getZ()+0.5d);
        var hit=com.slabbed.util.SlabbedOffsetRaycast.raycast(w,start,start.add(2.0d,0.0d,0.0d),
                net.minecraft.block.ShapeContext.absent());
        if (hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK || !hit.getBlockPos().equals(post)) {
            throw new net.minecraft.test.GameTestException("the extended post must be targetable");
        }
        w.setBlockState(owner,Blocks.AIR.getDefaultState(),2);
        seat(h,post,0.0d);
        if (Math.abs(w.getBlockState(post).getOutlineShape(w,post).getMax(Direction.Axis.Y)-1.0d)>EPS) {
            throw new net.minecraft.test.GameTestException("removing the ceiling restores ordinary post geometry");
        }
        h.complete();
            });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void fenceCanBePlacedUnderAFlushTopSlab(TestContext h) {
        withFrozen(() -> {
        var w=h.getWorld();var owner=h.getAbsolutePos(OWNER);
        topSlab(w,owner,0.0d);
        place(h,owner,Direction.DOWN,owner.getY()+0.5d);
        seat(h,owner.down(),0.0d);
        h.complete();
            });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void aFullCellOpeningDoesNotStretchThePost(TestContext h) {
        withFrozen(() -> {
        var w=h.getWorld();var owner=h.getAbsolutePos(OWNER);var post=owner.down();
        topSlab(w,owner,0.0d);w.setBlockState(post,Blocks.OAK_FENCE.getDefaultState(),2);store(w,post,-0.5d);
        if (Math.abs(w.getBlockState(post).getOutlineShape(w,post).getMax(Direction.Axis.Y)-0.5d)>EPS) {
            throw new net.minecraft.test.GameTestException("a full-cell opening must retain the ordinary seated post");
        }
        seat(h,post,-0.5d);h.complete();
            });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void fittingPostUnderALoweredTopSlabKeepsItsSeat(TestContext h) {
        withFrozen(() -> {
        var w=h.getWorld();var owner=h.getAbsolutePos(OWNER);var post=owner.down();
        w.setBlockState(post.down(),Blocks.OAK_FENCE.getDefaultState(),2);store(w,post.down(),0.0d);
        topSlab(w,owner,-0.5d);
        place(h,owner,Direction.DOWN,owner.getY());
        seat(h,post,0.0d);
        if (Math.abs(post.getY()+w.getBlockState(post).getOutlineShape(w,post).getMax(Direction.Axis.Y)-owner.getY())>EPS) {
            throw new net.minecraft.test.GameTestException("fitting post and slab must meet without overlap");
        }
        w.setBlockState(owner,Blocks.AIR.getDefaultState(),2);seat(h,post,0.0d);h.complete();
            });
    }
}
