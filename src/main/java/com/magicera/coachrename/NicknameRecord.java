package com.magicera.coachrename;

public record NicknameRecord(
        String displayNickname,
        String plainNickname,
        String rawNickname,
        String lastKnownUsername
) {
}
