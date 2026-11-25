package io.github.kenercartagena.tps.identity.domain;

/** Whether a user may log in. Users are never deleted, only disabled. */
public enum UserStatus {

    /** The user can log in (unless temporarily locked after failed attempts). */
    ACTIVE,

    /** The user cannot log in until an administrator activates them again. */
    DISABLED
}
