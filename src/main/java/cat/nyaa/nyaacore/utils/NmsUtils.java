package cat.nyaa.nyaacore.utils;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.critereon.NbtPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A collection of operations that cannot be done with NMS.
 * Downstream plugin authors can add methods here, so that
 * their plugins do not need to depend on NMS for just a
 * single function. It also makes upgrade a bit easier,
 * since all NMS codes are here.
 */
public final class NmsUtils {
    /* see CommandEntityData.java */
    public static void setEntityTag(Entity e, String tag) {
        net.minecraft.world.entity.Entity nmsEntity = getHandle(e);

        if (nmsEntity instanceof Player) {
            throw new IllegalArgumentException("Player NBT cannot be edited");
        } else {
            CompoundTag nbtToBeMerged;

            try {
                nbtToBeMerged = TagParser.parseTag(tag);
            } catch (CommandSyntaxException ex) {
                throw new IllegalArgumentException("Invalid NBTTag string");
            }

            CompoundTag nmsOrigNBT = NbtPredicate.getEntityTagToCompare(nmsEntity); // entity to nbt
            CompoundTag nmsClonedNBT = nmsOrigNBT.copy(); // clone
            nmsClonedNBT.merge(nbtToBeMerged); // merge NBT
            if (nmsClonedNBT.equals(nmsOrigNBT)) {
            } else {
                UUID uuid = nmsEntity.getUUID(); // store UUID
                nmsEntity.load(nmsClonedNBT); // set nbt
                nmsEntity.setUUID(uuid); // set uuid
            }
        }
    }

    public static boolean createExplosion(World world, Entity entity, double x, double y, double z, float power, boolean setFire, boolean breakBlocks) {
        return !getHandle(world).explode(getHandle(entity), x, y, z, power, setFire, breakBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE).wasCanceled;
    }

    /**
     * fromMobSpawner is removed in 1.15.2 Spigot
     * use {Mob.isAware} instead.
     */
    @Deprecated
    public static boolean isFromMobSpawner(Entity entity) {
        return false;
    }

    /**
     * fromMobSpawner is removed in 1.15.2 Spigot
     * use {Mob.isAware} instead.
     */
    @Deprecated
    public static void setFromMobSpawner(Entity entity, boolean fromMobSpawner) {
//        if (entity instanceof CraftEntity) {
//            ((CraftEntity) entity).getHandle().fromMobSpawner = fromMobSpawner;
//        }
    }

    /**
     * Update the yaw &amp; pitch of entities. Can be used to set head orientation.
     *
     * @param entity   the living entity
     * @param newYaw   can be null if not to be modified
     * @param newPitch can be null if not to be modified
     */
    public static void updateEntityYawPitch(LivingEntity entity, Float newYaw, Float newPitch) {
        if (entity == null) throw new IllegalArgumentException();
        if (newYaw == null && newPitch == null) return;
        net.minecraft.world.entity.Entity nmsEntity = getHandle(entity);
        if (newYaw != null) {
            nmsEntity.setYRot(newYaw);
        }

        if (newPitch != null) {
            nmsEntity.setXRot(newPitch);
        }
    }

    /**
     * Set "OnGround" flag for an entity
     *
     * @param e          the entity
     * @param isOnGround new OnGround value
     */
    public static void setEntityOnGround(Entity e, boolean isOnGround) {
        if (e == null) throw new IllegalArgumentException();
        getHandle(e).setOnGround(isOnGround); //nms method renamed
    }

    public static List<Block> getTileEntities(World world) {
        Map<BlockPos, BlockEntity> BlockEntityList = getHandle(world).capturedTileEntities;
        // Safe to parallelize getPosition and getBlockAt
        return BlockEntityList.entrySet().stream().parallel().map(Map.Entry::getKey).map(p -> world.getBlockAt(p.getX(), p.getY(), p.getZ())).collect(Collectors.toList());
    }

    public static List<BlockState> getBlockEntityBlockStates(World world) {
        Map<BlockPos, BlockEntity> BlockEntityList = getHandle(world).capturedTileEntities;
        // Safe to parallelize getPosition and getBlockAt
        return BlockEntityList.entrySet().stream().parallel().map(Map.Entry::getKey).map(p -> world.getBlockAt(p.getX(), p.getY(), p.getZ())).map(Block::getState).collect(Collectors.toList());
    }

    private final static Class<?> craftItemStackClazz;
    private final static Class<?> craftEntityClazz;
    private final static Class<?> craftWorldClazz;

    private final static Method getHandleCraftEntity;
    private final static Method getHandleCraftWorld;
    private final static Method asNMSCopyCraftItemStack;
    private final static Method asBukkitCopyCraftItemStack;

    static {
        try {
            craftItemStackClazz = Class.forName("org.bukkit.craftbukkit." + getNMSVersion() + ".inventory.CraftItemStack");
            asBukkitCopyCraftItemStack = craftItemStackClazz.getMethod("asBukkitCopy", net.minecraft.world.item.ItemStack.class);
            asNMSCopyCraftItemStack = craftItemStackClazz.getMethod("asNMSCopy", ItemStack.class);

            craftEntityClazz = Class.forName("org.bukkit.craftbukkit." + getNMSVersion() + ".entity.CraftEntity");
            getHandleCraftEntity = craftEntityClazz.getMethod("getHandle");

            craftWorldClazz = Class.forName("org.bukkit.craftbukkit." + getNMSVersion() + ".CraftWorld");
            getHandleCraftWorld = craftWorldClazz.getMethod("getHandle");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static net.minecraft.world.item.ItemStack asNMSCopy(ItemStack itemStack) {
        try {
            return (net.minecraft.world.item.ItemStack) asNMSCopyCraftItemStack.invoke(null, itemStack);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static ItemStack asBukkitCopy(net.minecraft.world.item.ItemStack itemStack) {
        try {
            return (ItemStack) asBukkitCopyCraftItemStack.invoke(null, itemStack);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public final static String nmsVersion = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

    public static String getNMSVersion() {
        return nmsVersion;
    }

    public static net.minecraft.world.entity.Entity getHandle(Entity entity) {
        try {
            return (net.minecraft.world.entity.Entity) getHandleCraftEntity.invoke(entity);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static ServerLevel getHandle(World world) {
        try {
            return (ServerLevel) getHandleCraftWorld.invoke(world);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> getCraftItemStackClass() {
        return craftItemStackClazz;
    }

    public static Class<?> getCraftEntityClass() {
        return craftEntityClazz;
    }
}
