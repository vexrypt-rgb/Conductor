import os,re,sys,collections,json
root=sys.argv[1]
mods=os.path.join(root,'systems/modules')
needs={
 'RENDER_WORLD (3D overlays: boxes, lines, tracers)': r'Render3DEvent|RenderAfterWorldEvent',
 'RENDER_HUD (2D overlay, text)': r'Render2DEvent',
 'RENDER_TWEAK (change vanilla rendering via mixin flags)': r'RenderBlockEntityEvent|RenderItemEntityEvent|ChunkOcclusionEvent|GetFovEvent|ApplyTransformationEvent|RenderBossBarEvent|ParticleEvent|isActive\(\)\s*&&.*render|NoRender',
 'PACKETS (read/send/cancel network packets)': r'PacketEvent|\.send\(new Serverbound|sendPacket|connection\.send',
 'LOOK (rotation)': r'Rotations\.rotate|setYRot|setXRot',
 'HOTBAR/INVENTORY (select, swap, move slots)': r'InvUtils\.(swap|move|click|shiftClick|drop|quickSwap)|setSelectedSlot|selectSlot',
 'INVENTORY READ (find items)': r'InvUtils\.(find|findInHotbar|testIn)|getInventory\(\)',
 'BLOCK ACTIONS (place, break, interact)': r'BlockUtils\.(place|breakBlock|interact)|gameMode\.(useItemOn|startDestroyBlock|continueDestroyBlock|destroyBlock)',
 'ENTITY ACTIONS (attack, interact)': r'gameMode\.(attack|interact)|attackEntity|AttackEntityEvent',
 'USE ITEM': r'gameMode\.useItem\b|useItem\(|keyUse',
 'ENTITY OBSERVATION (nearby entities)': r'entitiesForRendering|getEntities|TargetUtils|EntityAddedEvent|getEntitiesOfClass',
 'BLOCK/WORLD OBSERVATION (block states, scanning)': r'getBlockState|BlockIterator|ChunkDataEvent|BlockUpdateEvent|getChunk',
 'PLAYER STATE (health, food, effects, position)': r'getHealth|getFoodData|getActiveEffects|hasEffect|PlayerUtils\.(getTotalHealth|getDimension)|position\(\)|onGround',
 'MOTION WRITE (velocity / physics changes)': r'setDeltaMovement|PlayerMoveEvent|JumpVelocity|CollisionShapeEvent|setOnGround|noPhysics|abilities\.(flying|setFlyingSpeed)',
 'INPUT (keys, mouse, keybind)': r'KeyInputEvent|MouseClickEvent|MouseScrollEvent|options\.key|KeyMapping|\.setDown\(',
 'CHAT (send / receive)': r'ChatUtils\.sendPlayerMsg|sendChat|sendCommand|ReceiveMessageEvent|SendMessageEvent',
 'SCREENS / CONTAINERS': r'OpenScreenEvent|setScreen|AbstractContainerMenu|containerMenu|ScreenHandler',
 'PATHING ENGINE (Baritone)': r'PathManagers',
 'CAMERA (freecam, zoom, perspective)': r'Camera|ChangePerspectiveEvent|GetFovEvent',
 'SOUND': r'PlaySoundEvent|SoundEvent|playSound',
 'TIMER / GAME SPEED': r'Timer\b|tickRate|Modules\.get\(\)\.get\(Timer',
 'CONNECTION (reconnect, disconnect, server data)': r'ConnectScreen|disconnect\(|GameLeftEvent|ServerConnect',
}
res={}
for dp,_,fs in os.walk(mods):
    for f in fs:
        if not f.endswith('.java'): continue
        src=open(os.path.join(dp,f),encoding='utf-8',errors='ignore').read()
        if not re.search(r'extends Module\b',src): continue
        cat=os.path.relpath(dp,mods).split(os.sep)[0]
        res[f[:-5]]={'category':cat,'needs':[k for k,v in needs.items() if re.search(v,src)]}
count=collections.Counter(n for m in res.values() for n in m['needs'])
print(len(res),'modules')
for k,v in count.most_common(): print(f'{v:4d}  {k}')
json.dump(res,open(sys.argv[2],'w'),indent=1)
