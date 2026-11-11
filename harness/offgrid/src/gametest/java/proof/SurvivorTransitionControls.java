package proof;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Synthetic transition controls complement the real player interaction cases (LAW.md). */
final class SurvivorTransitionControls {
    private static final BlockState OLD = Blocks.STONE.defaultBlockState();
    private static final BlockState NEXT = Blocks.OAK_SLAB.defaultBlockState();
    private static Class<?> store() throws Exception { return Class.forName("com.slabbed.anchor.SlabAnchorAttachment"); }
    private static AutoCloseable open(Level l, BlockPos p) throws Exception {
        return open(l,p,NEXT);
    }
    private static AutoCloseable open(Level l, BlockPos p, BlockState next) throws Exception {
        return (AutoCloseable)Class.forName("com.slabbed.anchor.OffgridSurvivorTransition")
                .getMethod("open",Level.class,BlockPos.class,BlockState.class,BlockState.class).invoke(null,l,p,OLD,next);
    }
    static Object raw(Level l, BlockPos p) {
        try { return store().getMethod("rawPlacementDyFact",net.minecraft.world.level.BlockGetter.class,BlockPos.class).invoke(null,l,p); }
        catch(Exception e) { throw new AssertionError(e); }
    }
    private static void write(Level l, BlockPos p, double d) throws Exception {
        store().getMethod("writePlacementDy",Level.class,BlockPos.class,double.class).invoke(null,l,p,d);
    }
    private static void reset(Level l,BlockPos p) {l.setBlock(p,Blocks.AIR.defaultBlockState(),3);l.setBlock(p,OLD,3);}
    private static void absent(Level l,BlockPos p,String name) throws Exception {
        if((boolean)raw(l,p).getClass().getMethod("present").invoke(raw(l,p)))throw new AssertionError(name+" retained fact");
    }
    @SuppressWarnings("unchecked")
    static void run(Level l,BlockPos p) {
        try {
            var supports=Class.forName("com.slabbed.mixin.OffgridMixinConfigPlugin").getMethod("supports",String.class,String.class);
            if(!(boolean)supports.invoke(null,"26.3","1.0.6+26.3")||(boolean)supports.invoke(null,"26.2","1.0.6+26.3")||(boolean)supports.invoke(null,"26.3","1.0.4+26.3")||(boolean)supports.invoke(null,"26.3","1.0.7+26.3"))throw new AssertionError("compatibility gate");
            String[] names={"ANCHOR_TYPE","FROZEN_FLAT_TYPE","COMPOUND_FULL_BLOCK_ANCHOR_TYPE","COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE","COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE","COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE","COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE","LOWERED_SLAB_CARRIER_TYPE"};
            for(double d:new double[]{-0.5,0.0,-0.0}) {
                reset(l,p);write(l,p,d);Object before=raw(l,p);var chunk=l.getChunkAt(p);
                for(String name:names){var type=(AttachmentType<LongOpenHashSet>)store().getField(name).get(null);var existing=chunk.getAttached(type);var updated=existing==null?new LongOpenHashSet():new LongOpenHashSet(existing);updated.add(p.asLong());chunk.setAttached(type,updated);}
                try(var scope=open(l,p)) {
                    if(scope==null)throw new AssertionError("finite scope missing");
                    if(!l.setBlock(p,NEXT,3))throw new AssertionError("control replacement failed");
                    if(!before.equals(raw(l,p)))throw new AssertionError("raw bits changed "+d);
                    for(String name:names) {var set=chunk.getAttached((AttachmentType<LongOpenHashSet>)store().getField(name).get(null));if(set!=null&&set.contains(p.asLong()))throw new AssertionError("marker retained "+name);}
                    l.setBlock(p,Blocks.AIR.defaultBlockState(),3);l.setBlock(p,OLD,3);l.setBlock(p,NEXT,3);
                    absent(l,p,"consumed ABA");
                }
            }
            for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
                reset(l,p);var type=(AttachmentType<it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap>)store().getField("PLACEMENT_DY_TYPE").get(null);var existing=l.getChunkAt(p).getAttached(type);var invalidMap=existing==null?new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap():new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap(existing);invalidMap.put(p.asLong(),invalid);
                l.getChunkAt(p).setAttached(type,invalidMap);
                try(var scope=open(l,p)){if(scope!=null)throw new AssertionError("nonfinite fact admitted");}
            }
            reset(l,p);write(l,p,-0.5);Object wetBefore=raw(l,p);var wet=NEXT.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true);
            try(var scope=open(l,p,wet)){if(scope==null)throw new AssertionError("waterlogged survivor rejected");l.setBlock(p,wet,3);if(!wetBefore.equals(raw(l,p)))throw new AssertionError("waterlogged seat lost");}
            reset(l,p);try(var scope=open(l,p)){if(scope!=null)throw new AssertionError("absent scope admitted");l.setBlock(p,NEXT,3);}absent(l,p,"absent");
            reset(l,p);write(l,p,-0.5);try(var scope=open(l,p)){write(l,p,-0.25);l.setBlock(p,NEXT,3);}absent(l,p,"changed fact");
            reset(l,p);write(l,p,-0.5);Object before=raw(l,p);try(var scope=open(l,p)){if(l.setBlock(p,OLD,3))throw new AssertionError("same-state failed-write control unexpectedly wrote");}if(!before.equals(raw(l,p)))throw new AssertionError("failed write changed fact");l.setBlock(p,NEXT,3);absent(l,p,"closed failed scope");
            reset(l,p);write(l,p,-0.5);try(var scope=open(l,p)){throw new IllegalArgumentException("controlled throw");}catch(IllegalArgumentException expected){}l.setBlock(p,NEXT,3);absent(l,p,"throw cleanup");
            BlockPos q=p.east(2);reset(l,p);reset(l,q);write(l,p,-0.5);write(l,q,0.0);Object a=raw(l,p),b=raw(l,q);
            try(var outer=open(l,p)){try(var inner=open(l,q)){l.setBlock(q,NEXT,3);if(!b.equals(raw(l,q)))throw new AssertionError("inner scope");}l.setBlock(p,NEXT,3);if(!a.equals(raw(l,p)))throw new AssertionError("outer scope not restored");}
            l.setBlock(p,Blocks.AIR.defaultBlockState(),3);absent(l,p,"ordinary remove");
            reset(l,p);write(l,p,-0.5);try(var scope=open(l,p)){l.setBlock(p,Blocks.BIRCH_SLAB.defaultBlockState(),3);absent(l,p,"wrong survivor");}
            System.out.println("OFFGRID_PROOF PASS raw-zero/absent/changed/failed/throw/nested/marker/ABA controls");
        } catch(Exception e) {throw new AssertionError(e);}
    }
}
