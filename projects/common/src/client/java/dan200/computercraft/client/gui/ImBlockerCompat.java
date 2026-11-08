// SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.client.gui;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Best-effort glue for <a href="https://github.com/reserveword/IMBlocker">IMBlocker</a>.
 * <p>
 * IMBlocker decides whether to switch the input method on by looking at the "focus owner" of the game window.
 * Minecraft's screens and widgets tell it about themselves through Mixins, but
 * {@link dan200.computercraft.client.gui.widgets.TerminalWidget} is not one of the types it knows about - the focus
 * callback it injects into plain widgets is empty - so it never learns that our computer screens want to receive text,
 * and so keeps the IME disabled. The result is that you cannot type anything with an input method.
 * <p>
 * IMBlocker has no public API for other mods, so we poke at two of its public members instead:
 * <ul>
 *     <li>{@code FocusContainer.MINECRAFT#setPreferredState(boolean)} (IMBlocker 5.4+), which is exactly what IMBlocker
 *     itself uses to model "this screen wants the IME".</li>
 *     <li>{@code IMBlockerConfig#defaultScreenWhitelist} + {@code IMBlockerConfig#reloadConfig()} (IMBlocker 5.5+), the
 *     same list users edit through IMBlocker's config screen, so that the screen still works if IMBlocker resets the
 *     state above (e.g. after closing an alert).</li>
 * </ul>
 * Every access is reflective and guarded, so this class is a no-op when IMBlocker is not installed, or when a future
 * version renames the members we use.
 */
public final class ImBlockerCompat {
    private static final Logger LOG = LoggerFactory.getLogger(ImBlockerCompat.class);

    /**
     * The screens which accept typing through a {@link dan200.computercraft.client.gui.widgets.TerminalWidget}.
     * IMBlocker matches whitelist entries with {@code Class#isInstance}, so these abstract base classes also cover the
     * concrete screens ({@code ComputerScreen}, {@code TurtleScreen}, {@code PocketComputerLecternScreen}, ...).
     */
    private static final List<String> TERMINAL_SCREENS = List.of(
        "dan200.computercraft.client.gui.AbstractComputerScreen",
        "dan200.computercraft.client.gui.NoTermComputerScreen"
    );

    private static boolean resolved;
    private static boolean whitelisted;

    private static @Nullable ImBlocker container;

    private ImBlockerCompat() {
    }

    /**
     * Tell IMBlocker whether the terminal currently wants to receive text, and so whether the IME should be enabled.
     * <p>
     * This is cheap to call every tick - when IMBlocker is absent it is a boolean check, otherwise a couple of
     * reflective reads.
     *
     * @param enabled Whether the terminal is focused and so accepts text input.
     */
    public static void setTerminalIme(boolean enabled) {
        if (enabled) whitelistTerminalScreens();

        var imBlocker = resolve();
        if (imBlocker == null) return;

        try {
            // IMBlocker may have changed this behind our back (most notably when returning from another screen), so
            // read the current state rather than tracking it ourselves.
            var state = imBlocker.getPreferredState.invoke(imBlocker.container);
            if (!(state instanceof Boolean current) || current != enabled) {
                imBlocker.setPreferredState.invoke(imBlocker.container, enabled);

                // setPreferredState only applies the new state if IMBlocker thinks the game window (rather than some
                // widget) holds focus, and it will never think that for our terminal. Assign it ourselves, but only if
                // nobody else holds it - we do not want to steal focus from a real widget.
                if (enabled && !holdsFocus()) imBlocker.deliverFocus.invoke(imBlocker.container);
            }
        } catch (ReflectiveOperationException e) {
            giveUp(e);
        }
    }

    /**
     * Whether IMBlocker is installed and understood by this compatibility layer.
     *
     * @return Whether the IME integration is active.
     */
    public static boolean isSupported() {
        return resolve() != null;
    }

    /**
     * Whether something other than IMBlocker's window container currently holds focus.
     *
     * @return Whether we should leave the focus alone.
     */
    private static boolean holdsFocus() {
        var imBlocker = resolve();
        if (imBlocker == null) return false;

        var getFocusOwner = imBlocker.getFocusOwner;
        if (getFocusOwner == null) return false;

        try {
            var owner = getFocusOwner.invoke(null);
            return owner != null && owner != imBlocker.container;
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    /**
     * Add our screens to IMBlocker's default whitelist and ask it to re-bake it.
     * <p>
     * This is the mechanism IMBlocker documents for screens which are not text fields, and makes the IME work for the
     * whole screen rather than only while the terminal has focus. Re-baking through {@code reloadConfig} keeps any
     * entries the user added themselves.
     */
    private static void whitelistTerminalScreens() {
        if (whitelisted) return;
        whitelisted = true;

        try {
            var configClass = Class.forName("io.github.reserveword.imblocker.common.IMBlockerConfig");

            @SuppressWarnings("unchecked")
            var screens = (List<String>) configClass.getField("defaultScreenWhitelist").get(null);
            var changed = false;
            for (var screen : TERMINAL_SCREENS) changed |= screens != null && screens.add(screen);
            if (!changed) return;

            // Re-loading the whitelist is only available on IMBlocker 5.5+ (older versions expose a differently shaped
            // method which would drop the user's own entries). If it is absent, the entry above is still picked up next
            // time IMBlocker re-reads its config, and setPreferredState() does the work in the meantime.
            var reload = findMethod(configClass, "reloadConfig");
            var instance = findField(configClass, "INSTANCE");
            if (reload != null && instance != null) reload.invoke(instance.get(null));
        } catch (Throwable e) {
            LOG.debug("Cannot add our screens to IMBlocker's whitelist.", e);
        }
    }

    /**
     * Resolve IMBlocker's classes, if they are present and supported.
     *
     * @return IMBlocker's focus management, or {@code null} if it is not available.
     */
    private static @Nullable ImBlocker resolve() {
        if (resolved) return container;
        resolved = true;

        try {
            var containerClass = Class.forName("io.github.reserveword.imblocker.common.gui.FocusContainer");
            var instance = getStatic(containerClass, "MINECRAFT");
            var getPreferredState = findMethod(containerClass, "getPreferredState");
            var setPreferredState = findMethod(containerClass, "setPreferredState", boolean.class);
            var deliverFocus = findMethod(containerClass, "deliverFocus");

            // Older versions of IMBlocker (5.1 - 5.3) have the container, but not everything we need. Only claim
            // support when we really can drive it.
            if (instance == null || getPreferredState == null || setPreferredState == null || deliverFocus == null) {
                LOG.debug("IMBlocker is installed, but does not support its focus management API.");
                return null;
            }

            container = new ImBlocker(instance, getPreferredState, setPreferredState, deliverFocus, findFocusOwner());
        } catch (Throwable e) {
            // IMBlocker is not installed, or is a version without its focus management system (5.0 - 5.3).
            LOG.debug("IMBlocker is not installed (or is unsupported), not enabling IME integration.", e);
            return null;
        }

        LOG.info("Enabling IMBlocker compatibility: the IME will be enabled while a terminal is focused.");
        return container;
    }

    /**
     * Look up IMBlocker's {@code FocusManager#getFocusOwner}, if it is a static method.
     *
     * @return The method, or {@code null} if it is not available.
     */
    private static @Nullable Method findFocusOwner() {
        try {
            var method = Class.forName("io.github.reserveword.imblocker.common.gui.FocusManager")
                .getMethod("getFocusOwner");
            return Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Read a static field, if it is present.
     *
     * @param owner The class to read from.
     * @param name  The name of the field.
     * @return The value of the field, or {@code null} if it could not be read.
     */
    private static @Nullable Object getStatic(Class<?> owner, String name) {
        var field = findField(owner, name);
        if (field == null) return null;

        try {
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Look up a (declared) method, so that we cope with it being package private.
     *
     * @param owner The class to search.
     * @param name  The name of the method.
     * @param args  The parameter types of the method.
     * @return The method, or {@code null} if it could not be found or made accessible.
     */
    private static @Nullable Method findMethod(Class<?> owner, String name, Class<?>... args) {
        try {
            return owner.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            // Fall through to the declared method - IMBlocker calls these from its own code, so they need not be
            // public.
        }

        try {
            var method = owner.getDeclaredMethod(name, args);
            method.setAccessible(true);
            return method;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Look up a (declared) field, so that we cope with it being package private.
     *
     * @param owner The class to search.
     * @param name  The name of the field.
     * @return The field, or {@code null} if it could not be found or made accessible.
     */
    private static @Nullable Field findField(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException e) {
            // Fall through to the declared field, as above.
        }

        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Give up on IMBlocker, after an unexpected failure.
     *
     * @param e The error which occurred.
     */
    private static void giveUp(Exception e) {
        LOG.warn("Cannot talk to IMBlocker, the input method will not work in the computer UI.", e);
        container = null;
    }

    /**
     * The parts of IMBlocker we drive. Only ever constructed once everything has been resolved successfully, so that
     * every member can be used without further checks.
     *
     * @param container         IMBlocker's {@code FocusContainer.MINECRAFT}.
     * @param getPreferredState {@code FocusContainer#getPreferredState()}.
     * @param setPreferredState {@code FocusContainer#setPreferredState(boolean)}.
     * @param deliverFocus      {@code FocusContainer#deliverFocus()}.
     * @param getFocusOwner     {@code FocusManager#getFocusOwner()}, if it is available.
     */
    private record ImBlocker(
        Object container,
        Method getPreferredState,
        Method setPreferredState,
        Method deliverFocus,
        @Nullable Method getFocusOwner
    ) {
    }
}
