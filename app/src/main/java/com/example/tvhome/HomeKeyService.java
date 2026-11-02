package com.example.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the remote's Home button open AURA OS (the platform chooser) even though Fire OS
 * normally insists on its own home screen. It only listens for the Home key; it reads nothing
 * from the screen. Remove it any time with: adb uninstall com.example.tvhome
 */
public class HomeKeyService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used.
    }

    @Override
    public void onInterrupt() {
        // Not used.
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                Intent go = new Intent(this, MainActivity.class);
                go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(go);
            }
            return true; // swallow it so Fire OS doesn't also open its own home
        }
        return false;
    }
}
