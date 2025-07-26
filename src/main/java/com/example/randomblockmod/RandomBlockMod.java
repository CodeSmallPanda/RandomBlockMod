package com.example.randomblockmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class RandomBlockMod implements ModInitializer {

	// 不能作为随机方块的列表 - 包括各种地毯、按钮、重力方块等
	private static final Set<Identifier> EXCLUDED_BLOCKS = new HashSet<>();

	// 不能被随机方块替换的保留方块列表
	private static final Set<Identifier> PRESERVED_BLOCKS = new HashSet<>();

	// 已处理区块的缓存（玩家位置）
	private static final Map<ChunkPos, Set<UUID>> processedChunks = new ConcurrentHashMap<>();

	// 待处理的区块任务队列
	private static final Queue<ChunkTask> chunkTaskQueue = new ConcurrentLinkedQueue<>();

	// 异步任务线程池
	private static final ExecutorService executor = Executors.newFixedThreadPool(2);

	@Override
	public void onInitialize() {
		// 初始化排除的方块列表
		initExcludedBlocks();

		// 初始化保留的方块列表
		initPreservedBlocks();

		// 玩家加入游戏时清空他们的位置缓存
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			UUID playerId = handler.getPlayer().getUuid();
			processedChunks.values().forEach(set -> set.remove(playerId));
		});

		// 服务器每tick处理区块队列
		ServerTickEvents.START_WORLD_TICK.register(world -> {
			if (!(world instanceof ServerWorld)) return;

			if (!chunkTaskQueue.isEmpty()) {
				ChunkTask task = chunkTaskQueue.poll();
				if (task != null) {
					processChunk(task.chunkPos, task.world, task.randomBlock);
				}
			}
		});

		// 主要玩家检测逻辑
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			server.getPlayerManager().getPlayerList().forEach(player -> {
				if (player.isSpectator() || player.isCreative() || player.getVehicle() != null)
					return;

				World world = player.getWorld();

				// 计算玩家当前区块位置
				int chunkX = (int) player.getX() >> 4;
				int chunkZ = (int) player.getZ() >> 4;
				ChunkPos currentChunk = new ChunkPos(chunkX, chunkZ);

				// 检查玩家是否已处理过此区块
				Set<UUID> playersInChunk = processedChunks.computeIfAbsent(
						currentChunk, k -> ConcurrentHashMap.newKeySet());

				if (!playersInChunk.contains(player.getUuid())) {
					playersInChunk.add(player.getUuid());
					processNewChunk(currentChunk, (ServerWorld) world);
				}
			});
		});
	}

	private void initExcludedBlocks() {
		// 手动添加重力方块（沙子、沙砾）
		// 手动添加重力方块（沙子、沙砾）
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SAND));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_SAND));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAVEL));

		// 手动添加花朵
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DANDELION));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POPPY));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_ORCHID));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ALLIUM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.AZURE_BLUET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_TULIP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_TULIP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_TULIP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_TULIP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OXEYE_DAISY));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CORNFLOWER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LILY_OF_THE_VALLEY));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WITHER_ROSE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SUNFLOWER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LILAC));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ROSE_BUSH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PEONY));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TORCHFLOWER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPORE_BLOSSOM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.AZALEA));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_PETALS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHORUS_FLOWER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHORUS_PLANT));

		// 添加特殊排除方块
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.COBWEB)); // 蜘蛛网
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TRIPWIRE)); // 拌线
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TRIPWIRE_HOOK)); // 拌线钩
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SCAFFOLDING));//脚手架
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO));//竹子
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIG_DRIPLEAF_STEM));//垂滴叶
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIG_DRIPLEAF));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SMALL_DRIPLEAF));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.KELP));//海草
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.KELP_PLANT));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PITCHER_PLANT));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ATTACHED_PUMPKIN_STEM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ATTACHED_MELON_STEM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SCULK_VEIN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SCULK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SCULK_CATALYST));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SCULK_SENSOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SCULK_SHRIEKER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CALIBRATED_SCULK_SENSOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WATER));//水
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PIGLIN_HEAD));//活塞头
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MOVING_PISTON));//移动活塞
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SOUL_CAMPFIRE));//灵魂篝火
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CAMPFIRE));//篝火
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TORCHFLOWER_CROP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FIRE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SOUL_FIRE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FROGSPAWN));//蛙卵
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.STRUCTURE_VOID));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CONDUIT));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.NETHER_BRICK_FENCE));


		// 添加各种颜色地毯
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MAGENTA_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_BLUE_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.YELLOW_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIME_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAY_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_GRAY_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CYAN_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PURPLE_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GREEN_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACK_CARPET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MOSS_CARPET));

		// 添加各种按钮
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.STONE_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_BUTTON));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POLISHED_BLACKSTONE_BUTTON));

		// 添加各种花盆
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FLOWER_POT));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_DANDELION));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_POPPY));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_BLUE_ORCHID));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_ALLIUM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_AZURE_BLUET));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_RED_TULIP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_ORANGE_TULIP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_WHITE_TULIP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_PINK_TULIP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_OXEYE_DAISY));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_CORNFLOWER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_LILY_OF_THE_VALLEY));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_WITHER_ROSE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_BAMBOO));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_CACTUS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_ACACIA_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_BIRCH_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_CHERRY_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_JUNGLE_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_MANGROVE_PROPAGULE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_OAK_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_DARK_OAK_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_SPRUCE_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_FERN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_AZALEA_BUSH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_FLOWERING_AZALEA_BUSH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_DEAD_BUSH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_RED_MUSHROOM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_BROWN_MUSHROOM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_CRIMSON_FUNGUS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_CRIMSON_ROOTS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_WARPED_FUNGUS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTTED_WARPED_ROOTS));

		// 添加各种告示牌
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MANGROVE_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_WALL_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MANGROVE_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_WALL_HANGING_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_WALL_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_WALL_SIGN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MANGROVE_HANGING_SIGN));

		// 添加各种火把
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TORCH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WALL_TORCH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.REDSTONE_TORCH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.REDSTONE_WALL_TORCH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SOUL_TORCH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SOUL_WALL_TORCH));

		// 添加各种铁轨
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RAIL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POWERED_RAIL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DETECTOR_RAIL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACTIVATOR_RAIL));

		// 添加龙蛋、龙头
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DRAGON_EGG));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DRAGON_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DRAGON_WALL_HEAD));

		// 添加各种树苗
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_SAPLING));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MANGROVE_PROPAGULE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_SAPLING));

		// 添加各种压力板
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.STONE_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MANGROVE_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_PRESSURE_PLATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_PRESSURE_PLATE));

		// 添加各种形态的红石
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.REDSTONE_WIRE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.REDSTONE_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.REDSTONE_LAMP));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.REDSTONE_ORE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEEPSLATE_REDSTONE_ORE));

		// 添加红石中继器和比较器
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.REPEATER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.COMPARATOR));

		// 添加各种门
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MANGROVE_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_DOOR));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.IRON_DOOR));

		// 添加各种头颅
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SKELETON_SKULL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SKELETON_WALL_SKULL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WITHER_SKELETON_SKULL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WITHER_SKELETON_WALL_SKULL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ZOMBIE_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ZOMBIE_WALL_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CREEPER_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CREEPER_WALL_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DRAGON_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DRAGON_WALL_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PIGLIN_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PIGLIN_WALL_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PLAYER_HEAD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PLAYER_WALL_HEAD));

		// 添加各种蜡烛
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MAGENTA_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_BLUE_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.YELLOW_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIME_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAY_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_GRAY_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CYAN_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PURPLE_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GREEN_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_CANDLE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACK_CANDLE));

		// 添加拉杆
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LEVER));

		// 紫水晶簇（各种形态）
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.AMETHYST_CLUSTER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LARGE_AMETHYST_BUD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MEDIUM_AMETHYST_BUD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SMALL_AMETHYST_BUD));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BUDDING_AMETHYST));

		// 栅栏（所有类型）
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MANGROVE_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_FENCE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_FENCE));

		// 栅栏门（所有类型）
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.OAK_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPRUCE_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BIRCH_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.JUNGLE_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ACACIA_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DARK_OAK_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MANGROVE_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO_FENCE_GATE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHERRY_FENCE_GATE));

		// 钟
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BELL));

		// 添加各种旗帜
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MAGENTA_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_BLUE_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.YELLOW_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIME_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAY_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_GRAY_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CYAN_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PURPLE_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GREEN_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACK_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MAGENTA_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_BLUE_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.YELLOW_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIME_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAY_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_GRAY_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CYAN_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PURPLE_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GREEN_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_WALL_BANNER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACK_WALL_BANNER));

		// 添加睡莲
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LILY_PAD));

		// 添加滴水石锥
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POINTED_DRIPSTONE));

		// 添加各种石墙
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.COBBLESTONE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MOSSY_COBBLESTONE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BRICK_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PRISMARINE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_SANDSTONE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MOSSY_STONE_BRICK_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRANITE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.STONE_BRICK_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.NETHER_BRICK_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ANDESITE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_NETHER_BRICK_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SANDSTONE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.END_STONE_BRICK_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DIORITE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACKSTONE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POLISHED_BLACKSTONE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.COBBLED_DEEPSLATE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POLISHED_DEEPSLATE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEEPSLATE_BRICK_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEEPSLATE_TILE_WALL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MUD_BRICK_WALL));

		//带蜡烛的蛋糕
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MAGENTA_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_BLUE_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.YELLOW_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIME_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAY_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_GRAY_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CYAN_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PURPLE_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GREEN_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_CANDLE_CAKE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACK_CANDLE_CAKE));

		// 添加铁砧
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ANVIL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHIPPED_ANVIL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DAMAGED_ANVIL));

		// 添加梯子
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LADDER));

		// 添加藤蔓
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.VINE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CAVE_VINES));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CAVE_VINES_PLANT));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WEEPING_VINES));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WEEPING_VINES_PLANT));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TWISTING_VINES));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TWISTING_VINES_PLANT));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GLOW_LICHEN));

		// 添加各种草
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRASS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TALL_GRASS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FERN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LARGE_FERN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BUSH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SEAGRASS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TALL_SEAGRASS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.NETHER_SPROUTS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_ROOTS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_ROOTS));

		// 添加床
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MAGENTA_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_BLUE_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.YELLOW_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIME_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAY_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_GRAY_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CYAN_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PURPLE_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GREEN_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_BED));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACK_BED));

		// 添加珊瑚相关方块
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TUBE_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BRAIN_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BUBBLE_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FIRE_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.HORN_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_TUBE_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BRAIN_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BUBBLE_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_FIRE_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_HORN_CORAL));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TUBE_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BRAIN_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BUBBLE_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FIRE_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.HORN_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_TUBE_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BRAIN_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BUBBLE_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_FIRE_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_HORN_CORAL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TUBE_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BRAIN_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BUBBLE_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FIRE_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.HORN_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_TUBE_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BRAIN_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BUBBLE_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_FIRE_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_HORN_CORAL_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_TUBE_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BRAIN_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_BUBBLE_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_FIRE_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.DEAD_HORN_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TUBE_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BRAIN_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BUBBLE_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FIRE_CORAL_WALL_FAN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.HORN_CORAL_WALL_FAN));

		//各种蘑菇
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_MUSHROOM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_MUSHROOM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CRIMSON_FUNGUS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WARPED_FUNGUS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_MUSHROOM_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_MUSHROOM_BLOCK));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MUSHROOM_STEM));

		//悬挂类装饰方块
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LANTERN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SOUL_LANTERN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHAIN));

		// 添加各种潜影盒
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MAGENTA_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_BLUE_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.YELLOW_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIME_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAY_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_GRAY_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CYAN_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PURPLE_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GREEN_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_SHULKER_BOX));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACK_SHULKER_BOX));

		// 新增混凝土沙子（16种颜色）
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHITE_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ORANGE_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MAGENTA_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_BLUE_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.YELLOW_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIME_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PINK_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GRAY_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LIGHT_GRAY_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CYAN_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PURPLE_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLUE_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BROWN_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.GREEN_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.RED_CONCRETE_POWDER));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BLACK_CONCRETE_POWDER));

		//各种农作物
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WHEAT));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CARROTS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POTATOES));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BEETROOTS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.MELON_STEM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.PUMPKIN_STEM));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.COCOA));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SUGAR_CANE));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BAMBOO));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CACTUS));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.NETHER_WART));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SWEET_BERRY_BUSH));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CAVE_VINES));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CAVE_VINES_PLANT));

		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SNOW));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BUBBLE_COLUMN));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SNIFFER_EGG));
		EXCLUDED_BLOCKS.add(Registries.BLOCK.getId(Blocks.POWDER_SNOW));



	}

	private void initPreservedBlocks() {
		// 保留空气类方块
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.AIR));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CAVE_AIR));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.VOID_AIR));

		// 保留液体方块
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.WATER));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.LAVA));

		// 保留特殊功能方块
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.END_PORTAL_FRAME));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.CHEST));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ENDER_CHEST));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.TRAPPED_CHEST));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.SPAWNER)); // 刷怪笼
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ENCHANTING_TABLE));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BEACON));
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.ANVIL));

		// 基岩层需要保留
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.BEDROCK));

		// 保留各种花盆
		PRESERVED_BLOCKS.add(Registries.BLOCK.getId(Blocks.FLOWER_POT));
		PRESERVED_BLOCKS.addAll(EXCLUDED_BLOCKS.stream()
				.filter(id -> id.getNamespace().equals("minecraft") && id.getPath().startsWith("potted_"))
				.collect(Collectors.toList()));

		// 保留各种告示牌
		PRESERVED_BLOCKS.addAll(EXCLUDED_BLOCKS.stream()
				.filter(id -> id.getNamespace().equals("minecraft") &&
						(id.getPath().endsWith("_sign") || id.getPath().endsWith("_hanging_sign")))
				.collect(Collectors.toList()));

		// 保留各种火把
		PRESERVED_BLOCKS.addAll(EXCLUDED_BLOCKS.stream()
				.filter(id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith("_torch"))
				.collect(Collectors.toList()));

		// 保留各种铁轨
		PRESERVED_BLOCKS.addAll(EXCLUDED_BLOCKS.stream()
				.filter(id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith("_rail"))
				.collect(Collectors.toList()));
	}

	private void processNewChunk(ChunkPos chunkPos, ServerWorld world) {
		// 使用更稳定的方式获取区块
		Chunk chunk = world.getChunkManager().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
		if (chunk == null) return;

		executor.execute(() -> {
			Block randomBlock = getRandomBlock(world);
			if (randomBlock != null) {
				Identifier blockId = Registries.BLOCK.getId(randomBlock);
				world.getServer().sendMessage(net.minecraft.text.Text.literal(
						"正在处理区块 [" + chunkPos.x + ", " + chunkPos.z + "]，使用方块: " + blockId
				));
				chunkTaskQueue.add(new ChunkTask(chunkPos, world, randomBlock));
			}
		});
	}

	private Block getRandomBlock(ServerWorld world) {
		List<Block> allowedBlocks = new ArrayList<>();

		for (Block block : Registries.BLOCK) {
			Identifier id = Registries.BLOCK.getId(block);

			// 跳过排除方块和保留方块
			if (!EXCLUDED_BLOCKS.contains(id) && !PRESERVED_BLOCKS.contains(id)) {
				allowedBlocks.add(block);
			}
		}

		if (allowedBlocks.isEmpty()) {
			return null;
		}

		// 随机选择一个方块用于整个区块
		return allowedBlocks.get(world.getRandom().nextInt(allowedBlocks.size()));
	}

	private void processChunk(ChunkPos chunkPos, ServerWorld world, Block randomBlock) {
		// 获取区块边界
		int startX = chunkPos.getStartX();
		int startZ = chunkPos.getStartZ();
		int endX = chunkPos.getEndX();
		int endZ = chunkPos.getEndZ();
		int blocksReplaced = 0;

		// 遍历区块内每个位置
		for (int x = startX; x <= endX; x++) {
			for (int z = startZ; z <= endZ; z++) {
				for (int y = world.getBottomY(); y < world.getTopY(); y++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState currentState = world.getBlockState(pos);
					Block currentBlock = currentState.getBlock();
					Identifier id = Registries.BLOCK.getId(currentBlock);

					// 检查是否为保留方块（箱子、空气、液体等）
					if (PRESERVED_BLOCKS.contains(id)) {
						continue;
					}

					// 更新方块状态
					world.setBlockState(pos, randomBlock.getDefaultState(), Block.NOTIFY_LISTENERS);
					blocksReplaced++;
				}
			}

			// 每列处理完添加短暂延迟（减少服务器卡顿）
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// 处理完成后输出统计信息
		Identifier blockId = Registries.BLOCK.getId(randomBlock);
		world.getServer().sendMessage(net.minecraft.text.Text.literal(
				"区块 [" + chunkPos.x + ", " + chunkPos.z + "] 处理完成，" +
						"替换方块数: " + blocksReplaced +
						"，使用的方块: " + blockId
		));
	}

	private static class ChunkTask {
		final ChunkPos chunkPos;
		final ServerWorld world;
		final Block randomBlock;

		ChunkTask(ChunkPos chunkPos, ServerWorld world, Block randomBlock) {
			this.chunkPos = chunkPos;
			this.world = world;
			this.randomBlock = randomBlock;
		}
	}
}
