package dev.leadshot;

import java.util.UUID;
/** Waits for standard client movement synchronization before authorizing an assisted release.
 * Release still sends a final vanilla rotation packet directly before RELEASE_USE_ITEM.
 * This tracks local commands, not acknowledgements from the remote server.
 */
public final class MultiplayerAimSync {
    private UUID target;
    private long firstObservedTick=Long.MIN_VALUE;
    private long lastSteerTick=Long.MIN_VALUE;
    public void reset(){target=null;firstObservedTick=Long.MIN_VALUE;lastSteerTick=Long.MIN_VALUE;}
    public void observe(UUID id,long tick,boolean steered){
        if(id==null){reset();return;}
        if(!id.equals(target)){target=id;firstObservedTick=tick;lastSteerTick=tick;}
        if(steered)lastSteerTick=tick;
    }
    public boolean ready(UUID id,long tick){
        if(id==null||!id.equals(target))return false;
        // A continuously moving airborne target can require a camera correction every tick.
        // Waiting two ticks after the LAST correction made release impossible.
        // Allow after two stable target-identity ticks; final rotation is sent immediately
        // before the release packet. This is still not an acknowledgement from a server.
        return firstObservedTick!=Long.MIN_VALUE && tick>=firstObservedTick+2;
    }
}
