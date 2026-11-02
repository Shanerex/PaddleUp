package com.paddleup.accounts.service;

/** A plain-text message to one recipient. */
public record OutgoingEmail(String to, String subject, String text) {}
