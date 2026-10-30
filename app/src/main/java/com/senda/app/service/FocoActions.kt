package com.senda.app.service

object FocoActions {

    const val ACTION_ENSURE_RUNNING = "com.senda.app.action.ENSURE_RUNNING"
    const val ACTION_START_SESSION = "com.senda.app.action.START_SESSION"
    const val ACTION_STOP_SESSION = "com.senda.app.action.STOP_SESSION"
    const val ACTION_APP_LOCK = "com.senda.app.action.APP_LOCK"
    const val ACTION_APP_BLOCKED = "com.senda.app.action.APP_BLOCKED"
    const val ACTION_REQUEST_HOME = "com.senda.app.action.REQUEST_HOME"
    const val ACTION_LEAVE_BLOCKED_PAGE = "com.senda.app.action.LEAVE_BLOCKED_PAGE"
    const val ACTION_BLOCKED_SITE_DETECTED = "com.senda.app.action.BLOCKED_SITE_DETECTED"
    const val ACTION_DEVICE_ADMIN_CHANGED = "com.senda.app.action.DEVICE_ADMIN_CHANGED"

    const val EXTRA_GROUP_ID = "extra_group_id"
    const val EXTRA_USAGE_KEY = "extra_usage_key"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_TARGET_IDENTIFIER = "extra_target_identifier"
    const val EXTRA_IS_WEBSITE = "extra_is_website"
    const val EXTRA_DISPLAY_NAME = "extra_display_name"
    const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
    const val EXTRA_COOLDOWN_MINUTES = "extra_cooldown_minutes"
    const val EXTRA_COUNT_MODE = "extra_count_mode"
    const val EXTRA_URL = "extra_url"
    const val EXTRA_DOMAIN = "extra_domain"
    const val EXTRA_BLOCK_REASON = "extra_block_reason"
    const val EXTRA_COOLDOWN_SECONDS = "extra_cooldown_seconds"
}
