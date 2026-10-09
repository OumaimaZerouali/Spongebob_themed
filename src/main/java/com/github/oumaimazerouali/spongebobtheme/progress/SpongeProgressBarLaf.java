package com.github.oumaimazerouali.spongebobtheme.progress;

import com.intellij.util.ui.JBUI;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

/**
 * Swing looks up a static {@code createUI} on the class registered in UIManager.
 * Kotlin can't declare one that hides {@link ComponentUI#createUI}, so this thin Java entry point does it.
 */
public class SpongeProgressBarLaf extends SpongeProgressBarUI {
    @SuppressWarnings({"unused", "MethodOverridesStaticMethodOfSuperclass"})
    public static ComponentUI createUI(JComponent c) {
        c.setBorder(JBUI.Borders.empty().asUIResource());
        return new SpongeProgressBarLaf();
    }
}
