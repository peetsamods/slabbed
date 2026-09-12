package com.slabbed.placement;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.CollisionView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Additional collision owners whose stored bodies extend below their logical cells. */
public final class PlacementCollisionShapes {
    private PlacementCollisionShapes() { }

    public record Contact(BlockPos owner, VoxelShape shape) { }

    public static List<Contact> above(CollisionView view, ShapeContext context, Box box, boolean suffocatingOnly) {
        if (!(view instanceof World world) || box == null || SlabSupport.isRawShapeProbeActive()) {
            return List.of();
        }
        int minX = MathHelper.floor(box.minX - 1.0e-7) - 1;
        int maxX = MathHelper.floor(box.maxX + 1.0e-7) + 1;
        int minZ = MathHelper.floor(box.minZ - 1.0e-7) - 1;
        int maxZ = MathHelper.floor(box.maxZ + 1.0e-7) + 1;
        int minY = MathHelper.floor(box.minY - 1.0e-7) + 1;
        int maxY = MathHelper.floor(box.maxY + 1.0e-7) + (int) Math.ceil(-SlabSupport.MIN_PLACEMENT_DY);
        List<Contact> contacts = new ArrayList<>();
        VoxelShape query = VoxelShapes.cuboid(box);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // The same non-creating chunk read used by the native collision iterator.
                if (!(view.getChunkAsView(x >> 4, z >> 4) instanceof WorldChunk chunk)) continue;
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    double dy = world.isClient()
                            ? SlabPlacementDyAttachment.storedDy(world, cursor)
                            : SlabPlacementDyAttachment.lookup(chunk, cursor);
                    if (!Double.isFinite(dy) || dy >= -1.0e-6) continue;
                    var state = chunk.getBlockState(cursor);
                    if (state.isAir() || suffocatingOnly && !state.shouldSuffocate(view, cursor)) continue;
                    VoxelShape shape = context.getCollisionShape(state, view, cursor).offset(x, y, z);
                    if (!shape.isEmpty() && VoxelShapes.matchesAnywhere(query, shape, BooleanBiFunction.AND)) {
                        contacts.add(new Contact(cursor.toImmutable(), shape));
                    }
                }
            }
        }
        return contacts;
    }
}
