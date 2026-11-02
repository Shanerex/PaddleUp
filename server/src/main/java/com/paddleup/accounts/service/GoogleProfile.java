package com.paddleup.accounts.service;

/** The claims from Google's verified ID token that sign-in needs. */
public record GoogleProfile(
    String subject, String email, boolean emailVerified, String name, String pictureUrl) {}
