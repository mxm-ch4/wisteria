package dev.wisteria.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The COM plumbing of the in-process media source, on any system: fake COM objects built in native memory whose vtable
 * slots are Java upcalls. What Windows itself answers is the owner's live check (docs/media.md).
 */
class WinRtTest {
	private static final Linker LINKER = Linker.nativeLinker();
	private static final FunctionDescriptor THIS_ONLY = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final FunctionDescriptor THIS_OUT = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final FunctionDescriptor THIS_IN_OUT = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS);

	// State the fake objects' methods share; one test at a time touches it.
	static final AtomicInteger RELEASES = new AtomicInteger();
	static final AtomicInteger STATUS_CALLS = new AtomicInteger();
	static MemorySegment asyncInfo = MemorySegment.NULL;
	static MemorySegment result = MemorySegment.NULL;
	static int completeAfter;

	// ----- Methods the fake vtables point at -----

	static int queryInterface(MemorySegment self, MemorySegment iid, MemorySegment out) {
		byte[] wanted = WinRt.guidBytes(WinRt.IID_IASYNC_INFO);
		byte[] asked = iid.reinterpret(16).toArray(ValueLayout.JAVA_BYTE);
		if (!java.util.Arrays.equals(wanted, asked) || asyncInfo.address() == 0L) {
			out.reinterpret(8).set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
			return 0x80004002; // E_NOINTERFACE
		}

		out.reinterpret(8).set(ValueLayout.ADDRESS, 0, asyncInfo);
		return 0;
	}

	static int noop(MemorySegment self) {
		return 0;
	}

	static int release(MemorySegment self) {
		RELEASES.incrementAndGet();
		return 0;
	}

	static int answer(MemorySegment self, MemorySegment out) {
		out.reinterpret(4).set(ValueLayout.JAVA_INT, 0, 42);
		return 0;
	}

	static int fail(MemorySegment self, MemorySegment out) {
		return 0x80004005; // E_FAIL
	}

	static int status(MemorySegment self, MemorySegment out) {
		int calls = STATUS_CALLS.incrementAndGet();
		out.reinterpret(4).set(ValueLayout.JAVA_INT, 0, calls >= completeAfter ? WinRt.ASYNC_COMPLETED : WinRt.ASYNC_STARTED);
		return 0;
	}

	static int results(MemorySegment self, MemorySegment out) {
		out.reinterpret(8).set(ValueLayout.ADDRESS, 0, result);
		return 0;
	}

	/** A COM object: a pointer to a vtable whose slots are the given upcalls (null for a slot nobody calls). */
	private static MemorySegment object(Arena arena, MemorySegment... slots) {
		MemorySegment vtable = arena.allocate(ValueLayout.ADDRESS, slots.length);
		for (int i = 0; i < slots.length; i++) {
			vtable.setAtIndex(ValueLayout.ADDRESS, i, slots[i] == null ? MemorySegment.NULL : slots[i]);
		}

		MemorySegment object = arena.allocate(ValueLayout.ADDRESS);
		object.set(ValueLayout.ADDRESS, 0, vtable);
		return object;
	}

	private static MemorySegment upcall(Arena arena, String name, FunctionDescriptor descriptor) throws ReflectiveOperationException {
		MethodType type = descriptor.toMethodType();
		return LINKER.upcallStub(MethodHandles.lookup().findStatic(WinRtTest.class, name, type), descriptor, arena);
	}

	@Test
	void vtableSlotsAreCalledWithThisAndAnOutPointer() throws ReflectiveOperationException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment none = upcall(arena, "noop", THIS_ONLY);
			MemorySegment object = object(arena, upcall(arena, "queryInterface", THIS_IN_OUT), none, upcall(arena, "release", THIS_ONLY),
				none, none, none, upcall(arena, "answer", THIS_OUT), upcall(arena, "fail", THIS_OUT));
			MemorySegment out = arena.allocate(ValueLayout.JAVA_INT);
			assertEquals(0, WinRt.callOut(object, 6, out));
			assertEquals(42, out.get(ValueLayout.JAVA_INT, 0));
			assertThrows(IllegalStateException.class, () -> WinRt.check(WinRt.callOut(object, 7, out), "failing method"));

			RELEASES.set(0);
			WinRt.release(object);
			WinRt.release(MemorySegment.NULL);
			assertEquals(1, RELEASES.get(), "Release once, and never on null");

			asyncInfo = MemorySegment.NULL;
			assertThrows(IllegalStateException.class, () -> WinRt.query(arena, object, WinRt.IID_IASYNC_INFO), "E_NOINTERFACE");
		}
	}

	@Test
	void anAsyncOperationIsPolledUntilItCompletes() throws ReflectiveOperationException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment none = upcall(arena, "noop", THIS_ONLY);
			MemorySegment releaseStub = upcall(arena, "release", THIS_ONLY);
			MemorySegment queryStub = upcall(arena, "queryInterface", THIS_IN_OUT);
			// IAsyncInfo: Status at 7, Cancel 9, Close 10.
			asyncInfo = object(arena, queryStub, none, releaseStub, none, none, none, none, upcall(arena, "status", THIS_OUT), none, none, none);
			result = object(arena, queryStub, none, releaseStub);
			// IAsyncOperation<T>: GetResults at 8.
			MemorySegment operation = object(arena, queryStub, none, releaseStub, none, none, none, none, none, upcall(arena, "results", THIS_OUT));
			STATUS_CALLS.set(0);
			RELEASES.set(0);
			completeAfter = 3;
			MemorySegment got = WinRt.await(arena, operation, 2_000L);
			assertEquals(result.address(), got.address(), "the result once Completed");
			assertEquals(3, STATUS_CALLS.get(), "polled until Completed");
			assertEquals(2, RELEASES.get(), "the IAsyncInfo and the operation are released");

			STATUS_CALLS.set(0);
			completeAfter = Integer.MAX_VALUE;
			MemorySegment timedOut = WinRt.await(arena, operation, 30L);
			assertEquals(0L, timedOut.address(), "gives up after the timeout");
			asyncInfo = MemorySegment.NULL;
		}
	}

	@Test
	void guidsAreLaidOutLikeWindows() {
		byte[] expected = {0x36, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 0x46};
		assertArrayEquals(expected, WinRt.guidBytes("00000036-0000-0000-C000-000000000046"));
		byte[] statics = WinRt.guidBytes("{2050c4ee-11a0-57de-aed7-c97c70338245}");
		assertEquals((byte) 0xee, statics[0]);
		assertEquals((byte) 0x20, statics[3]);
		assertEquals((byte) 0xa0, statics[4]);
		assertEquals((byte) 0xde, statics[6]);
		assertEquals((byte) 0xae, statics[8]);
		assertEquals((byte) 0x45, statics[15]);
		assertThrows(IllegalArgumentException.class, () -> WinRt.guidBytes("not-a-guid"));
	}

	@Test
	void windowsTimesBecomeMilliseconds() {
		assertEquals(61_500L, WinRt.ticksToMillis(615_000_000L));
		long now = 1_800_000_000_000L;
		assertEquals(1_000L, WinRt.ageMillis((now - 1_000L) * 10_000L + WinRt.UNIX_EPOCH_FILETIME, now));
		assertEquals(0L, WinRt.ageMillis(0L, now), "no update time: no age");
	}

	@Test
	void offWindowsTheLibraryIsReportedMissing() {
		if (WinRtMediaSource.available()) {
			return;
		}

		try (Arena arena = Arena.ofConfined()) {
			assertThrows(IllegalStateException.class, () -> new WinRt(arena));
		}
	}
}
