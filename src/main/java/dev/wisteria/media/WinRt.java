package dev.wisteria.media;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The few pieces of COM and WinRT the media source needs, through Java's foreign function API: calls by vtable slot,
 * GUIDs, HSTRINGs and polling an async operation. No native library of our own: everything is in Windows' combase.dll.
 *
 * <p>Slots are counted from the start of the vtable: IUnknown takes 0–2, IInspectable 3–5, so an interface's own first
 * method is slot 6. The slot numbers used by {@link WinRtMediaSource} come from the Windows metadata, as generated into
 * the {@code windows} crate (0.58), and are listed there next to each call.
 */
final class WinRt {
	static final int S_OK = 0;
	static final int RO_INIT_MULTITHREADED = 1;
	/** IUnknown. */
	static final int QUERY_INTERFACE = 0;
	static final int RELEASE = 2;
	/** IAsyncInfo, {00000036-0000-0000-C000-000000000046}: Id 6, Status 7, ErrorCode 8, Cancel 9, Close 10. */
	static final String IID_IASYNC_INFO = "00000036-0000-0000-C000-000000000046";
	static final int ASYNC_INFO_STATUS = 7;
	static final int ASYNC_INFO_CANCEL = 9;
	static final int ASYNC_INFO_CLOSE = 10;
	/** IAsyncOperation&lt;T&gt;: SetCompleted 6, Completed 7, GetResults 8. */
	static final int ASYNC_OPERATION_GET_RESULTS = 8;
	/** AsyncStatus. */
	static final int ASYNC_STARTED = 0;
	static final int ASYNC_COMPLETED = 1;
	/** Windows file time (100 ns since 1601) of the Unix epoch. */
	static final long UNIX_EPOCH_FILETIME = 116_444_736_000_000_000L;

	private static final Linker LINKER = Linker.nativeLinker();
	private static final FunctionDescriptor THIS_ONLY = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final FunctionDescriptor THIS_OUT = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final FunctionDescriptor THIS_IN_OUT = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS);
	private static final MethodHandle CALL_THIS_ONLY = LINKER.downcallHandle(THIS_ONLY);
	private static final MethodHandle CALL_THIS_OUT = LINKER.downcallHandle(THIS_OUT);
	private static final MethodHandle CALL_THIS_IN_OUT = LINKER.downcallHandle(THIS_IN_OUT);

	private final MethodHandle roInitialize;
	private final MethodHandle roUninitialize;
	private final MethodHandle roGetActivationFactory;
	private final MethodHandle windowsCreateString;
	private final MethodHandle windowsDeleteString;
	private final MethodHandle windowsGetStringRawBuffer;

	/** @throws IllegalStateException off Windows, or if combase.dll lacks the functions */
	WinRt(Arena arena) {
		SymbolLookup combase;
		try {
			combase = SymbolLookup.libraryLookup("combase", arena);
		} catch (IllegalArgumentException missing) {
			throw new IllegalStateException("combase.dll is not there (not Windows)", missing);
		}

		this.roInitialize = LINKER.downcallHandle(find(combase, "RoInitialize"), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		this.roUninitialize = LINKER.downcallHandle(find(combase, "RoUninitialize"), FunctionDescriptor.ofVoid());
		this.roGetActivationFactory = LINKER.downcallHandle(find(combase, "RoGetActivationFactory"),
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		this.windowsCreateString = LINKER.downcallHandle(find(combase, "WindowsCreateString"),
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		this.windowsDeleteString = LINKER.downcallHandle(find(combase, "WindowsDeleteString"),
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		this.windowsGetStringRawBuffer = LINKER.downcallHandle(find(combase, "WindowsGetStringRawBuffer"),
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private static MemorySegment find(SymbolLookup lookup, String name) {
		return lookup.find(name).orElseThrow(() -> new IllegalStateException("combase.dll has no " + name));
	}

	/** Joins the calling thread to the multithreaded apartment. S_OK, S_FALSE (already) and RPC_E_CHANGED_MODE are all usable. */
	int initialize() {
		try {
			return (int) this.roInitialize.invokeExact(RO_INIT_MULTITHREADED);
		} catch (Throwable failed) {
			throw rethrow(failed);
		}
	}

	void uninitialize() {
		try {
			this.roUninitialize.invokeExact();
		} catch (Throwable failed) {
			throw rethrow(failed);
		}
	}

	/** The activation factory of a runtime class, as the given interface. The caller releases it. */
	MemorySegment activationFactory(Arena arena, String runtimeClass, String iid) {
		MemorySegment name = this.createString(runtimeClass);
		try {
			MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
			int hr = (int) this.roGetActivationFactory.invokeExact(name, guid(arena, iid), out);
			check(hr, "RoGetActivationFactory " + runtimeClass);
			return out.get(ValueLayout.ADDRESS, 0);
		} catch (Throwable failed) {
			throw rethrow(failed);
		} finally {
			this.deleteString(name);
		}
	}

	MemorySegment createString(String text) {
		try (Arena scratch = Arena.ofConfined()) {
			MemorySegment chars = scratch.allocateFrom(text, StandardCharsets.UTF_16LE);
			MemorySegment out = scratch.allocate(ValueLayout.ADDRESS);
			int hr = (int) this.windowsCreateString.invokeExact(chars, text.length(), out);
			check(hr, "WindowsCreateString");
			return out.get(ValueLayout.ADDRESS, 0);
		} catch (Throwable failed) {
			throw rethrow(failed);
		}
	}

	void deleteString(MemorySegment hstring) {
		if (hstring.address() == 0L) {
			return;
		}

		try {
			int ignored = (int) this.windowsDeleteString.invokeExact(hstring);
		} catch (Throwable failed) {
			throw rethrow(failed);
		}
	}

	/** The text of an HSTRING (null handle is ""); does not delete it. */
	String readString(MemorySegment hstring) {
		if (hstring.address() == 0L) {
			return "";
		}

		try (Arena scratch = Arena.ofConfined()) {
			MemorySegment length = scratch.allocate(ValueLayout.JAVA_INT);
			MemorySegment buffer = (MemorySegment) this.windowsGetStringRawBuffer.invokeExact(hstring, length);
			int count = length.get(ValueLayout.JAVA_INT, 0);
			if (buffer.address() == 0L || count <= 0) {
				return "";
			}

			char[] chars = new char[Math.min(count, 4096)];
			MemorySegment data = buffer.reinterpret((long) chars.length * 2L);
			for (int i = 0; i < chars.length; i++) {
				chars[i] = data.getAtIndex(ValueLayout.JAVA_CHAR_UNALIGNED, i);
			}

			return new String(chars);
		} catch (Throwable failed) {
			throw rethrow(failed);
		}
	}

	/** Reads an HSTRING getter (slot) and frees the string. */
	String stringProperty(Arena arena, MemorySegment object, int slot) {
		MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
		check(callOut(object, slot, out), "string property " + slot);
		MemorySegment hstring = out.get(ValueLayout.ADDRESS, 0);
		try {
			return this.readString(hstring);
		} finally {
			this.deleteString(hstring);
		}
	}

	// ----- Static helpers: no combase needed, so the unit tests reach them on any system -----

	/** The function in a COM object's vtable slot. */
	static MemorySegment slot(MemorySegment object, int slot) {
		MemorySegment vtable = object.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
		return vtable.reinterpret(ValueLayout.ADDRESS.byteSize() * (slot + 1L)).getAtIndex(ValueLayout.ADDRESS, slot);
	}

	/** HRESULT method(this). */
	static int call(MemorySegment object, int slot) {
		try {
			return (int) CALL_THIS_ONLY.invokeExact(slot(object, slot), object);
		} catch (Throwable failed) {
			throw rethrow(failed);
		}
	}

	/** HRESULT method(this, out). {@code out} receives a pointer, an int, a long or a double, by the method. */
	static int callOut(MemorySegment object, int slot, MemorySegment out) {
		try {
			return (int) CALL_THIS_OUT.invokeExact(slot(object, slot), object, out);
		} catch (Throwable failed) {
			throw rethrow(failed);
		}
	}

	/** A pointer-returning method: calls it and gives back the pointer it wrote. */
	static MemorySegment callPointer(Arena arena, MemorySegment object, int slot, String what) {
		MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
		check(callOut(object, slot, out), what);
		return out.get(ValueLayout.ADDRESS, 0);
	}

	/** IUnknown::QueryInterface. The caller releases what it gets. */
	static MemorySegment query(Arena arena, MemorySegment object, String iid) {
		MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
		try {
			int hr = (int) CALL_THIS_IN_OUT.invokeExact(slot(object, QUERY_INTERFACE), object, guid(arena, iid), out);
			check(hr, "QueryInterface " + iid);
		} catch (Throwable failed) {
			throw rethrow(failed);
		}

		return out.get(ValueLayout.ADDRESS, 0);
	}

	/** IUnknown::Release; null is ignored. */
	static void release(MemorySegment object) {
		if (object != null && object.address() != 0L) {
			call(object, RELEASE);
		}
	}

	/**
	 * Waits for an IAsyncOperation by polling its IAsyncInfo status (no completion callback to implement), then gets the
	 * pointer result. The operation is released.
	 *
	 * @return the result object (caller releases), or NULL when it failed, was cancelled or took longer than the timeout
	 */
	static MemorySegment await(Arena arena, MemorySegment operation, long timeoutMillis) {
		MemorySegment info = MemorySegment.NULL;
		try {
			info = query(arena, operation, IID_IASYNC_INFO);
			MemorySegment status = arena.allocate(ValueLayout.JAVA_INT);
			long deadline = System.currentTimeMillis() + timeoutMillis;
			while (true) {
				check(callOut(info, ASYNC_INFO_STATUS, status), "IAsyncInfo.Status");
				int now = status.get(ValueLayout.JAVA_INT, 0);
				if (now == ASYNC_COMPLETED) {
					return callPointer(arena, operation, ASYNC_OPERATION_GET_RESULTS, "IAsyncOperation.GetResults");
				}

				if (now != ASYNC_STARTED) {
					return MemorySegment.NULL;
				}

				if (System.currentTimeMillis() > deadline) {
					call(info, ASYNC_INFO_CANCEL);
					return MemorySegment.NULL;
				}

				Thread.sleep(5L);
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return MemorySegment.NULL;
		} finally {
			if (info.address() != 0L) {
				call(info, ASYNC_INFO_CLOSE);
				release(info);
			}

			release(operation);
		}
	}

	/** A GUID in memory, from "6969cb39-0bfa-5fe0-8d73-09cc5e5408e1": Data1–3 little-endian, Data4 as bytes. */
	static MemorySegment guid(Arena arena, String text) {
		MemorySegment guid = arena.allocate(16, 4);
		byte[] bytes = guidBytes(text);
		for (int i = 0; i < 16; i++) {
			guid.set(ValueLayout.JAVA_BYTE, i, bytes[i]);
		}

		return guid;
	}

	/** The 16 bytes of a GUID as Windows lays it out. Pure, for the unit test. */
	static byte[] guidBytes(String text) {
		String hex = text.replace("-", "").replace("{", "").replace("}", "").toLowerCase(Locale.ROOT);
		if (hex.length() != 32) {
			throw new IllegalArgumentException("Not a GUID: " + text);
		}

		long data1 = Long.parseLong(hex.substring(0, 8), 16);
		int data2 = Integer.parseInt(hex.substring(8, 12), 16);
		int data3 = Integer.parseInt(hex.substring(12, 16), 16);
		byte[] out = new byte[16];
		for (int i = 0; i < 4; i++) {
			out[i] = (byte) (data1 >>> (8 * i));
		}

		out[4] = (byte) data2;
		out[5] = (byte) (data2 >>> 8);
		out[6] = (byte) data3;
		out[7] = (byte) (data3 >>> 8);
		for (int i = 0; i < 8; i++) {
			out[8 + i] = (byte) Integer.parseInt(hex.substring(16 + i * 2, 18 + i * 2), 16);
		}

		return out;
	}

	/** A TimeSpan (100 ns ticks) in milliseconds. */
	static long ticksToMillis(long ticks) {
		return ticks / 10_000L;
	}

	/** How long ago a DateTime (100 ns since 1601) was, in milliseconds, at {@code nowMillis} (Unix time). */
	static long ageMillis(long dateTimeTicks, long nowMillis) {
		long now = nowMillis * 10_000L + UNIX_EPOCH_FILETIME;
		return dateTimeTicks <= 0L ? 0L : Math.max(0L, (now - dateTimeTicks) / 10_000L);
	}

	static void check(int hresult, String what) {
		if (hresult < 0) {
			throw new IllegalStateException(what + " failed: 0x" + Integer.toHexString(hresult));
		}
	}

	private static RuntimeException rethrow(Throwable failed) {
		if (failed instanceof RuntimeException runtime) {
			return runtime;
		}

		if (failed instanceof Error error) {
			throw error;
		}

		return new IllegalStateException(failed);
	}
}
