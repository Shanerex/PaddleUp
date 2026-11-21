package com.paddleup.scoring.model;

/** A stat and event type ("" for the stat itself): the key of one entry field. */
public record FieldKey(String stat, String eventType) {}
