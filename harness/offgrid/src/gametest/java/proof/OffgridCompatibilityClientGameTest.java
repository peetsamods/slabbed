package proof;
import dev.tazer.offgrid.slab.MixedSlabBlock;
import dev.tazer.offgrid.slab.MixedSlabBlockEntity;
import dev.tazer.offgrid.slab.MixedSlabs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
public final class OffgridCompatibilityClientGameTest implements FabricClientGameTest {
 static void log(String s) {System.out.println("OFFGRID_PROOF "+s);}
 public void runTest(ClientGameTestContext context) {
  log("MODS "+FabricLoader.getInstance().getAllMods().stream().map(m->m.getMetadata().getId()+"="+m.getMetadata().getVersion()).sorted().toList());
  for(boolean serverOnly:new boolean[]{false,true}) for(boolean loweredSupport:new boolean[]{true,false}) for(boolean removeUpper:new boolean[]{false,true}) {
   var world=context.worldBuilder().setUseConsistentSettings(true).create();
   boolean worldClosed=false;
   try {
   world.getConnection().waitForChunksDownload();
   log("CASE loweredSupport="+loweredSupport+" removeUpper="+removeUpper+" serverOnly="+serverOnly);
   double[] expectedBottom={Double.NaN};
   Object[] beforeFact={null};
   BlockPos pos=world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().above(3).immutable());
   Vec3 feet=new Vec3(pos.getX()+0.5,pos.getY()-1.37,pos.getZ()+2.5);
   world.getServer().runOnServer(s -> {
    var l=s.overworld(); var p=s.getPlayerList().getPlayers().getFirst();
    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-2;y<=3;y++) l.setBlock(pos.offset(x,y,z),y==-2?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
    l.setBlock(pos.below(2),(loweredSupport?Blocks.STONE_SLAB:Blocks.STONE).defaultBlockState(),Block.UPDATE_ALL);
    p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(feet.x,feet.y,feet.z);p.setYRot(180);p.setXRot(0);
   });
   context.waitFor(c->c.player!=null && c.player.position().distanceToSqr(feet)<0.01,200);
   context.runOnClient(c->{c.player.getAbilities().flying=true;c.player.setYRot(180);c.player.setXRot(0);});
   use(context,world,Items.STONE,new BlockHitResult(new Vec3(pos.getX()+0.5,pos.getY()-(loweredSupport?1.5:1.0),pos.getZ()+0.5),Direction.UP,pos.below(2),false));
   world.getServer().runOnServer(s->{snapshot("full-support-server",s.overworld(),pos.below(),s.getPlayerList().getPlayers().getFirst());if(!s.overworld().getBlockState(pos.below()).is(Blocks.STONE)) throw new AssertionError("INVALID_CONTROL missing full support");});
   double supportTop=context.computeOnClient(c->c.level.getBlockState(pos.below()).getShape(c.level,pos.below()).max(Direction.Axis.Y));
   log("SUPPORT_TOP world="+(pos.getY()-1+supportTop));
   use(context,world,Items.STONE_SLAB,new BlockHitResult(new Vec3(pos.getX()+0.5,pos.getY()-1+supportTop,pos.getZ()+0.5),Direction.UP,pos.below(),false));
   world.getServer().runOnServer(s -> {
    var st=s.overworld().getBlockState(pos);log("FIRST "+st);snapshot("first-server",s.overworld(),pos,s.getPlayerList().getPlayers().getFirst());
    log("ELIGIBILITY slabOffset="+dev.tazer.offgrid.world.Overlays.canOffset(st)+" mixedOffset="+dev.tazer.offgrid.world.Overlays.canOffset(dev.tazer.offgrid.registry.OffgridBlocks.getMixedSlab().defaultBlockState())+" settings="+dev.tazer.offgrid.OffgridSettings.of(s.overworld()));
    if(!st.is(Blocks.STONE_SLAB)||st.getValue(SlabBlock.TYPE)!=SlabType.BOTTOM)throw new AssertionError("INVALID_CONTROL first slab");
   });
   double firstTop=context.computeOnClient(c->c.level.getBlockState(pos).getShape(c.level,pos).max(Direction.Axis.Y));
   use(context,world,Items.OAK_SLAB,new BlockHitResult(new Vec3(pos.getX()+0.5,pos.getY()+firstTop,pos.getZ()+0.5),Direction.UP,pos,false));
   world.getServer().runOnServer(s -> {
    var l=s.overworld();log("MIXED_SERVER "+l.getBlockState(pos));
    if(!(l.getBlockEntity(pos) instanceof MixedSlabBlockEntity be))throw new AssertionError("INVALID_CONTROL did not form mixed slab");
    log("HALVES first="+be.getFirst()+" second="+be.getSecond());
    if(!be.getFirst().is(Blocks.STONE_SLAB)||!be.getSecond().is(Blocks.OAK_SLAB))throw new AssertionError("INVALID_CONTROL materials");
   });
   context.waitFor(c->c.level.getBlockEntity(pos) instanceof MixedSlabBlockEntity,100);
   world.getServer().runOnServer(s->{
    var l=s.overworld();var pl=s.getPlayerList().getPlayers().getFirst();
    double bottom=l.getBlockState(pos).getShape(l,pos).min(Direction.Axis.Y);
    expectedBottom[0]=bottom+(removeUpper?0:0.5);
    if(FabricLoader.getInstance().isModLoaded("slabbed"))beforeFact[0]=SurvivorTransitionControls.raw(l,pos);
    if(removeUpper)pl.teleportTo(pos.getX()+0.5,pos.getY()+3-pl.getEyeHeight(),pos.getZ()+0.5);else pl.teleportTo(pos.getX()+0.5,pos.getY()+bottom+0.25-pl.getEyeHeight(),pos.getZ()+2.5);pl.setYRot(180);pl.setXRot(removeUpper?90:0);
    log("MIXED_BOUNDS bottom="+bottom+" expectedRemainingMin="+expectedBottom[0]);
   });
   context.waitTicks(10);
   context.runOnClient(c->{
    c.player.setYRot(180);c.player.setXRot(removeUpper?90:0);
    log("TARGET second="+MixedSlabs.targetsSecond(c.player,pos,Direction.Axis.Y)+" eye="+c.player.getEyePosition()+" pos="+pos);
    if(MixedSlabs.targetsSecond(c.player,pos,Direction.Axis.Y)!=removeUpper)throw new AssertionError("INVALID_CONTROL targets upper");
    snapshot("before-client",c.level,pos,c.player);
    float[] before={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,0};
    var preEmitter=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q->{before[2]++;for(int v=0;v<4;v++){before[0]=Math.min(before[0],q.y(v));before[1]=Math.max(before[1],q.y(v));}});
    var preState=c.level.getBlockState(pos);c.getModelManager().getBlockStateModelSet().get(preState).emitQuads(preEmitter,c.level,pos,preState,net.minecraft.util.RandomSource.create(0),d->false);
    log("BEFORE_MODEL outer="+c.getModelManager().getBlockStateModelSet().get(preState).getClass().getName()+" min="+before[0]+" max="+before[1]+" quads="+before[2]);
    if(Math.abs(before[0]+(removeUpper?0:0.5)-expectedBottom[0])>0.00001)throw new AssertionError("INVALID_CONTROL model and outline disagree before break");
    try {var pick=c.getClass().getDeclaredMethod("pick",float.class);pick.setAccessible(true);pick.invoke(c,1.0f);}catch(Exception e){throw new AssertionError("pick invocation",e);}
    var hit=c.hitResult;
    if(!(hit instanceof BlockHitResult))throw new AssertionError("INVALID_CONTROL not block target");
    var ray=(BlockHitResult)hit;
    log("BREAK_RAY owner="+ray.getBlockPos()+" location="+ray.getLocation()+" type="+ray.getType());
    if(ray.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK || !ray.getBlockPos().equals(pos))throw new AssertionError("INVALID_CONTROL lower ray owner");
    if(!serverOnly && !Boolean.getBoolean("offgrid.test.modelOnly")) {var result=c.gameMode.startDestroyBlock(ray.getBlockPos(),ray.getDirection());log("PLAYER_BREAK "+result);}
   });
   if(Boolean.getBoolean("offgrid.test.modelOnly")) {log("PASS lowered mixed model alignment");return;}
   if(serverOnly)world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();log("SERVER_ONLY_BREAK "+p.gameMode.destroyBlock(pos));});
   context.waitTicks(20);
   world.getServer().runOnServer(s->{snapshot("after-server",s.overworld(),pos,s.getPlayerList().getPlayers().getFirst());if(beforeFact[0]!=null&&!beforeFact[0].equals(SurvivorTransitionControls.raw(s.overworld(),pos)))throw new AssertionError("server raw fact changed");});
   verifyClient(context,pos,removeUpper,expectedBottom[0],beforeFact[0],"after-client");
   log("PASS mixed slab removal preserves surviving geometry");
   if(beforeFact[0]!=null)world.getServer().runOnServer(s->SurvivorTransitionControls.run(s.overworld(),pos.east(5)));
   if(serverOnly && loweredSupport) {
    var save=world.getWorldSave();world.close();worldClosed=true;
    try(var reloaded=save.open()) {
     reloaded.getConnection().waitForChunksDownload();context.waitTicks(10);
     reloaded.getServer().runOnServer(s->{if(beforeFact[0]!=null&&!beforeFact[0].equals(SurvivorTransitionControls.raw(s.overworld(),pos)))throw new AssertionError("reload server raw fact");});
     verifyClient(context,pos,removeUpper,expectedBottom[0],beforeFact[0],"reload-client");
     log("PASS save/reload survivor");
    }
   }
   } finally {if(!worldClosed)world.close();}
  }
 }
 static void verifyClient(ClientGameTestContext context,BlockPos pos,boolean removeUpper,double expectedBottom,Object beforeFact,String label) {
  context.runOnClient(c->{
    snapshot(label,c.level,pos,c.player);
    if(beforeFact!=null&&!beforeFact.equals(SurvivorTransitionControls.raw(c.level,pos)))throw new AssertionError("remote client raw fact changed");
    var st=c.level.getBlockState(pos);
    float[] bounds={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,0};
    var emitter=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q->{bounds[2]++;for(int v=0;v<4;v++){bounds[0]=Math.min(bounds[0],q.y(v));bounds[1]=Math.max(bounds[1],q.y(v));}});
    var model=c.getModelManager().getBlockStateModelSet().get(st);
    model.emitQuads(emitter,c.level,pos,st,net.minecraft.util.RandomSource.create(0),d->false);
    log("MODEL min="+bounds[0]+" max="+bounds[1]+" quads="+bounds[2]+" class="+model.getClass().getName());
    if(!st.is(removeUpper?Blocks.STONE_SLAB:Blocks.OAK_SLAB)||st.getValue(SlabBlock.TYPE)!=(removeUpper?SlabType.BOTTOM:SlabType.TOP))throw new AssertionError("RED wrong remaining state");
    if(Math.abs(bounds[0]-expectedBottom)>0.00001)throw new AssertionError("RED remaining top moved modelMin="+bounds[0]);
    if(Math.abs(st.getShape(c.level,pos).min(Direction.Axis.Y)-expectedBottom)>0.00001)throw new AssertionError("RED remaining top moved outline");
    if(Math.abs(st.getCollisionShape(c.level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(c.player)).min(Direction.Axis.Y)-expectedBottom)>0.00001)throw new AssertionError("RED remaining top moved collision");
   });
 }
 static void use(ClientGameTestContext c,TestSingleplayerContext w,Item item,BlockHitResult hit) {
  w.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));p.inventoryMenu.broadcastChanges();});
  c.waitFor(mc->mc.player.getMainHandItem().is(item),100);
  c.runOnClient(mc->log("USE item="+item+" result="+mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit)));
  c.waitTicks(10);
 }
 static void snapshot(String label,Level l,BlockPos p,net.minecraft.world.entity.player.Player viewer) {
  var st=l.getBlockState(p);Object dy="absent";
  if(FabricLoader.getInstance().isModLoaded("slabbed"))try{dy=Class.forName("com.slabbed.anchor.SlabAnchorAttachment").getMethod("storedPlacementDy",net.minecraft.world.level.BlockGetter.class,BlockPos.class).invoke(null,l,p);}catch(Exception e){dy=e.toString();}
  log(label+" state="+st+" offgridOffset="+dev.tazer.offgrid.world.Offsets.at(l,p)+" overlays="+dev.tazer.offgrid.world.Cells.overlays(l,p).size()+" dy="+dy+" outline="+st.getShape(l,p).toAabbs()+" contextualOutline="+st.getShape(l,p,net.minecraft.world.phys.shapes.CollisionContext.of(viewer)).toAabbs()+" contextualCollision="+st.getCollisionShape(l,p,net.minecraft.world.phys.shapes.CollisionContext.of(viewer)).toAabbs()+" collision="+st.getCollisionShape(l,p).toAabbs());
 }
}
