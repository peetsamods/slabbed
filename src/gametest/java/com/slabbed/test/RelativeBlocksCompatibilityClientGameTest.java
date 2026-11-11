package com.slabbed.test;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.support.TestConnections;
import com.lukeolafp.relativeblocks.blocks.BlockClusterBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
public final class RelativeBlocksCompatibilityClientGameTest implements FabricClientGameTest {
 public void runTest(ClientGameTestContext context) {
  try (var world=context.worldBuilder().setUseConsistentSettings(true).create()) {
   TestConnections.waitForChunksDownload(world);
   BlockPos pos=world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().above().immutable());
   var referenceMeshes=new java.util.HashMap<String,java.util.List<String>>();
   var referenceOutlines=new java.util.HashMap<Integer,net.minecraft.world.phys.shapes.VoxelShape>();
   for (double dy:new double[]{0,-0.5}) {
    Vec3 feet=new Vec3(pos.getX()+0.5,pos.getY()+dy,pos.getZ()+2);
    world.getServer().runOnServer(s -> {
     var l=s.overworld();var p=s.getPlayerList().getPlayers().getFirst();
     for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) {
      l.setBlock(pos.offset(x,-1,z),(dy==0?Blocks.STONE:Blocks.STONE_SLAB).defaultBlockState(),Block.UPDATE_ALL);
      for(int y=0;y<=4;y++) l.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
     }
     p.setGameMode(GameType.CREATIVE);p.teleportTo(feet.x,feet.y,feet.z);p.setYRot(180);p.setXRot(45);
    });
    context.waitFor(c -> c.player!=null && c.player.position().distanceToSqr(feet)<0.01,200);
    context.runOnClient(c -> {c.player.setYRot(180);c.player.setXRot(45);});
    use(context,world,Items.CANDLE,new BlockHitResult(new Vec3(pos.getX()+0.5,pos.getY()+dy,pos.getZ()+0.5),Direction.UP,pos.below(),false));
    world.getServer().runOnServer(s -> snapshot("first-server",s.overworld(),pos));
    context.runOnClient(c -> snapshot("first-client",c.level,pos));
    use(context,world,Items.FLOWER_POT,new BlockHitResult(new Vec3(pos.getX()+0.75,pos.getY()+dy,pos.getZ()+0.5),Direction.UP,pos.below(),false));
    world.getServer().runOnServer(s -> snapshot("second-server",s.overworld(),pos));
    world.getServer().runOnServer(server -> {
     var l=server.overworld();l.setBlock(pos.east(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);l.setBlock(pos.east(),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
     if(SlabAnchorAttachment.storedPlacementDy(l,pos)!=dy) throw new AssertionError("RED neighbor mutation changed cluster dy");
    });
    context.waitTicks(3);
    context.runOnClient(c -> {
     snapshot("second-client",c.level,pos);
     var normalizedMesh=new java.util.ArrayList<String>();
     float[] bounds={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,0};
     var emitter=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {bounds[2]++;for(int v=0;v<4;v++){bounds[0]=Math.min(bounds[0],q.y(v));bounds[1]=Math.max(bounds[1],q.y(v));normalizedMesh.add(q.x(v)+","+(float)(q.y(v)-dy)+","+q.z(v));}});
     var model=c.getModelManager().getBlockStateModelSet().get(c.level.getBlockState(pos));
     model.emitQuads(emitter,c.level,pos,c.level.getBlockState(pos),net.minecraft.util.RandomSource.create(0),d -> false);
     java.util.Collections.sort(normalizedMesh);if(dy==0)referenceMeshes.put("cluster",normalizedMesh);else if(!normalizedMesh.equals(referenceMeshes.get("cluster")))throw new AssertionError("RED complete cluster mesh translation");
     var contextual=c.level.getBlockState(pos).getCollisionShape(c.level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(c.player));
     Slabbed.LOGGER.info("RPB_GEOMETRY case={} expectedLocalBottom={} modelMin={} modelMax={} quads={} modelClass={} contextualCollision={} outline={}","relative-cluster",dy,bounds[0],bounds[1],bounds[2],model.getClass().getName(),contextual.toAabbs(),c.level.getBlockState(pos).getShape(c.level,pos).toAabbs());
     if(bounds[2]==0 || Math.abs(bounds[0]-dy)>0.0001) throw new AssertionError("RED model contact: expected="+dy+" actual="+bounds[0]);
     if(Math.abs(c.level.getBlockState(pos).getShape(c.level,pos).min(Direction.Axis.Y)-dy)>0.0001) throw new AssertionError("RED outline contact");
     if(Math.abs(contextual.min(Direction.Axis.Y)-dy)>0.0001) throw new AssertionError("RED collision contact");
     var be=(BlockClusterBlockEntity)c.level.getBlockEntity(pos);
     for(boolean collisionFirst:new boolean[]{true,false}) {
      be.forgetShapes();
      var st=c.level.getBlockState(pos);
      if(collisionFirst) st.getCollisionShape(c.level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(c.player)); else st.getShape(c.level,pos);
      var coldOutline=st.getShape(c.level,pos);var coldCollision=st.getCollisionShape(c.level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(c.player));
      if(Math.abs(coldOutline.min(Direction.Axis.Y)-dy)>0.0001 || Math.abs(coldCollision.min(Direction.Axis.Y)-dy)>0.0001) throw new AssertionError("RED cold/warm query order="+collisionFirst+" outline="+coldOutline+" collision="+coldCollision);
      for(int i=0;i<be.capacity();i++) if(be.getCell(i)!=null) {
       var member=com.lukeolafp.relativeblocks.blocks.BlockClusterBlock.cellShape(c.level,pos,be,i,false);
       if(Math.abs(member.min(Direction.Axis.Y)-dy)>0.0001) throw new AssertionError("RED direct member wireframe shape "+i);
       var b=member.bounds();double x=pos.getX()+(b.minX+b.maxX)/2,z=pos.getZ()+(b.minZ+b.maxZ)/2;
       var from=new Vec3(x,pos.getY()+dy+1,z);var to=new Vec3(x,pos.getY()+dy-0.1,z);
       var hit=c.level.clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,c.player));
       int picked=com.lukeolafp.relativeblocks.blocks.RelativePlacement.nearestCell(be,pos,hit.getLocation());
       if(!hit.getBlockPos().equals(pos)||picked!=i) throw new AssertionError("RED member ray/pick expected="+i+" picked="+picked+" owner="+hit.getBlockPos());
       Slabbed.LOGGER.info("RPB_MEMBER case={} collisionFirst={} cell={} outlineBottom={} picked={} rayOwner={} contactError={}","relative-cluster",collisionFirst,i,member.min(Direction.Axis.Y),picked,hit.getBlockPos(),member.min(Direction.Axis.Y)-dy);
      }
     }
    });
    boolean cluster=world.getServer().computeOnServer(s -> s.overworld().getBlockEntity(pos) instanceof BlockClusterBlockEntity be && be.memberCount()==2);
    if(!cluster) throw new AssertionError("UNCLASSIFIED_FAILURE: control sequence did not form two-member cluster dy="+dy);
   }
   for(int layout:new int[]{1,2}) for(double dy:new double[]{0,-0.5}) {
    world.getServer().runOnServer(server -> {
     var l=server.overworld();
     l.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
     l.setBlock(pos,com.lukeolafp.relativeblocks.init.BlockInit.BLOCK_CLUSTER.defaultBlockState(),Block.UPDATE_CLIENTS);
     var be=(BlockClusterBlockEntity)l.getBlockEntity(pos);be.setLayout(layout);be.setCell(0,Blocks.OAK_FENCE.defaultBlockState());be.setCell(1,Blocks.FLOWER_POT.defaultBlockState());be.setChanged();
     SlabAnchorAttachment.writePlacementDy(l,pos,dy);l.sendBlockUpdated(pos,l.getBlockState(pos),l.getBlockState(pos),Block.UPDATE_CLIENTS);
    });
    context.waitFor(c -> c.level.getBlockEntity(pos) instanceof BlockClusterBlockEntity be && be.getLayout()==layout && be.memberCount()==2 && SlabAnchorAttachment.storedPlacementDy(c.level,pos)==dy,200);
    context.waitTicks(5);
    context.runOnClient(c -> {
     var st=c.level.getBlockState(pos);var be=(BlockClusterBlockEntity)c.level.getBlockEntity(pos);be.forgetShapes();
     if(!com.lukeolafp.relativeblocks.blocks.BarrierLayout.lays((BlockClusterBlockEntity.RenderData)be.getRenderData(),be.getCell(0))) throw new AssertionError("invalid BarrierLayout control");
     var outline=st.getShape(c.level,pos);
     if(dy==0) referenceOutlines.put(layout,outline); else if(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(outline.move(0,-dy,0),referenceOutlines.get(layout),net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME))throw new AssertionError("RED complete mixed barrier outline layout="+layout);
     if(Math.abs(outline.min(Direction.Axis.Y)-dy)>0.0001) throw new AssertionError("RED mixed barrier outline layout="+layout+" dy="+dy+" shape="+outline);
     var mesh=new java.util.ArrayList<String>();
     float[] b={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,0};
     var emitter=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {b[2]++;for(int v=0;v<4;v++){b[0]=Math.min(b[0],q.y(v));b[1]=Math.max(b[1],q.y(v));mesh.add(q.x(v)+","+(float)(q.y(v)-dy)+","+q.z(v));}});
     c.getModelManager().getBlockStateModelSet().get(st).emitQuads(emitter,c.level,pos,st,net.minecraft.util.RandomSource.create(0),d->false);
     java.util.Collections.sort(mesh);if(dy==0)referenceMeshes.put("barrier"+layout,mesh);else if(!mesh.equals(referenceMeshes.get("barrier"+layout)))throw new AssertionError("RED complete mixed barrier mesh layout="+layout);
     if(b[2]==0 || Math.abs(b[0]-dy)>0.0001) throw new AssertionError("RED mixed barrier model layout="+layout+" dy="+dy+" actual="+b[0]);
     Slabbed.LOGGER.info("RPB_BARRIER_CONTROL layout={} dy={} outlineMin={} modelMin={} quads={}",layout,dy,outline.min(Direction.Axis.Y),b[0],b[2]);
    });
   }
  }
 }
 static void use(ClientGameTestContext context,TestSingleplayerContext world,Item item,BlockHitResult hit) {
  world.getServer().runOnServer(s -> {var p=s.getPlayerList().getPlayers().getFirst();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));p.inventoryMenu.broadcastChanges();});
  context.waitFor(c -> c.player.getMainHandItem().is(item),100);
  context.runOnClient(c -> {
   Slabbed.LOGGER.info("RPB_USE case={} item={} owner={} hit={} state={} dy={}","relative-cluster",item,hit.getBlockPos(),hit.getLocation(),c.level.getBlockState(hit.getBlockPos()),SlabAnchorAttachment.storedPlacementDy(c.level,hit.getBlockPos()));
   var result=c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,hit);
   Slabbed.LOGGER.info("RPB_USE_RESULT case={} result={}","relative-cluster",result);
  });context.waitTicks(10);
 }
 static void snapshot(String label,Level l,BlockPos p) {
  var state=l.getBlockState(p);var outline=state.getShape(l,p);var collision=state.getCollisionShape(l,p);
  Slabbed.LOGGER.info("RPB_SNAPSHOT case={} label={} state={} dy={} outline={} collision={} model=NOT_RUN raycast=NOT_RUN","relative-cluster",label,state,SlabAnchorAttachment.storedPlacementDy(l,p),outline.toAabbs(),collision.toAabbs());
  if(l.getBlockEntity(p) instanceof BlockClusterBlockEntity be) {
   for(int i=0;i<be.capacity();i++) Slabbed.LOGGER.info("RPB_CELL case={} label={} layout={} index={} state={} offsetY={}","relative-cluster",label,be.getLayout(),i,be.getCell(i),be.cellOffsetY(i));
  }
 }
}
