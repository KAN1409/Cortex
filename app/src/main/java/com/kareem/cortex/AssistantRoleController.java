package com.kareem.cortex;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Explicit user-controlled access to Android's Assistant role. */
public final class AssistantRoleController {
    private AssistantRoleController() {}

    public static boolean isAvailable(Context context) {
        if (Build.VERSION.SDK_INT < 29) return false;
        RoleManager roles = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
        return roles != null && roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT);
    }

    public static boolean isHeld(Context context) {
        if (Build.VERSION.SDK_INT < 29) return false;
        RoleManager roles = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
        return roles != null && roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
                && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT);
    }

    /**
     * Opens Android's own role-consent UI. Cortex never changes the default assistant silently.
     * Returns false when the role is unsupported on this device/API level.
     */
    public static boolean request(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT < 29) return false;
        RoleManager roles = (RoleManager) activity.getSystemService(Context.ROLE_SERVICE);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return false;
        if (roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) return true;
        Intent request = roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT);
        activity.startActivityForResult(request, requestCode);
        return true;
    }
}
