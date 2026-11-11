package com.slabbed.test;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.support.TestConnections;
import dev.dyslexic.cinematic.client.TableStation;
import dev.dyslexic.cinematic.client.Reach;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
public final class CinematicTableClientGameTest implements FabricClientGameTest {
 public void runTest(ClientGameTestContext context) {
  try (var world=context.worldBuilder().setUseConsistentSettings(true).create()) {
   TestConnections.waitForChunksDownload(world);
   BlockPos pos=world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().above().immutable());
   for (double dy:new double[]{0,-0.5}) {
    Vec3 feet=new Vec3(pos.getX()+0.5,pos.getY()+dy,pos.getZ()+1.36);
    world.getServer().runOnServer(s -> {
     var level=s.overworld(); var player=s.getPlayerList().getPlayers().getFirst();
     for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) {
      level.setBlock(pos.offset(x,-1,z), (dy==0?Blocks.STONE:Blocks.STONE_SLAB).defaultBlockState(),Block.UPDATE_ALL);
      for(int y=0;y<=4;y++) level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
     }
     level.setBlock(pos,Blocks.CRAFTING_TABLE.defaultBlockState(),Block.UPDATE_ALL);
     SlabAnchorAttachment.writePlacementDy(level,pos,dy);
     player.teleportTo(feet.x,feet.y,feet.z);
    });
    context.waitFor(c -> c.level!=null && c.player!=null && c.level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)
      && SlabAnchorAttachment.storedPlacementDy(c.level,pos)==dy && c.player.position().distanceToSqr(feet)<0.01,200);
    context.waitTicks(5);
    context.runOnClient(c -> {
     TableStation station=new TableStation(pos,Direction.SOUTH);
     var plan=Reach.plan(station,c.player);
     double collisionTop=-Double.MAX_VALUE;
     for(var shape:c.level.getBlockCollisions(c.player,new net.minecraft.world.phys.AABB(pos).inflate(0.01)))
      if(!shape.isEmpty()) collisionTop=Math.max(collisionTop,shape.bounds().maxY);
     if(Math.abs(collisionTop-(pos.getY()+1+dy))>0.001) throw new AssertionError("collision top not translated: "+collisionTop);
     var hit=new net.minecraft.world.phys.BlockHitResult(new Vec3(pos.getX()+0.5,collisionTop,pos.getZ()+0.5),Direction.UP,pos,false);
     Slabbed.LOGGER.info("CINEMATIC_REPRO dy={} stored={} work={} feet={} collisionTop={} scene={} reason={} why={}",dy,SlabAnchorAttachment.storedPlacementDy(c.level,pos),station.workPoint(),c.player.position(),collisionTop,plan.scene(),plan.reason(),plan.why());
     if(dy==0 && !plan.scene()) throw new AssertionError("unlowered control failed: "+plan);
     if(!plan.scene()) throw new AssertionError("translated scene refused: "+plan);
     c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
     Slabbed.LOGGER.info("CINEMATIC_USE dy={} owner={} state={} physicalHit={} phase={} refused={}",dy,hit.getBlockPos(),c.level.getBlockState(hit.getBlockPos()),hit.getLocation(),dev.dyslexic.cinematic.client.Scene.phase,dev.dyslexic.cinematic.client.Scene.refused);
     if(dy==0 && dev.dyslexic.cinematic.client.Scene.phase==dev.dyslexic.cinematic.client.Scene.Phase.NONE) throw new AssertionError("full table failed to start scene");
     if(dev.dyslexic.cinematic.client.Scene.phase!=dev.dyslexic.cinematic.client.Scene.Phase.INTRO) throw new AssertionError("translated scene did not start");
    });
    context.waitTicks(50);
    context.runOnClient(c -> {
     if(dev.dyslexic.cinematic.client.Scene.phase!=dev.dyslexic.cinematic.client.Scene.Phase.ACTIVE) throw new AssertionError("scene did not become ACTIVE: "+dev.dyslexic.cinematic.client.Scene.phase);
     var st=(TableStation)dev.dyslexic.cinematic.client.Scene.station;
     if(Math.abs(st.workPoint().y-(pos.getY()+1+dy))>0.001) throw new AssertionError("work frame drift");
     Slabbed.LOGGER.info("CINEMATIC_ACTIVE work={} camera={} leftHand={} rightHand={}",st.workPoint(),st.lean().pos(),st.rest(false),st.rest(true));
    });
    world.getServer().runOnServer(server -> {
     var player=server.getPlayerList().getPlayers().getFirst();
     player.inventoryMenu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG));
     player.inventoryMenu.sendAllDataToRemote();
    });
    context.waitFor(c -> c.player.inventoryMenu.getCarried().is(net.minecraft.world.item.Items.OAK_LOG),100);
    context.takeScreenshot("cinematic-before-grid-pick-"+dy);
    double[] mouse=context.computeOnClient(c -> {
     Vec3 target=new Vec3(pos.getX()+dev.dyslexic.cinematic.table.TableGrid.cellX(0),pos.getY()+1+dy,
        pos.getZ()+dev.dyslexic.cinematic.table.TableGrid.cellZ(0));
     double[] gui=dev.dyslexic.cinematic.client.Picking.toGui(target);
     if(gui==null) throw new AssertionError("table cell is not projected on screen");
     return new double[]{gui[0]*c.getWindow().getScreenWidth()/c.getWindow().getGuiScaledWidth(),
        gui[1]*c.getWindow().getScreenHeight()/c.getWindow().getGuiScaledHeight()};
    });
    context.getInput().setCursorPos(mouse[0],mouse[1]);
    context.waitTicks(2);
    context.runOnClient(c -> {
     var table=(TableStation)dev.dyslexic.cinematic.client.Scene.station;
     table.updateCursor(false);
     if(table.hoverCell!=0) throw new AssertionError("physical cursor selected wrong cell: "+table.hoverCell);
     var physical=dev.dyslexic.cinematic.client.Picking.cursorOnPlane(pos.getY()+1+dy);
     if(physical==null || Math.abs(physical.y-(pos.getY()+1+dy))>0.0001) throw new AssertionError("cursor plane disagrees with visible table");
     Slabbed.LOGGER.info("CINEMATIC_CURSOR dy={} pickedCell={} planeY={}",dy,table.hoverCell,physical.y);
    });
    context.takeScreenshot("cinematic-target-grid-pick-"+dy);
    context.getInput().pressMouse(1);
    context.waitFor(c -> dev.dyslexic.cinematic.client.ClientTables.get(pos)!=null
       && dev.dyslexic.cinematic.client.ClientTables.get(pos).items[0].is(net.minecraft.world.item.Items.OAK_LOG),100);
    context.waitFor(c -> ((TableStation)dev.dyslexic.cinematic.client.Scene.station).result.is(net.minecraft.world.item.Items.OAK_PLANKS),100);
    context.takeScreenshot("cinematic-grid-"+dy);
    context.runOnClient(c -> {
     dev.dyslexic.cinematic.client.Scene.abort();
     final net.minecraft.world.phys.AABB[] captured={null};
     var frustum=new net.minecraft.client.renderer.culling.Frustum(new org.joml.Matrix4f(),new org.joml.Matrix4f()) {
      @Override public boolean isVisible(net.minecraft.world.phys.AABB bounds) { captured[0]=bounds; return false; }
     };
     dev.dyslexic.cinematic.client.TableRenderer.renderAll(new com.mojang.blaze3d.vertex.PoseStack(),null,c.player.getEyePosition(),frustum,c.level);
     if(captured[0]==null || Math.abs(captured[0].minY-(pos.getY()+dy))>0.001
        || Math.abs(captured[0].maxY-(pos.getY()+1.4+dy))>0.001) throw new AssertionError("idle item culling not translated: "+captured[0]);
     Slabbed.LOGGER.info("CINEMATIC_IDLE_BOUNDS dy={} bounds={}",dy,captured[0]);
    });
    var reload=context.computeOnClient(c -> c.reloadResourcePacks());
    context.waitFor(c -> reload.isDone() && c.gui.overlay()==null,400);
    if(reload.isCompletedExceptionally()) throw new AssertionError("resource reload failed");
    context.waitTicks(5);
    context.runOnClient(c -> c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,
       new net.minecraft.world.phys.BlockHitResult(new Vec3(pos.getX()+0.5,pos.getY()+1+dy,pos.getZ()+0.5),Direction.UP,pos,false)));
    context.waitTicks(50);
    context.runOnClient(c -> {
     if(dev.dyslexic.cinematic.client.Scene.phase!=dev.dyslexic.cinematic.client.Scene.Phase.ACTIVE) throw new AssertionError("reload reopen failed: "+dev.dyslexic.cinematic.client.Scene.phase+" refused="+dev.dyslexic.cinematic.client.Scene.refused+" screen="+c.gui.screen()+" feet="+c.player.position());
    });
    context.waitFor(c -> ((TableStation)dev.dyslexic.cinematic.client.Scene.station).result.is(net.minecraft.world.item.Items.OAK_PLANKS),100);
    context.runOnClient(c -> ((TableStation)dev.dyslexic.cinematic.client.Scene.station).craft(false));
    context.waitFor(c -> c.player.inventoryMenu.getCarried().is(net.minecraft.world.item.Items.OAK_PLANKS)
       && c.player.inventoryMenu.getCarried().getCount()==4,100);
    Slabbed.LOGGER.info("CINEMATIC_CRAFT dy={} oak_log_to_four_planks=true",dy);
    context.runOnClient(c -> dev.dyslexic.cinematic.client.Scene.abort());
    context.waitTicks(10);
    world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().teleportTo(feet.x,feet.y,feet.z));
    context.waitFor(c -> c.player.position().distanceToSqr(feet)<0.01,100);
    context.runOnClient(c -> c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,
       new net.minecraft.world.phys.BlockHitResult(new Vec3(pos.getX()+0.5,pos.getY()+1+dy,pos.getZ()+0.5),Direction.UP,pos,false)));
    context.waitTicks(50);
    context.runOnClient(c -> {
     if(dev.dyslexic.cinematic.client.Scene.phase!=dev.dyslexic.cinematic.client.Scene.Phase.ACTIVE) throw new AssertionError("reopen failed");
     Slabbed.LOGGER.info("CINEMATIC_REOPEN dy={} active=true",dy);
    });
    context.runOnClient(c -> dev.dyslexic.cinematic.client.Scene.abort());
    world.getServer().runOnServer(server -> {
     var level=server.overworld();
     level.setBlock(pos.above(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
     SlabAnchorAttachment.writePlacementDy(level,pos.above(),-0.5);
    });
    context.waitFor(c -> c.level.getBlockState(pos.above()).is(Blocks.STONE) && SlabAnchorAttachment.storedPlacementDy(c.level,pos.above())==-0.5,100);
    context.runOnClient(c -> {
     var plan=Reach.planHere(new TableStation(pos,Direction.SOUTH),c.player);
     Slabbed.LOGGER.info("CINEMATIC_OBSTACLE scene={} reason={} why={}",plan.scene(),plan.reason(),plan.why());
     if(plan.scene()) throw new AssertionError("lowered cover ignored by scene visibility");
    });
    context.runOnClient(c -> {
     if(SlabAnchorAttachment.storedPlacementDy(c.level,pos)!=dy) throw new AssertionError("neighbor changed saved height");
    });
   }
  }
 }
}
