package dev.wisteria.modules.xray;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.jspecify.annotations.Nullable;

/**
 * Variant D of SPEC_PASSIVE: the game's own ore generation, run on the client from the world seed. The placed ore
 * features come from the vanilla worldgen registries bundled in the game; each one is seeded exactly as
 * {@code ChunkGenerator.applyBiomeDecoration} seeds it and placed with the game's own {@code OreFeature} into copies of
 * the chunks the client was sent, with every ore put back to the rock it replaced. Whatever ore ends up in the copies
 * is the prediction. Nothing here touches the network or the real world.
 *
 * <p>Not thread-bound, but not thread-safe either: one predictor per worker thread.
 */
final class OrePredictor {
	/** A dimension whose ores can be predicted. */
	enum Dimension {
		OVERWORLD(NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD),
		NETHER(NoiseGeneratorSettings.NETHER, MultiNoiseBiomeSourceParameterLists.NETHER);

		final ResourceKey<NoiseGeneratorSettings> noise;
		final ResourceKey<MultiNoiseBiomeSourceParameterList> biomes;

		Dimension(ResourceKey<NoiseGeneratorSettings> noise, ResourceKey<MultiNoiseBiomeSourceParameterList> biomes) {
			this.noise = noise;
			this.biomes = biomes;
		}
	}

	/** One ore the seed puts somewhere. */
	record Ore(int x, int y, int z, OreKind kind) {
	}

	/**
	 * What the client was sent of one chunk, ores already turned back to rock. {@code real} is false for a chunk the
	 * client never had: solid rock is assumed.
	 */
	record Base(int chunkX, int chunkZ, LevelChunkSection[] sections, int[] floor, boolean real) {
	}

	private record Feature(PlacedFeature placed, int step, int index) {
	}

	private static final Object LOOKUP_LOCK = new Object();
	private static volatile HolderLookup.@Nullable Provider lookup;

	private final Dimension dimension;
	private final long seed;
	private final int minY;
	private final int height;
	private final PalettedContainerFactory containers;
	private final NoiseBasedChunkGenerator generator;
	private final MultiNoiseBiomeSource biomeSource;
	private final RandomState randomState;
	private final HolderGetter<Biome> biomes;
	private final List<Feature> features = new ArrayList<>();
	private final Map<Block, OreKind> ores = new IdentityHashMap<>();
	private final Map<Block, BlockState> hosts = new IdentityHashMap<>();
	private final Map<ResourceKey<Biome>, Holder<Biome>> biomeByKey = new HashMap<>();
	private final Long2ObjectOpenHashMap<Holder<Biome>> noiseBiomes = new Long2ObjectOpenHashMap<>();
	private final Predicate<BlockState> isOre;

	/** The vanilla worldgen registries: built once, about a second, then shared. */
	static HolderLookup.Provider lookup() {
		HolderLookup.Provider built = lookup;
		if (built == null) {
			synchronized (LOOKUP_LOCK) {
				built = lookup;
				if (built == null) {
					built = VanillaRegistries.createLookup();
					lookup = built;
				}
			}
		}

		return built;
	}

	/**
	 * @param minY the dimension's lowest block, as the client knows it
	 * @param height the dimension's height in blocks
	 * @param containers the client level's palette factory, for building chunk sections
	 */
	OrePredictor(Dimension dimension, long seed, int minY, int height, PalettedContainerFactory containers) {
		this.dimension = dimension;
		this.seed = seed;
		this.minY = minY;
		this.height = height;
		this.containers = containers;

		HolderLookup.Provider registries = lookup();
		this.biomes = registries.lookupOrThrow(Registries.BIOME);
		Holder<MultiNoiseBiomeSourceParameterList> preset = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
			.getOrThrow(dimension.biomes);
		this.biomeSource = MultiNoiseBiomeSource.createFromPreset(preset);
		Holder<NoiseGeneratorSettings> noise = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(dimension.noise);
		this.generator = new NoiseBasedChunkGenerator(this.biomeSource, noise);
		this.randomState = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), seed);

		for (OreKind kind : OreKind.values()) {
			for (Block block : BuiltInRegistries.BLOCK) {
				String id = BuiltInRegistries.BLOCK.getKey(block).toString();
				if (OreKind.ofBlock(id) == kind) {
					this.ores.put(block, kind);
					Block host = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(OreKind.hostOf(id)));
					this.hosts.put(block, host.defaultBlockState());
				}
			}
		}

		this.isOre = state -> this.ores.containsKey(state.getBlock());

		// The same feature order the server builds: every biome the dimension can have, sorted step by step.
		List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(
			List.copyOf(this.biomeSource.possibleBiomes()), biome -> biome.value().getGenerationSettings().features(), true);
		for (int step = 0; step < steps.size(); step++) {
			FeatureSorter.StepFeatureData data = steps.get(step);
			for (PlacedFeature placed : data.features()) {
				if (this.makesOre(placed)) {
					this.features.add(new Feature(placed, step, data.indexMapping().applyAsInt(placed)));
				}
			}
		}
	}

	Dimension dimension() {
		return this.dimension;
	}

	long seed() {
		return this.seed;
	}

	/** How many placed features of the dimension make an ore we track. */
	int featureCount() {
		return this.features.size();
	}

	/** The ore a block is, or null. */
	@Nullable OreKind oreOf(Block block) {
		return this.ores.get(block);
	}

	private boolean makesOre(PlacedFeature placed) {
		ConfiguredFeature<?, ?> configured = placed.feature().value();
		if (!(configured.config() instanceof OreConfiguration ore)) {
			return false;
		}

		for (OreConfiguration.TargetBlockState target : ore.targetStates) {
			if (this.ores.containsKey(target.state.getBlock())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Copies what the client has of a chunk, on the thread that owns it: the sections only, cheap. The ores are put
	 * back to rock later, in {@link #prepare}.
	 *
	 * @param sections the chunk's sections, bottom up
	 * @param surface the highest non-air block + 1 for each column, x + z * 16
	 */
	static Base snapshot(int chunkX, int chunkZ, LevelChunkSection[] sections, int[] surface) {
		LevelChunkSection[] copies = new LevelChunkSection[sections.length];
		for (int i = 0; i < sections.length; i++) {
			copies[i] = sections[i].copy();
		}

		return new Base(chunkX, chunkZ, copies, surface.clone(), true);
	}

	/** Turns a snapshot into the world generation saw: ores back to their rock, the ocean floor found. Worker thread. */
	Base prepare(Base snapshot) {
		for (LevelChunkSection section : snapshot.sections()) {
			if (!section.hasOnlyAir() && section.maybeHas(this.isOre)) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							BlockState host = this.hosts.get(section.getBlockState(x, y, z).getBlock());
							if (host != null) {
								section.setBlockState(x, y, z, host, false);
							}
						}
					}
				}
			}
		}

		// The ocean-floor heightmap of generation: the top block that blocks motion, below what the client calls the surface.
		int[] floor = new int[256];
		for (int column = 0; column < 256; column++) {
			int x = column & 15;
			int z = column >> 4;
			int y = Math.min(snapshot.floor()[column], this.minY + this.height) - 1;
			while (y >= this.minY) {
				LevelChunkSection section = snapshot.sections()[(y - this.minY) >> 4];
				BlockState state = section.getBlockState(x, y & 15, z);
				if (state.blocksMotion() && !laterDecoration(state)) {
					break;
				}

				y--;
			}

			floor[column] = y + 1;
		}

		return new Base(snapshot.chunkX(), snapshot.chunkZ(), snapshot.sections(), floor, true);
	}

	/**
	 * Blocks that come after the ores in generation (trees, huge mushrooms, plants, snow): the ocean-floor heightmap
	 * the ores were placed against did not have them yet.
	 */
	private static boolean laterDecoration(BlockState state) {
		return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK)
			|| state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.BEE_NEST) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
			|| state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.ICE)
			|| state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.MOSS_CARPET);
	}

	/** A chunk the client does not have: solid rock all the way up, deepslate below 0 in the overworld. */
	Base solid(int chunkX, int chunkZ) {
		int count = this.height >> 4;
		LevelChunkSection[] sections = new LevelChunkSection[count];
		BlockState stone = this.dimension == Dimension.NETHER ? Blocks.NETHERRACK.defaultBlockState() : Blocks.STONE.defaultBlockState();
		BlockState deep = this.dimension == Dimension.NETHER ? stone : Blocks.DEEPSLATE.defaultBlockState();
		for (int i = 0; i < count; i++) {
			int bottom = this.minY + i * 16;
			BlockState fill = bottom < 0 ? deep : stone;
			PalettedContainer<BlockState> states = new PalettedContainer<>(fill, this.containers.blockStatesStrategy());
			sections[i] = new LevelChunkSection(states, this.containers.createForBiomes());
		}

		int[] floor = new int[256];
		java.util.Arrays.fill(floor, this.minY + this.height);
		return new Base(chunkX, chunkZ, sections, floor, false);
	}

	/** Where the bases for a 3×3 around a chunk come from. */
	interface Bases {
		Base base(int chunkX, int chunkZ);
	}

	/**
	 * What decorating one chunk gave: every ore its features placed, wherever in the 3×3 it landed, and which of the
	 * chunks it read were assumed solid because the client did not have them (packed chunk positions).
	 */
	record Decoration(List<Ore> ores, List<Long> assumed) {
	}

	/**
	 * Decorates one chunk the way the server did: every ore feature of the dimension, in the server's order and with
	 * its seeds, into copies of the 3×3 chunks around it (features reach into the neighbours).
	 */
	Decoration decorate(int chunkX, int chunkZ, Bases bases) {
		Sim sim = new Sim(chunkX, chunkZ, bases);
		WorldGenLevel level = sim.level();
		WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
		BlockPos origin = SectionPos.of(new ChunkPos(chunkX, chunkZ), SectionPos.blockToSectionCoord(this.minY)).origin();
		long decorationSeed = random.setDecorationSeed(this.seed, origin.getX(), origin.getZ());
		for (Feature feature : this.features) {
			random.setFeatureSeed(decorationSeed, feature.index(), feature.step());
			feature.placed().placeWithBiomeCheck(level, this.generator, random, origin);
		}

		List<Long> assumed = new ArrayList<>();
		sim.used.forEach((key, base) -> {
			if (!base.real()) {
				assumed.add(key);
			}
		});
		return new Decoration(sim.ores(), assumed);
	}

	/** The biome generation saw at a quart position: the client's if it has the chunk, else from the seed's noise. */
	private Holder<Biome> noiseBiome(Sim sim, int quartX, int quartY, int quartZ) {
		Base base = sim.baseOrNull(quartX >> 2, quartZ >> 2);
		if (base != null && base.real()) {
			int blockY = quartY << 2;
			int index = Math.clamp((blockY - this.minY) >> 4, 0, base.sections().length - 1);
			int localY = Math.clamp((blockY - this.minY - index * 16) >> 2, 0, 3);
			Holder<Biome> client = base.sections()[index].getNoiseBiome(quartX & 3, localY, quartZ & 3);
			Optional<ResourceKey<Biome>> key = client.unwrapKey();
			if (key.isPresent()) {
				Holder<Biome> mapped = this.biomeByKey.computeIfAbsent(key.get(), k -> this.biomes.get(k).map(h -> (Holder<Biome>) h).orElse(null));
				if (mapped != null) {
					return mapped;
				}
			}
		}

		long packed = (((long) quartX & 0x3FFFFFL) << 42) | (((long) quartZ & 0x3FFFFFL) << 20) | ((long) quartY & 0xFFFFFL);
		Holder<Biome> cached = this.noiseBiomes.get(packed);
		if (cached == null) {
			if (this.noiseBiomes.size() > 200_000) {
				this.noiseBiomes.clear();
			}

			cached = this.biomeSource.getNoiseBiome(quartX, quartY, quartZ, this.randomState.sampler());
			this.noiseBiomes.put(packed, cached);
		}

		return cached;
	}

	/** One decoration: copies of the 3×3 chunks and a level that reads and writes them. */
	private final class Sim implements InvocationHandler {
		private final int centreX;
		private final int centreZ;
		private final Bases bases;
		private final Map<Long, Base> used = new HashMap<>();
		private final Map<Long, ProtoChunk> chunks = new HashMap<>();
		/** Every spot an ore feature asked to write: the only places an ore can have appeared. */
		private final it.unimi.dsi.fastutil.longs.LongOpenHashSet candidates = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		private final LevelHeightAccessor heights = LevelHeightAccessor.create(OrePredictor.this.minY, OrePredictor.this.height);
		private final BiomeManager biomeManager;

		Sim(int centreX, int centreZ, Bases bases) {
			this.centreX = centreX;
			this.centreZ = centreZ;
			this.bases = bases;
			this.biomeManager = new BiomeManager((x, y, z) -> OrePredictor.this.noiseBiome(this, x, y, z),
				BiomeManager.obfuscateSeed(OrePredictor.this.seed));
		}

		WorldGenLevel level() {
			return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class}, this);
		}

		@Nullable Base baseOrNull(int chunkX, int chunkZ) {
			return this.used.get(ChunkPos.pack(chunkX, chunkZ));
		}

		private Base base(int chunkX, int chunkZ) {
			return this.used.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), key -> this.bases.base(chunkX, chunkZ));
		}

		ProtoChunk chunk(int chunkX, int chunkZ) {
			return this.chunks.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), key -> {
				Base base = this.base(chunkX, chunkZ);
				LevelChunkSection[] copies = new LevelChunkSection[base.sections().length];
				for (int i = 0; i < copies.length; i++) {
					copies[i] = base.sections()[i].copy();
				}

				return new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, copies, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(),
					this.heights, OrePredictor.this.containers, null);
			});
		}

		private BlockState blockAt(BlockPos pos) {
			if (pos.getY() < OrePredictor.this.minY || pos.getY() >= OrePredictor.this.minY + OrePredictor.this.height) {
				return Blocks.AIR.defaultBlockState();
			}

			ProtoChunk chunk = this.chunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
			LevelChunkSection section = chunk.getSection((pos.getY() - OrePredictor.this.minY) >> 4);
			return section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
		}

		private void setBlock(BlockPos pos, BlockState state) {
			if (pos.getY() < OrePredictor.this.minY || pos.getY() >= OrePredictor.this.minY + OrePredictor.this.height) {
				return;
			}

			ProtoChunk chunk = this.chunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
			chunk.getSection((pos.getY() - OrePredictor.this.minY) >> 4).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
		}

		/** Every ore now at a spot a feature wrote to. Bases hold none, so each one was placed by this decoration. */
		List<Ore> ores() {
			List<Ore> found = new ArrayList<>();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			it.unimi.dsi.fastutil.longs.LongIterator iterator = this.candidates.iterator();
			while (iterator.hasNext()) {
				long packed = iterator.nextLong();
				pos.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
				OreKind kind = OrePredictor.this.ores.get(this.blockAt(pos).getBlock());
				if (kind != null) {
					found.add(new Ore(pos.getX(), pos.getY(), pos.getZ(), kind));
				}
			}

			return found;
		}

		/** The 3×3 around the decorated chunk, as the server's generation region allows. */
		private boolean writable(BlockPos pos) {
			return Math.abs(SectionPos.blockToSectionCoord(pos.getX()) - this.centreX) <= 1
				&& Math.abs(SectionPos.blockToSectionCoord(pos.getZ()) - this.centreZ) <= 1;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			int count = args == null ? 0 : args.length;
			switch (method.getName()) {
				case "getChunk":
					if (count >= 2 && args[0] instanceof Integer x && args[1] instanceof Integer z) {
						return this.chunk(x, z);
					}

					if (count == 1 && args[0] instanceof BlockPos pos) {
						return this.chunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
					}

					break;
				case "getBlockState":
					return this.blockAt((BlockPos) args[0]);
				case "getFluidState":
					return this.blockAt((BlockPos) args[0]).getFluidState();
				case "isStateAtPosition":
					@SuppressWarnings("unchecked")
					Predicate<BlockState> test = (Predicate<BlockState>) args[1];
					return test.test(this.blockAt((BlockPos) args[0]));
				case "setBlock":
					BlockPos target = (BlockPos) args[0];
					if (!this.writable(target)) {
						return false;
					}

					this.candidates.add(target.asLong());
					this.setBlock(target, (BlockState) args[1]);
					return true;
				case "getBiome":
					return this.biomeManager.getBiome((BlockPos) args[0]);
				case "getBiomeManager":
					return this.biomeManager;
				case "getNoiseBiome":
					return OrePredictor.this.noiseBiome(this, (Integer) args[0], (Integer) args[1], (Integer) args[2]);
				case "getMinY":
					return OrePredictor.this.minY;
				case "getHeight":
					if (count == 0) {
						return OrePredictor.this.height;
					}

					if (count == 3 && args[0] instanceof Heightmap.Types) {
						int x = (Integer) args[1];
						int z = (Integer) args[2];
						Base base = this.base(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
						return base.floor()[(x & 15) + (z & 15) * 16];
					}

					break;
				case "getSeed":
					return OrePredictor.this.seed;
				case "ensureCanWrite":
					// OreFeature asks this for every block it is about to try: remember them all.
					BlockPos pos = (BlockPos) args[0];
					if (this.writable(pos)) {
						this.candidates.add(pos.asLong());
						return true;
					}

					return false;
				case "setCurrentlyGenerating":
					return null;
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "XraySim[" + this.centreX + ", " + this.centreZ + "]";
				default:
					if (method.isDefault()) {
						return InvocationHandler.invokeDefault(proxy, method, args);
					}
			}

			throw new UnsupportedOperationException("Xray Predicted: the ore simulation does not provide " + method);
		}
	}
}
