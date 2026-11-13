package dev.wisteria.platform.game;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import dev.wisteria.render.BoxBatch;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws {@link BoxBatch}es from vertex buffers kept on the GPU: a batch is tessellated and uploaded once per
 * change, then each frame costs one draw call for its fills and one for its outlines. Uses the game's own
 * position-colour and line shaders, so it works on OpenGL and Vulkan alike. Render thread only.
 */
final class BoxBatchRenderer {
	/** Buffers of a batch that has not been drawn for this many frames are freed. */
	private static final int IDLE_FRAMES = 600;

	private static final RenderPipeline FILL = fill(false);
	private static final RenderPipeline FILL_THROUGH = fill(true);
	private static final RenderPipeline LINES = lines(false);
	private static final RenderPipeline LINES_THROUGH = lines(true);

	private final Map<BoxBatch, Uploaded> uploaded = new IdentityHashMap<>();
	private final List<BoxBatch> queued = new ArrayList<>();
	private RenderSystem.@Nullable AutoStorageIndexBuffer quadIndices;
	private long frame;

	private static RenderPipeline fill(boolean throughWalls) {
		return RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("wisteria", throughWalls ? "pipeline/batch_fill_through" : "pipeline/batch_fill"))
			.withDepthStencilState(new DepthStencilState(throughWalls ? CompareOp.ALWAYS_PASS : CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build();
	}

	private static RenderPipeline lines(boolean throughWalls) {
		return RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("wisteria", throughWalls ? "pipeline/batch_lines_through" : "pipeline/batch_lines"))
			.withDepthStencilState(new DepthStencilState(throughWalls ? CompareOp.ALWAYS_PASS : CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build();
	}

	/** From Render3D, during extraction: draw this batch in this frame's world pass. */
	void queue(BoxBatch batch) {
		if (batch.size() > 0 && !this.queued.contains(batch)) {
			this.queued.add(batch);
		}
	}

	/** At the end of the world's main pass. */
	void draw(Vec3 camera) {
		this.frame++;
		if (!this.queued.isEmpty()) {
			RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			Matrix4f view = RenderSystem.getModelViewMatrixCopy();
			for (BoxBatch batch : this.queued) {
				Uploaded buffers = this.upload(batch);
				buffers.lastDrawn = this.frame;
				Matrix4f modelView = new Matrix4f(view).translate(
					(float)(batch.originX() - camera.x), (float)(batch.originY() - camera.y), (float)(batch.originZ() - camera.z));
				GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
				this.drawPart(target, transforms, batch.throughWalls() ? FILL_THROUGH : FILL, buffers.fill, buffers.fillIndices, "fill");
				this.drawPart(target, transforms, batch.throughWalls() ? LINES_THROUGH : LINES, buffers.lines, buffers.lineIndices, "outline");
			}

			this.queued.clear();
		}

		this.freeIdle();
	}

	private void drawPart(RenderTarget target, GpuBufferSlice transforms, RenderPipeline pipeline, @Nullable GpuBuffer vertices, int indexCount, String what) {
		if (vertices == null || indexCount == 0) {
			return;
		}

		if (this.quadIndices == null) {
			this.quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		}

		GpuBuffer indices = this.quadIndices.getBuffer(indexCount);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
			.createRenderPass(() -> "Wisteria box batch " + what, target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices.slice());
			pass.setIndexBuffer(indices, this.quadIndices.type());
			pass.drawIndexed(indexCount, 1, 0, 0, 0);
		}
	}

	private Uploaded upload(BoxBatch batch) {
		Uploaded current = this.uploaded.get(batch);
		if (current != null && current.version == batch.version()) {
			return current;
		}

		if (current != null) {
			current.close();
		}

		Uploaded fresh = new Uploaded(batch.version());
		int fills = 0;
		int strokes = 0;
		for (int i = 0; i < batch.size(); i++) {
			fills += (batch.fill(i) >>> 24) != 0 ? 1 : 0;
			strokes += (batch.stroke(i) >>> 24) != 0 && batch.strokeWidth() > 0.0F ? 1 : 0;
		}

		if (fills > 0) {
			try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 24 * fills)) {
				BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
				for (int i = 0; i < batch.size(); i++) {
					if ((batch.fill(i) >>> 24) != 0) {
						fillBox(builder, batch, i);
					}
				}

				try (MeshData mesh = builder.buildOrThrow()) {
					fresh.fillIndices = mesh.drawState().indexCount();
					fresh.fill = RenderSystem.getDevice().createBuffer(() -> "Wisteria box batch fill", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
				}
			}
		}

		if (strokes > 0) {
			// The builder doubles every line vertex: 12 edges x 2 ends x 2.
			try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH.getVertexSize() * 48 * strokes)) {
				BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
				for (int i = 0; i < batch.size(); i++) {
					if ((batch.stroke(i) >>> 24) != 0 && batch.strokeWidth() > 0.0F) {
						outlineBox(builder, batch, i);
					}
				}

				try (MeshData mesh = builder.buildOrThrow()) {
					fresh.lineIndices = mesh.drawState().indexCount();
					fresh.lines = RenderSystem.getDevice().createBuffer(() -> "Wisteria box batch outline", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
				}
			}
		}

		this.uploaded.put(batch, fresh);
		return fresh;
	}

	private static void fillBox(BufferBuilder b, BoxBatch batch, int i) {
		float x0 = batch.bound(i, 0);
		float y0 = batch.bound(i, 1);
		float z0 = batch.bound(i, 2);
		float x1 = batch.bound(i, 3);
		float y1 = batch.bound(i, 4);
		float z1 = batch.bound(i, 5);
		int c = batch.fill(i);
		quad(b, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(b, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(b, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(b, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(b, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(b, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(BufferBuilder b, int c, float ax, float ay, float az, float bx, float by, float bz,
		float cx, float cy, float cz, float dx, float dy, float dz) {
		b.addVertex(ax, ay, az).setColor(c);
		b.addVertex(bx, by, bz).setColor(c);
		b.addVertex(cx, cy, cz).setColor(c);
		b.addVertex(dx, dy, dz).setColor(c);
	}

	private static void outlineBox(BufferBuilder b, BoxBatch batch, int i) {
		float x0 = batch.bound(i, 0);
		float y0 = batch.bound(i, 1);
		float z0 = batch.bound(i, 2);
		float x1 = batch.bound(i, 3);
		float y1 = batch.bound(i, 4);
		float z1 = batch.bound(i, 5);
		int c = batch.stroke(i);
		float w = batch.strokeWidth();
		// Four edges along each axis.
		for (int k = 0; k < 4; k++) {
			float y = (k & 1) == 0 ? y0 : y1;
			float z = (k & 2) == 0 ? z0 : z1;
			edge(b, c, w, x0, y, z, x1, y, z, 1.0F, 0.0F, 0.0F);
			float x = (k & 1) == 0 ? x0 : x1;
			edge(b, c, w, x, y0, z, x, y1, z, 0.0F, 1.0F, 0.0F);
			edge(b, c, w, x, y, z0, x, y, z1, 0.0F, 0.0F, 1.0F);
		}
	}

	private static void edge(BufferBuilder b, int c, float w, float ax, float ay, float az, float bx, float by, float bz, float nx, float ny, float nz) {
		b.addVertex(ax, ay, az).setColor(c).setNormal(nx, ny, nz).setLineWidth(w);
		b.addVertex(bx, by, bz).setColor(c).setNormal(nx, ny, nz).setLineWidth(w);
	}

	private void freeIdle() {
		Iterator<Uploaded> it = this.uploaded.values().iterator();
		while (it.hasNext()) {
			Uploaded buffers = it.next();
			if (this.frame - buffers.lastDrawn > IDLE_FRAMES) {
				buffers.close();
				it.remove();
			}
		}
	}

	private static final class Uploaded {
		final int version;
		@Nullable GpuBuffer fill;
		@Nullable GpuBuffer lines;
		int fillIndices;
		int lineIndices;
		long lastDrawn;

		Uploaded(int version) {
			this.version = version;
		}

		void close() {
			if (this.fill != null) {
				this.fill.close();
			}

			if (this.lines != null) {
				this.lines.close();
			}
		}
	}
}
