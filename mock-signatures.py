#!/usr/bin/env python3
"""Synthetic only. Catches local Java syntax/integration inconsistencies.
It CANNOT validate real Minecraft/Fabric 26.3 class and method signatures.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT=Path(__file__).resolve().parents[1]
S={
'com/mojang/blaze3d/platform/InputConstants.java':'''package com.mojang.blaze3d.platform;
 public class InputConstants { public enum Type { KEYBOARD, KEYSYM, MOUSE } public static final int KEY_U=1,KEY_V=2,KEY_N=3,KEY_G=4,KEY_B=5,KEY_O=6,KEY_I=7,KEY_P=8,KEY_K=9,KEY_J=10,KEY_H=11;}''',
 'net/minecraft/sounds/SoundEvents.java':'''package net.minecraft.sounds;public class SoundEvents{
 public static final Ref UI_BUTTON_CLICK=new Ref(); public static class Ref{public Object value(){return new Object();}}}''',
 'net/minecraft/client/resources/sounds/SimpleSoundInstance.java':'''package net.minecraft.client.resources.sounds;public class SimpleSoundInstance{
 public static SimpleSoundInstance forUI(Object event,float pitch,float volume){return new SimpleSoundInstance();}}''',
'net/fabricmc/api/ClientModInitializer.java':'''package net.fabricmc.api;public interface ClientModInitializer{void onInitializeClient();}''',
'net/fabricmc/loader/api/FabricLoader.java':'''package net.fabricmc.loader.api;import java.nio.file.Path;
 public class FabricLoader {public static FabricLoader getInstance(){return new FabricLoader();}public Path getConfigDir(){return Path.of("config");}}''',
'net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java':'''package net.fabricmc.fabric.api.client.event.lifecycle.v1;import net.minecraft.client.Minecraft;
 public class ClientTickEvents {public interface Callback{void tick(Minecraft m);}public static final Event END_CLIENT_TICK=new Event();public static class Event{public void register(Callback c){}}}''',
'net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper.java':'''package net.fabricmc.fabric.api.client.keymapping.v1;
 import net.minecraft.client.KeyMapping;public class KeyMappingHelper {public static KeyMapping registerKeyMapping(KeyMapping k){return k;}}''',
'net/fabricmc/fabric/api/client/rendering/v1/hud/HudElementRegistry.java':'''package net.fabricmc.fabric.api.client.rendering.v1.hud;
 import net.minecraft.resources.Identifier;import net.minecraft.client.gui.GuiGraphicsExtractor;
 public class HudElementRegistry {public interface Hud{void draw(GuiGraphicsExtractor g,Object delta);}public static void attachElementBefore(Identifier place,Identifier id,Hud action){}}''',
'net/fabricmc/fabric/api/client/rendering/v1/hud/VanillaHudElements.java':'''package net.fabricmc.fabric.api.client.rendering.v1.hud;import net.minecraft.resources.Identifier;
 public class VanillaHudElements {public static Identifier CHAT=new Identifier();}''',
'net/minecraft/resources/Identifier.java':'''package net.minecraft.resources;public class Identifier {public static Identifier fromNamespaceAndPath(String a,String b){return new Identifier();}}''',
'net/minecraft/client/KeyMapping.java':'''package net.minecraft.client;import com.mojang.blaze3d.platform.InputConstants;import net.minecraft.resources.Identifier;
 public class KeyMapping {public boolean isDown(){return true;} public static class Category{public static Category register(Identifier i){return new Category();}}
 public KeyMapping(String t,InputConstants.Type typ,int code,Category c){}public boolean consumeClick(){return false;}}''',
'net/minecraft/world/phys/Vec3.java':'''package net.minecraft.world.phys;public class Vec3{public double x,y,z;public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}public Vec3(){}public Vec3 lerp(Vec3 to,double t){return new Vec3();}public Vec3 subtract(Vec3 v){return new Vec3(x-v.x,y-v.y,z-v.z);}}''',
'net/minecraft/core/BlockPos.java':'''package net.minecraft.core;import net.minecraft.world.phys.Vec3; public class BlockPos { public static BlockPos containing(Vec3 v){return new BlockPos();}}''',
'net/minecraft/world/level/ClipContext.java':'''package net.minecraft.world.level;import net.minecraft.world.phys.Vec3;import net.minecraft.world.entity.Entity;public class ClipContext {public enum Block { COLLIDER } public enum Fluid { NONE }public ClipContext(Vec3 from,Vec3 to,Block b,Fluid f,Entity shooter){}}''',
'net/minecraft/world/phys/HitResult.java':'''package net.minecraft.world.phys;public class HitResult { public enum Type { BLOCK, MISS } public HitResult(){}public HitResult(Type t){type=t;}private Type type=Type.MISS;public Type getType(){return type;} }''',
'net/minecraft/client/multiplayer/ClientLevel.java':'''package net.minecraft.client.multiplayer;import net.minecraft.world.level.*;import net.minecraft.world.phys.HitResult;import net.minecraft.core.BlockPos;public class ClientLevel extends Level {public boolean loaded=true,blocked=false;public boolean hasChunkAt(BlockPos p){return loaded;}public HitResult clip(ClipContext c){return new HitResult(blocked?HitResult.Type.BLOCK:HitResult.Type.MISS);}}''',
'net/minecraft/world/phys/AABB.java':'''package net.minecraft.world.phys;public class AABB{public AABB inflate(double d){return this;}public Vec3 getCenter(){return new Vec3();}public double getXsize(){return 1;}public double getYsize(){return 1;}public double getZsize(){return 1;}}''',
'net/minecraft/network/chat/Component.java':'''package net.minecraft.network.chat;public class Component{public static Component literal(String x){return new Component();}public String getString(){return "name";}}''',
'net/minecraft/world/InteractionHand.java':'''package net.minecraft.world;public enum InteractionHand{MAIN_HAND,OFF_HAND}''',
'net/minecraft/world/item/Item.java':'''package net.minecraft.world.item;public class Item{}''',
'net/minecraft/world/item/BowItem.java':'''package net.minecraft.world.item;public class BowItem extends Item{}''',
'net/minecraft/world/item/ItemStack.java':'''package net.minecraft.world.item;public class ItemStack{public Item getItem(){return new Item();}}''',
'net/minecraft/world/entity/Entity.java':'''package net.minecraft.world.entity;
 import net.minecraft.world.phys.*;import net.minecraft.network.chat.Component;import java.util.UUID;
 public class Entity{public void setYRot(float r){}public void setXRot(float r){}public AABB getBoundingBox(){return new AABB();}public boolean isAlive(){return true;}
 public boolean isSpectator(){return false;}public UUID getUUID(){return UUID.randomUUID();}
 public Component getName(){return new Component();}public double distanceTo(Entity other){return 1;}
 public double distanceToSqr(Entity other){return 1;}public Vec3 getDeltaMovement(){return new Vec3();}}''',
'net/minecraft/world/entity/projectile/arrow/AbstractArrow.java':'''package net.minecraft.world.entity.projectile.arrow;
 import net.minecraft.world.entity.Entity;import net.minecraft.world.phys.Vec3;
 public class AbstractArrow extends Entity {public Entity getOwner(){return null;}public Vec3 position(){return new Vec3();}}''',
'net/minecraft/world/entity/projectile/arrow/ThrownTrident.java':'''package net.minecraft.world.entity.projectile.arrow;
 public class ThrownTrident extends AbstractArrow {}''',
'net/minecraft/world/entity/LivingEntity.java':'''package net.minecraft.world.entity;public class LivingEntity extends Entity{}''',
'net/minecraft/world/entity/monster/Enemy.java':'''package net.minecraft.world.entity.monster;public interface Enemy{}''',
'net/minecraft/world/entity/player/Player.java':'''package net.minecraft.world.entity.player;
 import net.minecraft.world.entity.LivingEntity;import net.minecraft.world.item.ItemStack;import net.minecraft.world.phys.Vec3;
 public class Player extends LivingEntity{public net.minecraft.world.item.ItemStack getItemInHand(net.minecraft.world.InteractionHand hand){return new net.minecraft.world.item.ItemStack();}public boolean horizontalCollision=false;public boolean onGround(){return true;}public boolean isUsingItem(){return true;}public ItemStack getUseItem(){return new ItemStack();}
 public Vec3 getEyePosition(){return new Vec3();}public float getYRot(){return 0;}public float getXRot(){return 0;}public int getTicksUsingItem(){return 20;}}''',
'net/minecraft/client/player/LocalPlayer.java':'''package net.minecraft.client.player;public class LocalPlayer extends net.minecraft.world.entity.player.Player{}''',
'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level;
 import net.minecraft.world.entity.*;import net.minecraft.world.phys.AABB;
 import java.util.List;import java.util.ArrayList;import java.util.function.Predicate;
 public class Level {public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type,AABB aabb,Predicate<? super T> predicate){return new ArrayList<>();}}''',
'net/minecraft/client/gui/GuiGraphicsExtractor.java':'''package net.minecraft.client.gui;
 public class GuiGraphicsExtractor{public int guiWidth(){return 800;}public int guiHeight(){return 480;}
 public void fill(int x1,int y1,int x2,int y2,int color){}public void text(Object f,String s,int x,int y,int color,boolean shadow){}
 public Pose pose(){return new Pose();}public static class Pose { public void pushMatrix(){}public void popMatrix(){}
 public void translate(float x,float y){}public void scale(float x,float y){} }}''',
'net/minecraft/client/gui/components/Button.java':'''package net.minecraft.client.gui.components;
 import net.minecraft.network.chat.Component;import java.util.function.Consumer;
 public class Button{public static Builder builder(Component text,Consumer<Button> callback){return new Builder();}
 public static class Builder{public Builder bounds(int x,int y,int w,int h){return this;}public Button build(){return new Button();}}}''',
'net/minecraft/client/gui/components/EditBox.java':'''package net.minecraft.client.gui.components;
 import net.minecraft.network.chat.Component;
 public class EditBox { private String value="";
 public EditBox(Object f,int x,int y,int width,int height,Component label){}
 public void setValue(String v){value=v;}public String getValue(){return value;}
 public void setMaxLength(int n){} }''',
'net/minecraft/client/gui/screens/Screen.java':'''package net.minecraft.client.gui.screens;
 import net.minecraft.network.chat.Component;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphicsExtractor;
 public class Screen{protected int width=800,height=480;protected Object font=new Object();protected Minecraft minecraft=new Minecraft();
 public Screen(Component title){}protected void init(){}protected void rebuildWidgets(){}
 protected <T> T addRenderableWidget(T widget){return widget;}
 public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){}public void onClose(){}}''',
'net/minecraft/network/protocol/game/ServerboundMovePlayerPacket.java':'''package net.minecraft.network.protocol.game;public class ServerboundMovePlayerPacket { public static class Rot{public Rot(float yaw,float pitch,boolean onGround,boolean collision){}}}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;
 import net.minecraft.client.player.LocalPlayer;import net.minecraft.world.level.Level;import net.minecraft.client.gui.screens.Screen;
 public class Minecraft{public LocalPlayer player;public net.minecraft.client.multiplayer.ClientLevel level;public Object font=new Object();public Options options=new Options();public MultiPlayerGameMode gameMode=new MultiPlayerGameMode();
 public Gui gui=new Gui();public Connection getConnection(){return new Connection();}public static class Connection{public void send(Object packet){}}public static Minecraft getInstance(){return new Minecraft();}
 public SoundManager getSoundManager(){return new SoundManager();}public static class SoundManager{public void play(Object instance){}}
 public static class Options{public boolean hideGui;public KeyMapping keyAttack=new KeyMapping("attack",com.mojang.blaze3d.platform.InputConstants.Type.MOUSE,0,KeyMapping.Category.register(new net.minecraft.resources.Identifier()));public KeyMapping keyUse=new KeyMapping("use",com.mojang.blaze3d.platform.InputConstants.Type.MOUSE,1,KeyMapping.Category.register(new net.minecraft.resources.Identifier()));}public static class MultiPlayerGameMode {public boolean isDestroying(){return false;}public void stopDestroyBlock(){}public void useItem(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){}public void releaseUsingItem(net.minecraft.world.entity.player.Player player){}}public static class Gui{public Screen screen(){return null;}public void setScreen(Screen s){}}}''',
}
with TemporaryDirectory(prefix='leadshot-mock-') as tmp:
    root=Path(tmp)
    for name,source in S.items():
        target=root/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source)
    java=[str(p) for p in root.rglob('*.java')]+[str(p) for p in (ROOT/'src/main/java').rglob('*.java')]+[str(ROOT/'tools/ClientBlockOcclusionStubTest.java')]
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(root/'classes'),*java],check=True)
    subprocess.run(['java','-cp',str(root/'classes'),'dev.leadshot.ClientBlockOcclusionStubTest'],check=True)
    print('PASS synthetic API javac:',len(java),'Java files (NOT real Fabric compilation)')
