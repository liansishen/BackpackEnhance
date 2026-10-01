package com.hepdd.backpackenhance.integration.ae2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.junit.Test;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;

public class WirelessTerminalNetworkTest {

    @Test
    public void wiredTerminalMatchesItsGridHost() {
        IGrid grid = proxy(IGrid.class, null);
        IGridNode node = proxy(IGridNode.class, grid);
        IGridHost host = proxy(IGridHost.class, node);
        assertTrue(WirelessOverlayService.isSameTerminalNetwork(host, null, grid));
        assertFalse(WirelessOverlayService.isSameTerminalNetwork(host, null, proxy(IGrid.class, null)));
    }

    @Test
    public void wirelessTerminalUsesActionableNode() {
        IGrid grid = proxy(IGrid.class, null);
        IGridNode node = proxy(IGridNode.class, grid);
        IActionHost host = (IActionHost) Proxy.newProxyInstance(
            IActionHost.class.getClassLoader(),
            new Class<?>[] { IActionHost.class },
            (ignored, method, args) -> method.getName()
                .equals("getActionableNode") ? node : null);
        assertTrue(WirelessOverlayService.isSameTerminalNetwork(host, null, grid));
        assertFalse(WirelessOverlayService.isSameTerminalNetwork(host, null, null));
    }

    @Test
    public void nodeFallbackExcludesUnconnectedPortableStorage() {
        IGrid grid = proxy(IGrid.class, null);
        IGridNode node = proxy(IGridNode.class, grid);
        assertTrue(WirelessOverlayService.isSameTerminalNetwork(new Object(), node, grid));
        assertFalse(WirelessOverlayService.isSameTerminalNetwork(new Object(), null, grid));
        assertFalse(WirelessOverlayService.isSameTerminalNetwork(null, proxy(IGridNode.class, null), grid));
    }

    @Test
    public void injectionOnlyAllowsStorageAccess() {
        ISecurityGrid security = security(SecurityPermissions.INJECT);
        assertTrue(WirelessOverlayService.canAccessStorage(security, null));
        assertFalse(security.hasPermission(null, SecurityPermissions.EXTRACT));
    }

    @Test
    public void extractionOnlyAllowsStorageAccess() {
        ISecurityGrid security = security(SecurityPermissions.EXTRACT);
        assertTrue(WirelessOverlayService.canAccessStorage(security, null));
        assertFalse(security.hasPermission(null, SecurityPermissions.INJECT));
    }

    @Test
    public void bothStoragePermissionsAllowAccess() {
        assertTrue(
            WirelessOverlayService
                .canAccessStorage(security(SecurityPermissions.INJECT, SecurityPermissions.EXTRACT), null));
    }

    @Test
    public void noStoragePermissionsDenyAccess() {
        assertFalse(WirelessOverlayService.canAccessStorage(security(), null));
        assertFalse(WirelessOverlayService.canAccessStorage(security(SecurityPermissions.BUILD), null));
    }

    private static ISecurityGrid security(SecurityPermissions... permissions) {
        return (ISecurityGrid) Proxy.newProxyInstance(
            ISecurityGrid.class.getClassLoader(),
            new Class<?>[] { ISecurityGrid.class },
            (ignored, method, args) -> Arrays.asList(permissions)
                .contains(args[1]));
    }

    private static <T> T proxy(Class<T> type, Object result) {
        return type.cast(
            Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (ignored, method, args) -> result));
    }
}
