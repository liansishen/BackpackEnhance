package com.hepdd.backpackenhance.client.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;

import org.junit.After;
import org.junit.Test;

import com.hepdd.backpackenhance.client.overlay.OverlayTheme.Color;

public class OverlayThemeTest {

    @After
    public void restoreDefaults() {
        OverlayTheme.INSTANCE.onResourceManagerReload(manager(new AtomicInteger()));
    }

    @Test
    public void rgbAndArgbColorsPreserveOpacity() {
        assertEquals(0xFF123456, OverlayTheme.parseColor(" #123456 "));
        assertEquals(0x80123456, OverlayTheme.parseColor("80123456"));
        assertEquals(0xFFFFFFFF, OverlayTheme.parseColor("ffffffff"));
        assertEquals(0, OverlayTheme.parseColor("00000000"));
    }

    @Test
    public void invalidColorFormatsAreRejected() {
        for (String invalid : new String[] { "", "123", "1234567", "FFGGHH", "123456789", "-123456" }) {
            assertThrows(IllegalArgumentException.class, () -> OverlayTheme.parseColor(invalid));
        }
    }

    @Test
    public void packsOverrideIndividualKeysAndCloseTheirStreams() {
        AtomicInteger closed = new AtomicInteger();
        OverlayTheme.INSTANCE.onResourceManagerReload(
            manager(closed, "text=112233\nsearch.hint=445566\n", "text=abcdef\nsearch.shade=40000000\n"));
        assertEquals(0xFFABCDEF, OverlayTheme.color(Color.TEXT));
        assertEquals(0xFF445566, OverlayTheme.color(Color.SEARCH_HINT));
        assertEquals(0x40000000, OverlayTheme.color(Color.SEARCH_SHADE));
        assertEquals(0xFF707070, OverlayTheme.color(Color.SEARCH_DISABLED));
        assertEquals(2, closed.get());
    }

    @Test
    public void invalidOverridePreservesLowerPriorityValue() {
        OverlayTheme.INSTANCE.onResourceManagerReload(
            manager(new AtomicInteger(), "text=123456\n", "text=invalid\nsearch.hint=abcdef\n"));
        assertEquals(0xFF123456, OverlayTheme.color(Color.TEXT));
        assertEquals(0xFFABCDEF, OverlayTheme.color(Color.SEARCH_HINT));
    }

    @Test
    public void reloadDropsRemovedColors() {
        AtomicInteger closed = new AtomicInteger();
        OverlayTheme.INSTANCE.onResourceManagerReload(manager(closed, "text=abcdef\n"));
        assertEquals(0xFFABCDEF, OverlayTheme.color(Color.TEXT));
        assertEquals(1, closed.get());
        OverlayTheme.INSTANCE.onResourceManagerReload(manager(closed));
        assertEquals(Color.TEXT.fallback, OverlayTheme.color(Color.TEXT));
    }

    private static IResourceManager manager(AtomicInteger closed, String... packs) {
        return (IResourceManager) Proxy.newProxyInstance(
            IResourceManager.class.getClassLoader(),
            new Class<?>[] { IResourceManager.class },
            (proxy, method, args) -> {
                if ("getAllResources".equals(method.getName())) {
                    assertEquals(OverlayTheme.COLORS, args[0]);
                    List<IResource> resources = new ArrayList<>();
                    for (String pack : packs) resources.add(resource(pack, closed));
                    return resources;
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IResource resource(String text, AtomicInteger closed) {
        return (IResource) Proxy.newProxyInstance(
            IResource.class.getClassLoader(),
            new Class<?>[] { IResource.class },
            (proxy, method, args) -> {
                if (!"getInputStream".equals(method.getName()))
                    throw new UnsupportedOperationException(method.getName());
                return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)) {

                    @Override
                    public void close() throws IOException {
                        closed.incrementAndGet();
                        super.close();
                    }
                };
            });
    }
}
